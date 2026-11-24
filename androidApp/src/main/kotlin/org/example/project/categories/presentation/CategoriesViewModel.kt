package org.example.project.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.categories.presentation.mapper.toEffect
import org.example.project.categories.presentation.mapper.toUi
import org.example.project.core.loading.ScreenLoaderFactory
import org.example.project.core.result.map

/** Loading follows the shared rules in ScreenLoader; only what is specific to this screen lives here. */
class CategoriesViewModel(
    getCategoriesUseCase: GetCategoriesUseCase,
    loaderFactory: ScreenLoaderFactory,
) : ViewModel() {

    private val loader = loaderFactory.create(viewModelScope) { getCategoriesUseCase().map { it.toUi() } }

    val state: StateFlow<CategoriesState> = loader.state

    private val _effect = Channel<CategoriesEffect>(Channel.BUFFERED)
    val effect: Flow<CategoriesEffect> = _effect.receiveAsFlow()

    init {
        loader.effects.onEach { _effect.send(it.toEffect()) }.launchIn(viewModelScope)
        onIntent(CategoriesIntent.LoadCategories)
    }

    /** The only public function. Every user action comes through here. */
    fun onIntent(intent: CategoriesIntent) {
        when (intent) {
            CategoriesIntent.LoadCategories,
            CategoriesIntent.Retry,
            CategoriesIntent.Refresh -> loader.load()
            is CategoriesIntent.CategoryClicked -> sendEffect(CategoriesEffect.OpenCategory(intent.categoryId))
        }
    }

    private fun sendEffect(effect: CategoriesEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}
