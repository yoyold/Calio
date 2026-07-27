package app.calio.shared

import app.calio.data.sync.DatabaseSyncableStore
import app.calio.domain.repository.SettingsRepository
import app.calio.sync.FolderRemoteSyncSource
import app.calio.sync.PassthroughCipher
import app.calio.sync.SyncCoordinator
import app.calio.sync.SyncEngine
import app.calio.sync.SyncStatus
import app.calio.sync.SyncableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Runs synchronisation while a folder is configured, and not otherwise.
 *
 * Nothing is drained when no folder is set. It would be easy to point the engine at a remote that
 * accepts and forgets, which would keep the outbox tidy — and would also throw away the history that
 * the first real remote needs to catch up on. An unconfigured installation therefore simply
 * accumulates, exactly as an offline one does.
 *
 * Changing the folder tears the previous round down and starts again from that folder's own cursor,
 * because a cursor means nothing outside the folder that issued it.
 */
class CalioSync(
    private val store: SyncableStore,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.System,
) {

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var coordinator: SyncCoordinator? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.observe()
                .map { it.syncFolderPath }
                .distinctUntilChanged()
                .collectLatest { folder ->
                    coordinator?.stop()
                    coordinator = null

                    if (folder.isNullOrBlank()) {
                        _status.value = SyncStatus(pendingChanges = _status.value.pendingChanges)
                        return@collectLatest
                    }

                    val started = SyncCoordinator(
                        runner = SyncEngine(
                            store = store,
                            remote = FolderRemoteSyncSource(folder),
                            cipher = PassthroughCipher(),
                            clock = clock,
                        ),
                        pendingChanges = store.observePendingCount(),
                        clock = clock,
                    )
                    coordinator = started
                    started.start(scope)
                    launch { started.status.collect { _status.value = it } }
                }
        }
    }

    fun requestSync() {
        coordinator?.requestSync()
    }
}

/** Builds the store the engine reads and writes through. */
internal fun CalioContainer.syncableStore(): SyncableStore =
    DatabaseSyncableStore(database, revisions, dispatcher)
