package org.pantry.repositories

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.postgres.tables.HouseholdTable
import org.pantry.postgres.tables.ItemTable
import org.pantry.postgres.tables.RecipeIngredientTable
import org.pantry.postgres.tables.RecipeStepTable
import org.pantry.postgres.tables.RecipeTable
import org.pantry.postgres.tables.RecipeTagTable
import org.pantry.postgres.tables.UserTable
import org.testcontainers.containers.PostgreSQLContainer

// Other tests build their schema from the Exposed objects, so this is the only check
// that the Flyway migrations (what production runs) match them. It compares column
// names and nullability; types and constraint names may differ harmlessly.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationTest {

    private val postgres = PostgreSQLContainer("postgres:15").apply { start() }

    @AfterAll
    fun teardown() {
        postgres.stop()
    }

    @Test
    fun `migrations create every Exposed column with the same nullability`() {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        Database.connect(postgres.jdbcUrl, "org.postgresql.Driver", postgres.username, postgres.password)

        for (table in listOf(
            UserTable, HouseholdTable, HouseholdMemberTable, GroceryListTable, ItemTable,
            RecipeTable, RecipeIngredientTable, RecipeStepTable, RecipeTagTable
        )) {
            val expected = table.columns.associate { it.name to it.columnType.nullable }
            assertEquals(expected, migratedColumns(table), "Columns of ${table.tableName}")
        }
    }

    // column name -> nullable, as Flyway left it
    private fun migratedColumns(table: Table): Map<String, Boolean> = transaction {
        val columns = mutableMapOf<String, Boolean>()
        TransactionManager.current().exec(
            "SELECT column_name, is_nullable FROM information_schema.columns WHERE table_name = '${table.tableName}'"
        ) { rs ->
            while (rs.next()) columns[rs.getString(1)] = rs.getString(2) == "YES"
        }
        columns
    }
}
