package org.example.project.categories.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.AppGraph
import org.example.project.R
import org.example.project.categories.presentation.CategoriesEffect
import org.example.project.categories.presentation.CategoriesIntent
import org.example.project.categories.presentation.CategoriesState
import org.example.project.categories.presentation.CategoriesViewModel
import org.example.project.categories.presentation.ui.section.CategoriesErrorSection
import org.example.project.categories.presentation.ui.section.CategoriesList
import org.example.project.categories.presentation.ui.section.CategoriesShimmerList

/** Connects the ViewModel: collects state, handles effects, forwards intents. */
@Composable
fun CategoriesRoute(graph: AppGraph, onOpenCategory: (categoryId: String) -> Unit) {
    val viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.factory(graph))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CategoriesEffect.OpenCategory -> onOpenCategory(effect.categoryId)
            }
        }
    }

    CategoriesScreen(state = state, onIntent = viewModel::onIntent)
}

/** A thin assembler: picks which pre-computed state to show. No business decisions here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(state: CategoriesState, onIntent: (CategoriesIntent) -> Unit) {
    val onRetry = remember(onIntent) { { onIntent(CategoriesIntent.Retry) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.categories_title)) }) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> CategoriesShimmerList()
                state.error != null -> CategoriesErrorSection(error = state.error, onRetry = onRetry)
                else -> CategoriesList(categories = state.categories, onIntent = onIntent)
            }
        }
    }
}
