package app.calio.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.repository.SearchHit
import app.calio.domain.repository.SearchRepository
import app.calio.model.CalioColor
import app.calio.model.Category
import app.calio.model.Event
import app.calio.model.Task
import app.calio.model.resolveEventColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One row of the result list, with everything already resolved that drawing it needs. */
sealed interface SearchResult {
    data class EventResult(val event: Event, val color: CalioColor) : SearchResult
    data class TaskResult(val task: Task, val category: Category?) : SearchResult
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val isEmptyResult: Boolean get() = hasQuery && !isSearching && results.isEmpty()
}

sealed interface SearchUiEvent {
    data class QueryChanged(val query: String) : SearchUiEvent
    data object Clear : SearchUiEvent
}

/**
 * Full-text search over events and tasks.
 *
 * Typing does not search. Each keystroke restarts a short wait, and only a pause actually reaches
 * the index — otherwise a five letter word would run five queries of which four are thrown away
 * before anyone reads them.
 *
 * The ranking comes from the index and is left alone. Re-sorting the loaded rows here would discard
 * the only thing the search actually computed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val search: SearchRepository,
    private val calendars: CalendarRepository,
    private val categories: CategoryRepository,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<SearchUiState> = query
        .flatMapLatest { text -> resultsFor(text) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = SearchUiState(),
        )

    fun onEvent(event: SearchUiEvent) {
        query.value = when (event) {
            is SearchUiEvent.QueryChanged -> event.query
            SearchUiEvent.Clear -> ""
        }
    }

    private fun resultsFor(text: String) = flow {
        if (text.isBlank()) {
            emit(SearchUiState(query = text))
            return@flow
        }

        emit(SearchUiState(query = text, isSearching = true))
        delay(debounceMillis)

        val hits = search.search(text)
        emit(SearchUiState(query = text, results = enrich(hits), isSearching = false))
    }

    /**
     * Colours are looked up once per search rather than per row: a result list of fifty entries
     * would otherwise repeat the same two map lookups fifty times.
     */
    private suspend fun enrich(hits: List<SearchHit>): List<SearchResult> {
        val calendarsById = calendars.observeAll().first().associateBy { it.id }
        val categoriesById = categories.observeAll().first().associateBy { it.id }

        return hits.mapNotNull { hit ->
            when (hit) {
                is SearchHit.EventHit -> {
                    val calendar = calendarsById[hit.event.calendarId] ?: return@mapNotNull null
                    SearchResult.EventResult(
                        event = hit.event,
                        color = resolveEventColor(
                            colorOverride = hit.event.colorOverride,
                            category = hit.event.categoryId?.let(categoriesById::get),
                            calendar = calendar,
                        ),
                    )
                }

                is SearchHit.TaskHit -> SearchResult.TaskResult(
                    task = hit.task,
                    category = hit.task.categoryId?.let(categoriesById::get),
                )
            }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /** Long enough to swallow a burst of typing, short enough to feel immediate. */
        const val DEFAULT_DEBOUNCE_MILLIS = 220L
    }
}
