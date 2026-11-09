package org.example.project.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppGraph
import org.example.project.categories.domain.model.Category
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.categories.presentation.mapper.CategoryUiListMapper
import org.example.project.categories.presentation.mapper.CategoryUiMapper
import org.example.project.categories.presentation.mapper.ErrorUiMapper
import org.example.project.categories.presentation.model.CategoryUi
import org.example.project.categories.presentation.model.ErrorUi
import org.example.project.core.mapper.ListMapper
import org.example.project.core.mapper.Mapper
import org.example.project.network.ApiResult
import org.example.project.network.DataError

class CategoriesViewModel(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val categoryUiListMapper: ListMapper<Category, CategoryUi>,
    private val errorUiMapper: Mapper<DataError, ErrorUi>,
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
                    it.copy(isLoading = false, categories = categoryUiListMapper.map(result.data))
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = errorUiMapper.map(result.error))
                }
            }
        }
    }

    private fun sendEffect(effect: CategoriesEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }

    companion object {
        /** Factory pattern: construction stays out of composables. Hilt or Koin replaces this later. */
        fun factory(graph: AppGraph): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CategoriesViewModel(
                    getCategoriesUseCase = graph.getCategoriesUseCase,
                    categoryUiListMapper = CategoryUiListMapper(itemMapper = CategoryUiMapper()),
                    errorUiMapper = ErrorUiMapper(),
                )
            }
        }
    }
}
