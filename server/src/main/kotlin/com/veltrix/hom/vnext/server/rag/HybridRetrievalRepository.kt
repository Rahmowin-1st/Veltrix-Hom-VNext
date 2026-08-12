package com.veltrix.hom.vnext.server.rag

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.server.CitationResponse
import com.veltrix.hom.vnext.server.Database
import java.security.MessageDigest
import java.sql.Connection
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Hybrid lexical + pgvector retrieval with account/source/version isolation. */
class HybridRetrievalRepository(
    private val db: Database,
    private val embeddings: EmbeddingAdapter,
) {
    val semanticConfigured:Boolean get()=embeddings.isConfigured()
    data class Hit(
        val citation: CitationResponse,
        val lexicalScore: Double,
        val semanticScore: Double,
        val fusedScore: Double,
    )

    fun indexSource(accountId: String, sourceId: String, sourceVersion: Long = 1): Int {
        val chunks = db.tx { c ->
            requireReadyOwnedSource(c, accountId, sourceId, allowProcessing = true)
            c.prepareStatement("SELECT id,chunk_text,text_hash FROM source_chunk WHERE account_id=?::uuid AND source_id=?::uuid AND source_version=? ORDER BY offset_start").use { ps ->
                ps.setString(1, accountId); ps.setString(2, sourceId); ps.setLong(3, sourceVersion)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3))) } }
            }
        }
        if (chunks.isEmpty()) throw DomainException(DomainError("SOURCE_NOT_READY", ErrorCategory.SOURCE_PROCESSING, "Source has no chunks to index", true))
        var indexed = 0
        chunks.chunked(64).forEach { batch ->
            val existing = db.tx { c ->
                val ids = c.createArrayOf("uuid", batch.map { UUID.fromString(it.first) }.toTypedArray())
                c.prepareStatement("SELECT chunk_id::text,text_hash,embedding_model FROM source_embedding WHERE account_id=?::uuid AND chunk_id=ANY(?::uuid[])").use { ps ->
                    ps.setString(1, accountId); ps.setArray(2, ids)
                    ps.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2) to rs.getString(3)) } }
                }
            }
            val toEmbed = batch.filter { existing[it.first]?.let { e -> e.first == it.third && e.second == embeddings.model } != true }
            if (toEmbed.isEmpty()) return@forEach
            val vectors = embeddings.embed(toEmbed.map { it.second })
            if (vectors.size != toEmbed.size) throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Embedding result count mismatch"))
            db.tx { c ->
                c.prepareStatement(
                    """INSERT INTO source_embedding(chunk_id,account_id,source_id,source_version,embedding_model,embedding_version,text_hash,embedding)
                       VALUES (?::uuid,?::uuid,?::uuid,?,?, '1', ?, ?::vector)
                       ON CONFLICT(chunk_id) DO UPDATE SET embedding_model=excluded.embedding_model,embedding_version=excluded.embedding_version,
                         text_hash=excluded.text_hash,embedding=excluded.embedding,updated_at=now(),source_version=excluded.source_version"""
                ).use { ps ->
                    toEmbed.zip(vectors).forEach { (chunk, vector) ->
                        if (vector.size != 64) throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Part 1 vector index requires 64 dimensions"))
                        ps.setString(1, chunk.first); ps.setString(2, accountId); ps.setString(3, sourceId); ps.setLong(4, sourceVersion)
                        ps.setString(5, embeddings.model); ps.setString(6, chunk.third); ps.setString(7, vectorLiteral(vector)); ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            indexed += toEmbed.size
        }
        db.tx { c ->
            c.prepareStatement("UPDATE source SET state='READY',processing_progress=100,metadata=jsonb_set(metadata,'{embeddingModel}',to_jsonb(?::text),true),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use { ps ->
                ps.setString(1, embeddings.model); ps.setString(2, sourceId); ps.setString(3, accountId)
                if (ps.executeUpdate() != 1) throw DomainException(DomainError("SOURCE_NOT_FOUND", ErrorCategory.NOT_FOUND, "Source not found"))
            }
        }
        return indexed
    }

    fun search(accountId: String, query: String, sourceIds: List<String> = emptyList(), projectId: String? = null, limit: Int = 8): List<Hit> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        if(!embeddings.isConfigured()) return lexicalOnly(accountId,q,sourceIds,projectId,limit)
        val vector = embeddings.embed(listOf(q)).single()
        if (vector.size != 64) throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Part 1 vector index requires 64 dimensions"))
        val sourceFilter = sourceIds.map { UUID.fromString(it) }
        return db.tx { c ->
            if (projectId != null) requireOwnedProject(c, accountId, projectId)
            val sql = """
                WITH candidates AS (
                  SELECT sc.id,sc.source_id,sc.source_version,sc.page,sc.section,sc.text_hash,sc.chunk_text,
                         ts_rank_cd(sc.search_vector, plainto_tsquery('simple', ?)) AS lexical,
                         GREATEST(0.0, 1.0 - (se.embedding <=> ?::vector)) AS semantic,
                         CASE WHEN lower(sc.chunk_text) LIKE '%' || lower(?) || '%' THEN 0.15 ELSE 0 END AS exact_bonus
                  FROM source_chunk sc
                  JOIN source s ON s.id=sc.source_id
                  JOIN source_embedding se ON se.chunk_id=sc.id AND se.account_id=sc.account_id
                  WHERE sc.account_id=?::uuid AND s.account_id=?::uuid AND s.deleted_at IS NULL AND s.state='READY'
                    AND (?::uuid[] IS NULL OR sc.source_id=ANY(?::uuid[]))
                    AND (?::uuid IS NULL OR EXISTS (SELECT 1 FROM source_project_link spl WHERE spl.account_id=sc.account_id AND spl.source_id=sc.source_id AND spl.project_id=?::uuid))
                )
                SELECT *, LEAST(1.0, 0.42*lexical + 0.48*semantic + exact_bonus) AS fused
                FROM candidates
                WHERE lexical > 0 OR semantic > 0.05 OR exact_bonus > 0
                ORDER BY fused DESC, id
                LIMIT ?
            """.trimIndent()
            c.prepareStatement(sql).use { ps ->
                var i=1
                ps.setString(i++, q); ps.setString(i++, vectorLiteral(vector)); ps.setString(i++, q); ps.setString(i++, accountId); ps.setString(i++, accountId)
                if (sourceFilter.isEmpty()) { ps.setNull(i++, java.sql.Types.ARRAY); ps.setNull(i++, java.sql.Types.ARRAY) }
                else { val arr=c.createArrayOf("uuid", sourceFilter.toTypedArray()); ps.setArray(i++, arr); ps.setArray(i++, arr) }
                if (projectId == null) { ps.setNull(i++, java.sql.Types.OTHER); ps.setNull(i++, java.sql.Types.OTHER) }
                else { ps.setObject(i++, UUID.fromString(projectId)); ps.setObject(i++, UUID.fromString(projectId)) }
                ps.setInt(i, limit.coerceIn(1,30))
                ps.executeQuery().use { rs -> buildList {
                    while (rs.next()) {
                        val lexical=rs.getDouble("lexical").coerceIn(0.0,1.0)
                        val semantic=rs.getDouble("semantic").coerceIn(0.0,1.0)
                        val fused=rs.getDouble("fused").coerceIn(0.0,1.0)
                        add(Hit(CitationResponse(
                            sourceId=rs.getObject("source_id",UUID::class.java).toString(), sourceVersion=rs.getLong("source_version"),
                            chunkId=rs.getObject("id",UUID::class.java).toString(), page=rs.getObject("page") as? Int,
                            section=rs.getString("section"), relevance=fused, textHash=rs.getString("text_hash"), excerpt=rs.getString("chunk_text").take(700)
                        ), lexical, semantic, fused))
                    }
                } }
            }
        }
    }

    private fun lexicalOnly(accountId:String,query:String,sourceIds:List<String>,projectId:String?,limit:Int):List<Hit> = db.tx { c ->
        if(projectId!=null) requireOwnedProject(c,accountId,projectId)
        val ids=sourceIds.map(UUID::fromString)
        val sql="""SELECT sc.id,sc.source_id,sc.source_version,sc.page,sc.section,sc.text_hash,sc.chunk_text,
            ts_rank_cd(sc.search_vector,plainto_tsquery('simple',?)) lexical,
            CASE WHEN lower(sc.chunk_text) LIKE '%'||lower(?)||'%' THEN 0.15 ELSE 0 END exact_bonus
            FROM source_chunk sc JOIN source s ON s.id=sc.source_id
            WHERE sc.account_id=?::uuid AND s.account_id=?::uuid AND s.deleted_at IS NULL AND s.state IN ('READY','PARTIAL')
              AND (?::uuid[] IS NULL OR sc.source_id=ANY(?::uuid[]))
              AND (?::uuid IS NULL OR EXISTS(SELECT 1 FROM source_project_link spl WHERE spl.account_id=sc.account_id AND spl.source_id=sc.source_id AND spl.project_id=?::uuid))
              AND (sc.search_vector @@ plainto_tsquery('simple',?) OR lower(sc.chunk_text) LIKE '%'||lower(?)||'%')
            ORDER BY (ts_rank_cd(sc.search_vector,plainto_tsquery('simple',?))+CASE WHEN lower(sc.chunk_text) LIKE '%'||lower(?)||'%' THEN .15 ELSE 0 END) DESC,sc.id LIMIT ?"""
        c.prepareStatement(sql).use { ps -> var i=1;ps.setString(i++,query);ps.setString(i++,query);ps.setString(i++,accountId);ps.setString(i++,accountId);
            if(ids.isEmpty()){ps.setNull(i++,java.sql.Types.ARRAY);ps.setNull(i++,java.sql.Types.ARRAY)}else{val arr=c.createArrayOf("uuid",ids.toTypedArray());ps.setArray(i++,arr);ps.setArray(i++,arr)}
            if(projectId==null){ps.setNull(i++,java.sql.Types.OTHER);ps.setNull(i++,java.sql.Types.OTHER)}else{ps.setObject(i++,UUID.fromString(projectId));ps.setObject(i++,UUID.fromString(projectId))}
            ps.setString(i++,query);ps.setString(i++,query);ps.setString(i++,query);ps.setString(i++,query);ps.setInt(i,limit.coerceIn(1,30));
            ps.executeQuery().use { rs -> buildList { while(rs.next()){val lexical=rs.getDouble("lexical").coerceIn(0.0,1.0);val fused=(.85*lexical+rs.getDouble("exact_bonus")).coerceIn(0.0,1.0);add(Hit(CitationResponse(rs.getObject("source_id",UUID::class.java).toString(),rs.getLong("source_version"),rs.getObject("id",UUID::class.java).toString(),rs.getObject("page") as? Int,rs.getString("section"),fused,rs.getString("text_hash"),rs.getString("chunk_text").take(700)),lexical,0.0,fused)) } } }
        }
    }

    fun ensureAllChunksIndexed(accountId:String,sourceId:String):Boolean = db.tx { c ->
        val counts=c.prepareStatement("SELECT count(*) chunks, count(se.chunk_id) indexed FROM source_chunk sc LEFT JOIN source_embedding se ON se.chunk_id=sc.id WHERE sc.account_id=?::uuid AND sc.source_id=?::uuid").use { ps -> ps.setString(1,accountId);ps.setString(2,sourceId);ps.executeQuery().use{rs->rs.next();rs.getLong(1) to rs.getLong(2)} }
        counts.first>0 && counts.first==counts.second
    }

    private fun requireReadyOwnedSource(c: Connection, accountId:String, sourceId:String, allowProcessing:Boolean) {
        c.prepareStatement("SELECT state FROM source WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use { ps ->
            ps.setString(1,sourceId);ps.setString(2,accountId);ps.executeQuery().use { rs ->
                if(!rs.next()) throw DomainException(DomainError("SOURCE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Source not found"))
                val state=rs.getString(1); if(!allowProcessing && state!="READY") throw DomainException(DomainError("SOURCE_NOT_READY",ErrorCategory.SOURCE_PROCESSING,"Source is not ready",true))
            }
        }
    }
    private fun requireOwnedProject(c:Connection,accountId:String,projectId:String){c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,projectId);ps.setString(2,accountId);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
}
