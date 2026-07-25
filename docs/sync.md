# Synchronisation

## Principle: local-first

The device database is the source of truth for the user interface. Nothing in the UI path ever waits
for the network. Synchronisation is a background reconciliation process between equal peers, not a
client fetching from an authority.

Consequences:

- Every write succeeds offline, immediately.
- The remote side is optional. With no remote configured the app is fully functional; the outbox
  simply accumulates and is drained when a remote is added.
- The transport is replaceable without touching repositories or the UI.

## Write path

```
UseCase → Repository.upsert(entity)
              │
              └─ single database transaction
                   ├─ write entity row (revision = newHlc(), origin_device = thisDevice)
                   └─ append change_log row (entity_type, entity_id, operation, revision)
```

Because the entity and its outbox record are committed together, the outbox can never disagree with
the data. A crash between the two is impossible; a crash after the commit leaves a pending outbox
row that is retried on next start.

## Read path from remote

```
SyncEngine.sync()
   1. push   → drain change_log where synced_at IS NULL
   2. pull   → fetch envelopes newer than remote_cursor
   3. apply  → decrypt, map, resolve conflicts, write, advance cursor
```

Both directions are idempotent and resumable. Interrupting a sync round loses no data: the cursor
only advances after a batch has been committed locally.

## Logical time: hybrid logical clock

Wall clocks on two devices disagree. Using `updatedAt` for conflict resolution would let a device
with a fast clock always win. Calio therefore stores a **hybrid logical clock** value as `revision`:

```
revision = <physicalMillis>-<counter>-<deviceId>
```

- `physicalMillis` is the local wall clock, never allowed to go backwards.
- `counter` increments when two events share the same millisecond, or when an incoming remote clock
  is ahead of the local one.
- `deviceId` is a stable tiebreaker, which makes the ordering total and identical on every device.

Comparison is lexicographic on the tuple. Every device that sees the same two revisions reaches the
same verdict, which is what makes eventual convergence deterministic.

## Conflict handling

A conflict exists when a remote revision arrives for an entity that also has a pending local change
whose revision is not an ancestor of it.

| Situation                                     | Resolution                                              |
| --------------------------------------------- | ------------------------------------------------------- |
| Remote newer, no pending local change          | apply remote                                            |
| Local newer, remote older                      | keep local, re-push                                     |
| Both changed since last sync                   | last-writer-wins by HLC **and** record a `sync_conflict` |
| Local delete vs. remote update                 | delete wins only if its revision is newer                |
| Both deleted                                   | converge on tombstone, no conflict                       |

Last-writer-wins is the default because it always converges and never blocks the user. The recorded
conflict is what prevents silent data loss: the UI shows a banner listing affected entities and lets
the user restore the discarded version, which is stored alongside the resolution.

Field-level merging is deliberately not attempted in the first iteration. The `sync_conflict` table
already carries both full revisions, so a smarter merge strategy can be introduced later without a
schema change.

## Transport abstraction

```kotlin
interface RemoteSyncSource {
    suspend fun push(envelopes: List<SyncEnvelope>): PushResult
    suspend fun pull(cursor: RemoteCursor?, limit: Int): PullResult
    suspend fun capabilities(): RemoteCapabilities
}
```

`SyncEnvelope` is transport-agnostic and already encryption-shaped:

```kotlin
data class SyncEnvelope(
    val entityType: String,
    val entityId: String,
    val operation: Operation,
    val revision: String,
    val payload: SealedPayload,   // ciphertext + nonce + key id + scheme
)
```

Planned implementations, in the order they become useful:

1. `NoOpRemoteSyncSource` — local only; the app is complete without a server.
2. `FileRemoteSyncSource` — a synced folder or a LAN share; gives real two-device sync with zero
   infrastructure and is the fixture used for end-to-end sync tests.
3. `HttpRemoteSyncSource` — a small append-only endpoint over Ktor, cloud-hostable.

The engine only requires that the remote can store opaque blobs, return them in a stable order and
hand out a cursor. This is intentionally the weakest possible contract, so almost any backend
qualifies.

## Scheduling

`SyncScheduler` is an interface with per-platform implementations:

| Trigger                        | Android                      | Windows                              |
| ------------------------------ | ---------------------------- | ------------------------------------ |
| App start / resume             | lifecycle observer           | window focus                         |
| Local change (debounced 2 s)   | coroutine in app scope       | coroutine in app scope               |
| Periodic                       | `WorkManager` periodic work  | coroutine timer in the app process   |
| Connectivity regained          | network callback             | network availability listener        |

Retries use exponential backoff with jitter and are capped; failures never surface as blocking
dialogs, only as a status indicator.

## End-to-end encryption

The envelope format is encrypted-by-design from the first commit, even while the pass-through cipher
is in use. This avoids a migration of already-synced data later.

```
passphrase ──Argon2id──► master key
                            ├── derive: data key   (AES-256-GCM, per entity payload)
                            └── derive: index key  (deterministic ids for the remote)
```

- The remote sees: entity type, opaque id, revision, ciphertext. Never titles, times or participants.
- Nonces are random per payload; the key id in the envelope allows key rotation.
- The master key is stored in platform secure storage (Android Keystore, Windows credential
  storage) and never written to the database.
- Key material never enters the outbox, the export files or the logs.

`PayloadCipher` has two implementations from the start: `PassthroughCipher` (local-only mode) and
`AesGcmCipher`. Switching is a binding change in the composition root.

## Backup and export

Backup is a separate concern from sync and uses the same serialisation:

- **Backup archive** — full database snapshot plus attachments, optionally passphrase-encrypted.
- **ICS** — standards-compliant export/import per calendar; recurrence maps directly to RRULE.
- **CSV** — flat occurrence list for spreadsheets.
- **PDF** — rendered day, week or month view for printing.

## Testing the sync engine

- The HLC has property tests: monotonicity, total order, convergence under permutation.
- Conflict resolution is tested as a pure function over (localRevision, remoteRevision, states).
- Two in-memory databases plus `FileRemoteSyncSource` form an end-to-end test that simulates two
  devices, including offline edits on both sides, and asserts identical final state.
