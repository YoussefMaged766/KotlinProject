package org.example.project.categories.presentation.ui.section

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.example.project.categories.presentation.CategoriesIntent
import org.example.project.categories.presentation.model.CategoryUi

@Composable
fun CategoriesList(categories: List<CategoryUi>, onIntent: (CategoriesIntent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(categories, key = { it.id }) { category ->
            val onClick = remember(category.id, onIntent) {
                { onIntent(CategoriesIntent.CategoryClicked(category.id)) }
            }
            CategoryRow(category = category, onClick = onClick)
        }
    }
}

/** One item: plain data in, a callback out. No list or index logic. */
@Composable
fun CategoryRow(category: CategoryUi, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(category.name) },
        supportingContent = category.subtitleRes?.let { subtitleRes ->
            { Text(stringResource(subtitleRes)) }
        },
    )
}

@Composable
fun CategoriesShimmerList(count: Int = 6) {
    LazyColumn(Modifier.fillMaxSize(), userScrollEnabled = false) {
        items(count) { CategoryShimmer() }
    }
}

/** Same height as CategoryRow (56dp), with a pulsing placeholder instead of text. */
@Composable
fun CategoryShimmer() {
    val transition = rememberInfiniteTransition(label = "categoryShimmer")
    val placeholderAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "placeholderAlpha",
    )
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth(0.5f)
            .height(20.dp)
            .graphicsLayer { alpha = placeholderAlpha }   // read in the draw phase: no recomposition per frame
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    )
}
