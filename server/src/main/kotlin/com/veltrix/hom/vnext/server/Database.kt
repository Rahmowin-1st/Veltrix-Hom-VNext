package com.veltrix.hom.vnext.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection

class Database(config: ServerConfig) : AutoCloseable {
    private val dataSource: HikariDataSource
    init {
        val hc = HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            username = config.databaseUser
            password = config.databasePassword
            maximumPoolSize = 12
            minimumIdle = 1
            connectionTimeout = 5_000
            validationTimeout = 2_000
            idleTimeout = 60_000
            maxLifetime = 600_000
            isAutoCommit = false
            poolName = "veltrix-vnext-db"
        }
        dataSource = HikariDataSource(hc)
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:migrations")
            .validateMigrationNaming(true)
            .load()
            .migrate()
    }

    fun <T> tx(block: (Connection) -> T): T {
        dataSource.connection.use { c ->
            return try {
                val result = block(c)
                c.commit()
                result
            } catch (t: Throwable) {
                c.rollback()
                throw t
            }
        }
    }

    fun ping(): Boolean = runCatching { tx { c -> c.createStatement().use { it.executeQuery("SELECT 1").next() } } }.getOrDefault(false)
    override fun close() = dataSource.close()
}
