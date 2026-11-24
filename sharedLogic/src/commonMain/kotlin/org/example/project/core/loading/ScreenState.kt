package org.example.project.core.loading

import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.ErrorMessage
import org.example.project.core.error.MessageKey
import org.example.project.core.error.RequestKind

/** The one thing a screen is showing. */
sealed interface Content<out T> {
    data object Loading : Content<Nothing>
    data class Loaded<out T>(val data: T) : Content<T>

    /** The server answered, and there is nothing to list. */
    data object Empty : Content<Nothing>
    data class Failed(val display: ErrorDisplay.FullScreen) : Content<Nothing>
}

/**
 * The state every screen that loads something shares (R1, R6). Loading and an error at once can't be built.
 * Move between states only with [loading], [loaded] and [failed].
 */
data class ScreenState<out T>(
    val content: Content<T> = Content.Loading,
    /** Updating what is already on screen: a small spinner over it, never a blank screen. */
    val isRefreshing: Boolean = false,
    /** "Slow connection, still trying…", see [withSlowSignal]. */
    val isSlow: Boolean = false,
) {
    /** What to tell ErrorPresenter about a load from here: a screen with an answer on it is only refreshing. */
    val nextLoadKind: RequestKind
        get() = if (hasAnswer) RequestKind.Refresh else RequestKind.InitialLoad

    internal val hasAnswer: Boolean get() = content is Content.Loaded || content is Content.Empty
}

/** A load (first, refresh or retry) begins. */
fun <T> ScreenState<T>.loading(): ScreenState<T> =
    if (hasAnswer) copy(isRefreshing = true, isSlow = false) else ScreenState()

fun <T> ScreenState<T>.loaded(data: T): ScreenState<T> =
    ScreenState(if (data is Collection<*> && data.isEmpty()) Content.Empty else Content.Loaded(data))

/**
 * Data already on screen always stays. Without any, the screen must still leave Loading: a [display] that is
 * drawn elsewhere (an app action, a toast) leaves a Retry here for when it goes away.
 */
fun <T> ScreenState<T>.failed(display: ErrorDisplay): ScreenState<T> =
    if (hasAnswer) copy(isRefreshing = false, isSlow = false)
    else ScreenState(Content.Failed(display as? ErrorDisplay.FullScreen ?: GENERIC_RETRY))

private val GENERIC_RETRY = ErrorDisplay.FullScreen(ErrorMessage(MessageKey.Generic), canRetry = true)
