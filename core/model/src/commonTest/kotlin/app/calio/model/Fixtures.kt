package app.calio.model

import kotlin.time.Instant

/**
 * Minimal fixtures for the model tests. Defaults are chosen so a test only mentions the fields it
 * actually cares about, which keeps the intent of each test visible.
 */
internal val testDevice = DeviceId("test-device")

internal fun audit(
    createdAt: Instant = Instant.parse("2026-07-01T08:00:00Z"),
    updatedAt: Instant = createdAt,
    revision: Revision = Revision.of(updatedAt.toEpochMilliseconds(), 0, testDevice),
    deletedAt: Instant? = null,
): AuditFields = AuditFields(
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
    originDevice = testDevice,
    deletedAt = deletedAt,
)
