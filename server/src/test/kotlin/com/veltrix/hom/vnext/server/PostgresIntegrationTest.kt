package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainException
import kotlin.test.*
import java.util.UUID

class PostgresIntegrationTest {
    @Test fun authOwnershipProjectMemorySourceFlow() {
        val url=System.getenv("VELTRIX_TEST_DATABASE_URL") ?: return
        val cfg=ServerConfig("test",url,System.getenv("VELTRIX_TEST_DATABASE_USER")?:"postgres",System.getenv("VELTRIX_TEST_DATABASE_PASSWORD")?:"postgres",8080,"disabled",null)
        Database(cfg).use { db ->
            val auth=AuthRepository(db);val projects=ProjectRepository(db);val memory=MemoryRepository(db);val sources=SourceRepository(db)
            val suffix=UUID.randomUUID().toString().take(8)
            val a=auth.register(RegisterRequest("a-$suffix@example.test","testing-password-12345","A"))
            val b=auth.register(RegisterRequest("b-$suffix@example.test","testing-password-12345","B"))
            assertEquals(a.accountId,auth.resolve(a.sessionToken)?.accountId)
            val p=projects.create(a.accountId,CreateProjectRequest("CEFR C1","Language mastery"))
            assertEquals("CEFR C1",projects.get(a.accountId,p.id).title)
            assertFailsWith<DomainException>{projects.get(b.accountId,p.id)}
            val m=memory.create(a.accountId,MemoryCreateRequest("PROJECT",p.id,"PREFERENCE","Use British English","EXPLICIT_USER",1.0,"TEST",p.id))
            assertEquals(p.id,m.scopeId)
            val src=sources.createMetadata(a.accountId,SourceCreateRequest("fixture","TEXT","text/plain","a".repeat(64),12))
            val ready=sources.ingestText(a.accountId,src.id,"British English uses colour. This source is a deterministic retrieval fixture.")
            assertEquals("PROCESSING",ready.state) // low-level lexical ingestion is not READY until required indexing completes
            val hits=sources.search(a.accountId,SourceSearchRequest("colour",listOf(src.id),8))
            assertTrue(hits.isNotEmpty());assertEquals(src.id,hits.first().sourceId);assertTrue(hits.first().textHash.isNotBlank())
            sources.linkProject(a.accountId,src.id,p.id);sources.unlinkProject(a.accountId,src.id,p.id)
            assertEquals(src.id,sources.search(a.accountId,SourceSearchRequest("colour",listOf(src.id),8)).first().sourceId)
        }
    }
}
