package com.veltrix.hom.vnext.server.foundation

import com.veltrix.hom.vnext.core.*
import java.security.MessageDigest
import java.util.Locale

enum class SourceStage { UPLOAD, SAFETY_VALIDATE, EXTRACT, OCR, NORMALIZE, CHUNK, METADATA, INDEX, READY, FAILED, UNSUPPORTED }

data class SourceProcess(
    val sourceId: String,
    val stage: SourceStage = SourceStage.UPLOAD,
    val progress: Int = 0,
    val errorCode: String? = null,
)

object SourcePipeline {
    private val linear = listOf(SourceStage.UPLOAD, SourceStage.SAFETY_VALIDATE, SourceStage.EXTRACT, SourceStage.NORMALIZE, SourceStage.CHUNK, SourceStage.METADATA, SourceStage.INDEX, SourceStage.READY)

    fun next(process: SourceProcess, needsOcr: Boolean = false): SourceProcess {
        if (process.stage in setOf(SourceStage.READY, SourceStage.FAILED, SourceStage.UNSUPPORTED)) return process
        val target = when {
            needsOcr && process.stage == SourceStage.EXTRACT -> SourceStage.OCR
            process.stage == SourceStage.OCR -> SourceStage.NORMALIZE
            else -> linear.getOrNull(linear.indexOf(process.stage) + 1) ?: SourceStage.FAILED
        }
        val progress = when (target) {
            SourceStage.UPLOAD -> 5
            SourceStage.SAFETY_VALIDATE -> 12
            SourceStage.EXTRACT -> 25
            SourceStage.OCR -> 42
            SourceStage.NORMALIZE -> 55
            SourceStage.CHUNK -> 68
            SourceStage.METADATA -> 78
            SourceStage.INDEX -> 90
            SourceStage.READY -> 100
            SourceStage.FAILED, SourceStage.UNSUPPORTED -> process.progress
        }
        return process.copy(stage = target, progress = progress)
    }

    fun fail(process: SourceProcess, code: String): SourceProcess = process.copy(stage = SourceStage.FAILED, errorCode = code)

    fun validateMime(mime: String): Boolean = mime.lowercase(Locale.ROOT) in setOf(
        "application/pdf", "text/plain", "text/markdown", "image/jpeg", "image/png", "image/webp",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
}

object Chunker {
    fun chunk(accountId: String, sourceId: String, sourceVersion: Long, text: String, targetChars: Int = 1200, overlapChars: Int = 160): List<SourceChunk> {
        require(targetChars >= 200)
        require(overlapChars in 0 until targetChars)
        val normalized = text.replace("\r\n", "\n").replace(Regex("[ \\t]+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        val chunks = mutableListOf<SourceChunk>()
        var start = 0
        while (start < normalized.length) {
            var end = minOf(normalized.length, start + targetChars)
            if (end < normalized.length) {
                val natural = normalized.lastIndexOfAny(charArrayOf('\n', '.', '!', '?'), startIndex = end)
                if (natural > start + targetChars / 2) end = natural + 1
            }
            val body = normalized.substring(start, end).trim()
            if (body.isNotEmpty()) chunks += SourceChunk(
                accountId = accountId,
                sourceId = sourceId,
                sourceVersion = sourceVersion,
                offsetStart = start,
                offsetEnd = end,
                text = body,
                textHash = sha256(body),
            )
            if (end >= normalized.length) break
            start = maxOf(start + 1, end - overlapChars)
        }
        return chunks
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

object CitationAssembler {
    fun validate(citations: List<Citation>, chunks: List<SourceChunk>): List<Citation> {
        val byId = chunks.associateBy { it.id }
        return citations.map { c ->
            val chunk = byId[c.chunkId] ?: throw DomainException(DomainError("NO_CITATION_SUPPORT", ErrorCategory.VALIDATION, "Citation chunk does not exist"))
            if (chunk.sourceId != c.sourceId || chunk.sourceVersion != c.sourceVersion || chunk.textHash != c.textHash) throw DomainException(
                DomainError("NO_CITATION_SUPPORT", ErrorCategory.CONFLICT, "Citation provenance does not match current source version")
            )
            c
        }
    }
}
