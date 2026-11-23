package com.arflix.tv.ui.components

import android.content.Context
import com.arflix.tv.R
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.repository.PersonalListsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State behind [ListPickerMenu], shared by every screen that offers "Add to list".
 * A screen's ViewModel owns one, with its own scope and a way to show the result.
 */
class PersonalListPicker(
    private val scope: CoroutineScope,
    private val repository: PersonalListsRepository,
    private val context: Context,
    private val onResult: (message: String, success: Boolean) -> Unit
) {
    /** The user's lists for one title, and which ones already hold it. */
    data class State(
        val item: MediaItem,
        val isLoading: Boolean = true,
        val choices: List<PersonalListsRepository.Choice> = emptyList(),
        val busyKeys: Set<String> = emptySet()
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /** Whether "Add to list" applies: a service with personal lists is connected. */
    suspend fun isAvailable(): Boolean = runCatching { repository.hasProvider() }
        .onFailure { if (it is CancellationException) throw it }
        .getOrDefault(false)

    fun open(item: MediaItem) {
        if (item.id <= 0) return
        _state.value = State(item)
        scope.launch {
            val choices = runCatching { repository.choicesFor(item.mediaType, item.id) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrDefault(emptyList())
            _state.update { current ->
                current?.takeIf { it.item == item }?.copy(isLoading = false, choices = choices) ?: current
            }
        }
    }

    fun toggle(choice: PersonalListsRepository.Choice) {
        val current = _state.value ?: return
        val key = choice.list.key
        if (key in current.busyKeys) return
        val item = current.item
        val add = !choice.contains
        _state.update { it?.copy(busyKeys = it.busyKeys + key) }
        scope.launch {
            val ok = repository.setMembership(choice.list, item.mediaType, item.id, add)
            _state.update { state ->
                state?.takeIf { it.item == item }?.copy(
                    busyKeys = state.busyKeys - key,
                    choices = state.choices.map {
                        if (ok && it.list.key == key) it.copy(contains = add) else it
                    }
                ) ?: state
            }
            val message = when {
                !ok -> context.getString(R.string.list_update_failed, choice.list.title)
                add -> context.getString(R.string.list_added_to, choice.list.title)
                else -> context.getString(R.string.list_removed_from, choice.list.title)
            }
            onResult(message, ok)
        }
    }

    fun close() {
        _state.value = null
    }
}
