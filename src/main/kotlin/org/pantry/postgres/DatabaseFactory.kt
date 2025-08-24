package org.pantry.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.sql.Connection
import javax.sql.DataSource

object DatabaseFactory {
    private lateinit var dataSource: HikariDataSource

    fun init(url: String, user: String, password: String) {
        val config = HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }

        dataSource = HikariDataSource(config)

        runFlywayMigrations(dataSource)

        Database.connect(this.dataSource)
    }

    private fun runFlywayMigrations(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun getConnection(): Connection = dataSource.connection
}
