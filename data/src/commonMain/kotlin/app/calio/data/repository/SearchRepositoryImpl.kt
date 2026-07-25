package app.calio.data.repository

import app.calio.data.mapper.toDomain
import app.calio.database.CalioDatabase
import app.calio.domain.repository.SearchHit
import app.calio.domain.repository.SearchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SearchRepositoryImpl(
    private val database: CalioDatabase,
    private val dispatcher: CoroutineDispatcher,
) : SearchRepository {

    /**
     * The full-text index answers with identifiers and a relevance score; the rows themselves are
     * fetched afterwards. Keeping the entity out of the index is what allows one index to span both
     * events and tasks, and it keeps the index small enough to stay fast.
     *
     * The ranking from the index is preserved: re-sorting the loaded rows would throw away the only
     * thing the search actually computed.
     */
    override suspend fun search(query: String, limit: Int): List<SearchHit> = withContext(dispatcher) {
        val sanitised = query.trim()
        if (sanitised.isEmpty()) return@withContext emptyList()

        database.searchQueries.search(query = sanitised, limit = limit.toLong())
            .executeAsList()
            .mapNotNull { hit ->
                val id = hit.entity_id ?: return@mapNotNull null
                when (hit.entity_type) {
                    "EVENT" -> database.eventsQueries.selectById(id).executeAsOneOrNull()
                        ?.let { SearchHit.EventHit(it.toDomain()) }

                    "TASK" -> database.tasksQueries.selectById(id).executeAsOneOrNull()
                        ?.let { SearchHit.TaskHit(it.toDomain()) }

                    else -> null
                }
            }
    }
}
