package app.calio.data.repository

import app.calio.data.TestEnvironment
import app.calio.data.calendar
import app.calio.data.event
import app.calio.model.CalendarId
import app.calio.model.CalendarOrigin
import app.calio.model.CalendarProvider
import app.calio.model.EventId
import app.calio.model.ExternalAccountId
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule that keeps the two worlds apart.
 *
 * A calendar mirrored from Google or Outlook is mirrored by every device for itself. If its changes
 * also went into Calio's own outbox, the same edit would arrive twice by two routes — and the two
 * routes disagree about what a revision means, so the second arrival could not even be recognised as
 * a repeat.
 */
class ExternalCalendarRoutingTest {

    private val environment = TestEnvironment()
    private val database = environment.database

    private val account = ExternalAccountId("account-1")

    @AfterTest
    fun tearDown() = environment.close()

    private suspend fun givenMirroredCalendar(isReadOnly: Boolean = false) {
        database.accountsQueries.insertAccount(
            id = account.value,
            provider = CalendarProvider.GOOGLE.name,
            display_name = "someone@example.com",
            status = "CONNECTED",
            connected_at = environment.clock.now().toEpochMilliseconds(),
        )
        environment.calendars.upsert(
            calendar(id = "calendar-1", name = "Work").copy(
                origin = CalendarOrigin.External(
                    accountId = account,
                    provider = CalendarProvider.GOOGLE,
                    externalId = "primary",
                    isReadOnly = isReadOnly,
                ),
            ),
        )
    }

    private fun deviceChanges() = environment.pendingChanges()

    private fun providerChanges() =
        database.accountsQueries.selectPendingExternalChanges(limit = 100).executeAsList()

    @Test
    fun `a mirrored calendar keeps its origin`() = runTest {
        givenMirroredCalendar()

        val stored = environment.calendars.byId(CalendarId("calendar-1"))!!
        val external = stored.external

        assertEquals(account, external?.accountId)
        assertEquals("primary", external?.externalId)
        assertFalse(stored.takesPartInDeviceSync)
    }

    @Test
    fun `a local calendar stays local`() = runTest {
        environment.calendars.upsert(calendar())

        val stored = environment.calendars.byId(CalendarId("calendar-1"))!!

        assertEquals(CalendarOrigin.Local, stored.origin)
        assertTrue(stored.takesPartInDeviceSync)
        assertTrue(stored.isWritable)
    }

    @Test
    fun `a read-only calendar says so`() = runTest {
        givenMirroredCalendar(isReadOnly = true)

        assertFalse(environment.calendars.byId(CalendarId("calendar-1"))!!.isWritable)
    }

    @Test
    fun `a mirrored calendar is not reported to the device outbox`() = runTest {
        givenMirroredCalendar()

        assertEquals(0, deviceChanges().count { it.entity_type == "CALENDAR" })
    }

    @Test
    fun `a local calendar is reported to the device outbox`() = runTest {
        environment.calendars.upsert(calendar())

        assertEquals(1, deviceChanges().count { it.entity_type == "CALENDAR" })
    }

    @Test
    fun `an event in a mirrored calendar goes to the provider outbox only`() = runTest {
        givenMirroredCalendar()

        environment.events.upsert(event())

        assertEquals(0, deviceChanges().count { it.entity_type == "EVENT" })
        val queued = providerChanges().single()
        assertEquals("event-1", queued.event_id)
        assertEquals("calendar-1", queued.calendar_id)
        assertEquals("UPSERT", queued.operation)
    }

    @Test
    fun `an event in a local calendar goes to the device outbox only`() = runTest {
        environment.calendars.upsert(calendar())

        environment.events.upsert(event())

        assertEquals(1, deviceChanges().count { it.entity_type == "EVENT" })
        assertTrue(providerChanges().isEmpty())
    }

    @Test
    fun `deleting a mirrored event goes to the provider outbox`() = runTest {
        givenMirroredCalendar()
        environment.events.upsert(event())

        environment.events.delete(EventId("event-1"))

        assertEquals(0, deviceChanges().count { it.entity_type == "EVENT" })
        assertEquals(
            listOf("UPSERT", "DELETE"),
            providerChanges().map { it.operation },
        )
    }

    @Test
    fun `deleting a local event still goes to the device outbox`() = runTest {
        environment.calendars.upsert(calendar())
        environment.events.upsert(event())

        environment.events.delete(EventId("event-1"))

        assertEquals(
            listOf("UPSERT", "DELETE"),
            deviceChanges().filter { it.entity_type == "EVENT" }.map { it.operation },
        )
        assertTrue(providerChanges().isEmpty())
    }

    @Test
    fun `the two outboxes are counted separately`() = runTest {
        givenMirroredCalendar()
        environment.calendars.upsert(calendar(id = "local-1", name = "Private"))
        environment.events.upsert(event(id = "mirrored", calendarId = "calendar-1"))
        environment.events.upsert(event(id = "own", calendarId = "local-1"))

        assertEquals(1, database.syncQueries.countPendingChanges().executeAsOne().toInt() - 1)
        assertEquals(1, database.accountsQueries.countPendingExternalChanges().executeAsOne().toInt())
    }

    @Test
    fun `removing an account takes its calendar catalogue with it`() = runTest {
        givenMirroredCalendar()
        database.accountsQueries.insertExternalCalendar(
            account_id = account.value,
            external_id = "primary",
            name = "Work",
            color = null,
            is_read_only = 0,
        )

        database.accountsQueries.deleteAccount(account.value)

        assertTrue(database.accountsQueries.selectExternalCalendars(account.value).executeAsList().isEmpty())
    }
}
