package org.example.project.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.categories.presentation.mapper.toErrorUi
import org.example.project.categories.presentation.mapper.toUi
import org.example.project.network.ApiResult

class CategoriesViewModel(
    private val getCategoriesUseCase: GetCategoriesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    private val _effect = Channel<CategoriesEffect>(Channel.BUFFERED)
    val effect: Flow<CategoriesEffect> = _effect.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        onIntent(CategoriesIntent.LoadCategories)
    }

    /** The only public function. Every user action comes through here. */
    fun onIntent(intent: CategoriesIntent) {
        when (intent) {
            CategoriesIntent.LoadCategories,
            CategoriesIntent.Retry -> loadCategories()
            is CategoriesIntent.CategoryClicked -> sendEffect(CategoriesEffect.OpenCategory(intent.categoryId))
        }
    }

    private fun loadCategories() {
        loadJob?.cancel()                      // a second Retry tap replaces the running request
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = getCategoriesUseCase()) {
                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, categories = result.data.toUi())
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.error.toErrorUi())
                }
            }
        }
    }

    private fun sendEffect(effect: CategoriesEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}
