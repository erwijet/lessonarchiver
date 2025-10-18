package com.lessonarchiver

import com.lessonarchiver.db.ManagedTable
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.flywaydb.core.Flyway
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.qualifier.named
import org.koin.ktor.ext.getKoin
import java.io.File

fun Application.configureMigrations() {
    val koin = getKoin()
    koin.get<Database>()

    val flyway =
        Flyway
            .configure()
            .also { it.pluginRegister.getPlugin(PostgreSQLConfigurationExtension::class.java).isTransactionalLock = false }
            .dataSource(
                koin.get<String>(named("db.url")),
                koin.get<String>(named("db.username")),
                koin.get<String>(named("db.password")),
            ).defaultSchema("lessonarchiver")
            .locations("classpath:migrations")
            .validateMigrationNaming(true)
            .cleanDisabled(koin.get(named("mode")) !in listOf("local"))
            .load()

    val result = flyway.migrate()

    if (!result.success) {
        throw Exception("Failed to migrate database: ${result.migrationsExecuted} migrations executed")
    }

    val migrationStmts =
        transaction {
            MigrationUtils.statementsRequiredForDatabaseMigration(
                *ManagedTable.getAll().toTypedArray().also {
                    log.info("Found ${it.size} managed tables.")
                },
            )
        }

    if (migrationStmts.isNotEmpty()) {
        log.error("Database Migration needed!")
        File("./src/main/resources/migration.sql")
            .apply { createNewFile() }
            .apply { writeText(migrationStmts.joinToString(";\n\n")) }
        throw Exception("Database Migration needed!")
    }
}
