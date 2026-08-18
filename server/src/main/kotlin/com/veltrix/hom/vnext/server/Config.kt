package com.veltrix.hom.vnext.server

data class ServerConfig(
    val environment: String,
    val databaseUrl: String,
    val databaseUser: String?,
    val databasePassword: String?,
    val port: Int,
    val aiProvider: String,
    val aiApiKey: String?,
    val testAiEnabled: Boolean = false,
    val translationUrl: String? = null,
    val translationApiKey: String? = null,
    val testTranslationEnabled: Boolean = false,
    val sourceStorageRoot: String = "./var/source-storage",
    val ocrGatewayUrl: String? = null,
    val ocrGatewayApiKey: String? = null,
    val testOcrEnabled: Boolean = false,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiFastModel: String = "gpt-5-mini",
    val aiQualityModel: String = "gpt-5.1",
    val aiRequestTimeoutSeconds: Long = 60,
    val embeddingProvider: String = "openai",
    val embeddingApiKey: String? = null,
    val embeddingBaseUrl: String = "https://api.openai.com/v1",
    val embeddingModel: String = "text-embedding-3-small",
    val embeddingDimensions: Int = 64,
    val testEmbeddingEnabled: Boolean = false,
    val storageProvider: String = "local",
    val s3Endpoint: String? = null,
    val s3Region: String = "us-east-1",
    val s3Bucket: String = "veltrix-hom-vnext",
    val s3AccessKey: String? = null,
    val s3SecretKey: String? = null,
    val s3PathStyle: Boolean = true,
    val storageSignedUrlTtlSeconds: Long = 300,
    val workerEnabled: Boolean = true,
    val googleServerClientIds: Set<String> = emptySet(),
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val environment = env["VELTRIX_ENV"] ?: "development"
            val url = env["VELTRIX_DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/veltrix_vnext"
            require(url.startsWith("jdbc:postgresql://")) { "VELTRIX_DATABASE_URL must be a PostgreSQL JDBC URL" }
            val aiKey = env["OPENAI_API_KEY"]?.takeIf { it.isNotBlank() }
                ?: env["VELTRIX_AI_API_KEY"]?.takeIf { it.isNotBlank() }
            val embeddingKey = env["VELTRIX_EMBEDDING_API_KEY"]?.takeIf { it.isNotBlank() } ?: aiKey
            val googleClientIds = buildSet {
                env["VELTRIX_GOOGLE_SERVER_CLIENT_ID"]?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                env["VELTRIX_GOOGLE_ALLOWED_CLIENT_IDS"]
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    ?.forEach(::add)
            }
            return ServerConfig(
                environment = environment,
                databaseUrl = url,
                databaseUser = env["VELTRIX_DATABASE_USER"],
                databasePassword = env["VELTRIX_DATABASE_PASSWORD"],
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                aiProvider = env["VELTRIX_AI_PROVIDER"] ?: if (aiKey != null) "openai" else "disabled",
                aiApiKey = aiKey,
                testAiEnabled = env["VELTRIX_TEST_AI_MOCK"]?.equals("true", ignoreCase = true) == true,
                translationUrl = env["VELTRIX_TRANSLATION_URL"]?.takeIf { it.isNotBlank() },
                translationApiKey = env["VELTRIX_TRANSLATION_API_KEY"]?.takeIf { it.isNotBlank() },
                testTranslationEnabled = env["VELTRIX_TEST_TRANSLATE_MOCK"]?.equals("true", ignoreCase = true) == true,
                sourceStorageRoot = env["VELTRIX_SOURCE_STORAGE_ROOT"] ?: "./var/source-storage",
                ocrGatewayUrl = env["VELTRIX_OCR_GATEWAY_URL"]?.takeIf { it.isNotBlank() },
                ocrGatewayApiKey = env["VELTRIX_OCR_GATEWAY_API_KEY"]?.takeIf { it.isNotBlank() },
                testOcrEnabled = env["VELTRIX_TEST_OCR_MOCK"]?.equals("true", ignoreCase = true) == true,
                aiBaseUrl = env["VELTRIX_AI_BASE_URL"]?.trimEnd('/') ?: "https://api.openai.com/v1",
                aiFastModel = env["VELTRIX_AI_FAST_MODEL"] ?: "gpt-5-mini",
                aiQualityModel = env["VELTRIX_AI_QUALITY_MODEL"] ?: "gpt-5.1",
                aiRequestTimeoutSeconds = env["VELTRIX_AI_TIMEOUT_SECONDS"]?.toLongOrNull()?.coerceIn(5, 180) ?: 60,
                embeddingProvider = env["VELTRIX_EMBEDDING_PROVIDER"] ?: if (embeddingKey != null) "openai" else "disabled",
                embeddingApiKey = embeddingKey,
                embeddingBaseUrl = env["VELTRIX_EMBEDDING_BASE_URL"]?.trimEnd('/') ?: "https://api.openai.com/v1",
                embeddingModel = env["VELTRIX_EMBEDDING_MODEL"] ?: "text-embedding-3-small",
                embeddingDimensions = env["VELTRIX_EMBEDDING_DIMENSIONS"]?.toIntOrNull()?.coerceIn(8, 4096) ?: 64,
                testEmbeddingEnabled = env["VELTRIX_TEST_EMBEDDING_MOCK"]?.equals("true", ignoreCase = true) == true,
                storageProvider = env["VELTRIX_STORAGE_PROVIDER"] ?: "local",
                s3Endpoint = env["VELTRIX_S3_ENDPOINT"]?.trimEnd('/')?.takeIf { it.isNotBlank() },
                s3Region = env["VELTRIX_S3_REGION"] ?: "us-east-1",
                s3Bucket = env["VELTRIX_S3_BUCKET"] ?: "veltrix-hom-vnext",
                s3AccessKey = env["VELTRIX_S3_ACCESS_KEY"]?.takeIf { it.isNotBlank() },
                s3SecretKey = env["VELTRIX_S3_SECRET_KEY"]?.takeIf { it.isNotBlank() },
                s3PathStyle = env["VELTRIX_S3_PATH_STYLE"]?.let { !it.equals("false", true) } ?: true,
                storageSignedUrlTtlSeconds = env["VELTRIX_STORAGE_SIGNED_URL_TTL"]?.toLongOrNull()?.coerceIn(30, 3600) ?: 300,
                workerEnabled = env["VELTRIX_WORKERS_ENABLED"]?.let { !it.equals("false", true) } ?: true,
                googleServerClientIds = googleClientIds,
            )
        }
    }
}
