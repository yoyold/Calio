package app.calio.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the SQLite driver for the current platform.
 *
 * The two platforms need genuinely different inputs — Android needs a `Context`, the desktop needs a
 * file path — so this is an interface implemented per platform rather than an `expect` declaration
 * with an awkward common constructor. The composition root binds the right one, and tests bind an
 * in-memory implementation.
 *
 * Implementations are responsible for creating or migrating the schema and for switching foreign key
 * enforcement on: SQLite disables it per connection by default, which would silently turn every
 * cascade in this schema into a no-op.
 */
fun interface DatabaseDriverFactory {
    fun create(): SqlDriver
}

fun createCalioDatabase(driverFactory: DatabaseDriverFactory): CalioDatabase =
    CalioDatabase(driverFactory.create())
