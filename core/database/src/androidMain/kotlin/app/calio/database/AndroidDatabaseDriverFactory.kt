package app.calio.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android driver, backed by a database file in the application's private storage.
 *
 * Foreign keys are switched on in `onConfigure` rather than by executing a pragma afterwards,
 * because Android may open several connections and the setting is per connection.
 */
class AndroidDatabaseDriverFactory(
    private val context: Context,
    private val databaseName: String = DEFAULT_DATABASE_NAME,
) : DatabaseDriverFactory {

    override fun create(): SqlDriver = AndroidSqliteDriver(
        schema = CalioDatabase.Schema,
        context = context,
        name = databaseName,
        callback = object : AndroidSqliteDriver.Callback(CalioDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )

    private companion object {
        const val DEFAULT_DATABASE_NAME = "calio.db"
    }
}
