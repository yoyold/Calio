package app.calio.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/**
 * Desktop driver, backed by a database file at [databasePath].
 *
 * Passing the schema to the driver lets SQLDelight create it on first start and migrate it on later
 * ones, so no separate bootstrap step can be forgotten.
 */
class JvmDatabaseDriverFactory(
    private val databasePath: String,
) : DatabaseDriverFactory {

    override fun create(): SqlDriver {
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:$databasePath",
            properties = Properties(),
            schema = CalioDatabase.Schema,
        )
        driver.enableForeignKeys()
        return driver
    }
}

/** Foreign key enforcement is off by default in SQLite and has to be switched on per connection. */
internal fun SqlDriver.enableForeignKeys() {
    execute(identifier = null, sql = "PRAGMA foreign_keys = ON;", parameters = 0)
}
