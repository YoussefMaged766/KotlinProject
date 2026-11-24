package org.example.project.categories.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.example.project.R
import org.example.project.categories.presentation.CategoriesEffect
import org.example.project.categories.presentation.CategoriesIntent
import org.example.project.categories.presentation.CategoriesState
import org.example.project.categories.presentation.CategoriesViewModel
import org.example.project.categories.presentation.ui.section.CategoriesEmptySection
import org.example.project.categories.presentation.ui.section.CategoriesList
import org.example.project.categories.presentation.ui.section.CategoriesShimmerList
import org.example.project.core.loading.Content
import org.example.project.core.presentation.error.ErrorDialogHost
import org.example.project.core.presentation.error.FullScreenError
import org.example.project.core.presentation.error.rememberErrorHostState
import org.example.project.core.presentation.loading.SlowConnectionHint
import org.koin.androidx.compose.koinViewModel

/**
 * Connects the ViewModel: collects state, handles effects, forwards intents.
 * The ViewModel is a default parameter: Koin in the app, a hand-built one in previews and tests.
 */
@Composable
fun CategoriesRoute(
    onOpenCategory: (categoryId: String) -> Unit,
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorHost = rememberErrorHostState(onRetry = { viewModel.onIntent(CategoriesIntent.Retry) })
    val backOnline = stringResource(R.string.back_online)
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenCategory by rememberUpdatedState(onOpenCategory)
    val currentBackOnline by rememberUpdatedState(backOnline)

    LaunchedEffect(viewModel, errorHost, lifecycleOwner) {
        val snackbars = this    // outside repeatOnLifecycle, so leaving the app doesn't cancel the one on screen
        // Only while visible: an effect sent in the background waits in the ViewModel's channel until it can be seen.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effect.collect { effect ->
                when (effect) {
                    is CategoriesEffect.OpenCategory -> currentOnOpenCategory(effect.categoryId)
                    // Launched: a snackbar suspends until it is gone, and must not hold up the next effect.
                    is CategoriesEffect.ShowError -> snackbars.launch { errorHost.show(effect.display) }
                    CategoriesEffect.ShowBackOnline -> snackbars.launch { errorHost.showNotice(currentBackOnline) }
                }
            }
        }
    }

    CategoriesScreen(state = state, snackbarHostState = errorHost.snackbar, onIntent = viewModel::onIntent)
    ErrorDialogHost(errorHost)
}

/** A thin assembler: picks which pre-computed state to show. No business decisions here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesState,
    snackbarHostState: SnackbarHostState,
    onIntent: (CategoriesIntent) -> Unit,
) {
    val onRetry = remember(onIntent) { { onIntent(CategoriesIntent.Retry) } }
    val onRefresh = remember(onIntent) { { onIntent(CategoriesIntent.Refresh) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.categories_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedVisibility(state.isSlow) { SlowConnectionHint() }

            when (val content = state.content) {
                Content.Loading -> CategoriesShimmerList()
                is Content.Failed -> FullScreenError(display = content.display, onRetry = onRetry)
                is Content.Loaded -> PullToRefreshBox(state.isRefreshing, onRefresh) {
                    CategoriesList(categories = content.data, onIntent = onIntent)
                }
                Content.Empty -> PullToRefreshBox(state.isRefreshing, onRefresh) { CategoriesEmptySection() }
            }
        }
    }
}
