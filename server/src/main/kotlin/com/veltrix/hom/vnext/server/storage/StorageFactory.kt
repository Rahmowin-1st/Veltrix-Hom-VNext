package com.veltrix.hom.vnext.server.storage

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.server.ServerConfig

object StorageFactory {
    fun create(config:ServerConfig):StorageAdapter = when(config.storageProvider.lowercase()) {
        "local" -> {
            if(config.environment in setOf("staging","production")) throw DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,"Local storage cannot be source-of-truth outside development/test"))
            LocalStorageAdapter(config.sourceStorageRoot)
        }
        "s3","minio","s3-compatible" -> S3CompatibleStorageAdapter(config.s3Endpoint ?: "",config.s3Region,config.s3Bucket,config.s3AccessKey,config.s3SecretKey,config.s3PathStyle)
        else -> throw DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,"Unknown storage provider"))
    }
}
