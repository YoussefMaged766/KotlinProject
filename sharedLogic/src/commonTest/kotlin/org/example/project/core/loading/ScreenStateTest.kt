package org.example.project.core.loading

import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.ErrorMessage
import org.example.project.core.error.MessageKey
import org.example.project.core.error.RequestKind
import org.example.project.core.events.GlobalEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/** R1: what the screen shows before, during and after a load. */
class ScreenStateTest {

    private val timeout = ErrorDisplay.FullScreen(ErrorMessage(MessageKey.Timeout), canRetry = true)
    private val toast = ErrorDisplay.Toast(ErrorMessage(MessageKey.Timeout), canRetry = true)
    private val genericRetry = ErrorDisplay.FullScreen(ErrorMessage(MessageKey.Generic), canRetry = true)

    private val first = ScreenState<List<String>>()
    private val withData = ScreenState(Content.Loaded(listOf("a")))
    private val empty = ScreenState<List<String>>(Content.Empty)
    private val failed = ScreenState<List<String>>(Content.Failed(timeout))

    @Test
    fun aNewScreenIsLoading() {
        assertEquals(Content.Loading, first.content)
        assertEquals(RequestKind.InitialLoad, first.nextLoadKind)
    }

    @Test
    fun loadingOverDataKeepsItOnScreen() {
        assertEquals(withData.copy(isRefreshing = true), withData.loading())
        assertEquals(RequestKind.Refresh, withData.nextLoadKind)
    }

    @Test
    fun loadingOverAnEmptyResultIsARefreshToo() {
        assertEquals(empty.copy(isRefreshing = true), empty.loading())
        assertEquals(RequestKind.Refresh, empty.nextLoadKind)
    }

    @Test
    fun retryingAFailedScreenShowsLoadingAgain() {
        assertEquals(first, failed.loading())
        assertEquals(RequestKind.InitialLoad, failed.nextLoadKind)
    }

    @Test
    fun theKindStaysTheSameOnceTheLoadHasStarted() {
        listOf(first, withData, empty, failed).forEach {
            assertEquals(it.nextLoadKind, it.loading().nextLoadKind, "$it")
        }
    }

    @Test
    fun everyAttemptStartsWithoutTheSlowMessage() {
        listOf(first, withData, empty, failed).forEach {
            assertEquals(false, it.copy(isSlow = true).loading().isSlow, "$it")
        }
    }

    @Test
    fun dataReplacesWhateverWasThere() {
        listOf(first, withData, empty, failed).forEach {
            assertEquals(ScreenState(Content.Loaded(listOf("b"))), it.copy(isRefreshing = true, isSlow = true).loaded(listOf("b")), "$it")
        }
    }

    @Test
    fun anEmptyListIsEmptyNotLoaded() {
        assertEquals(empty, withData.loading().loaded(emptyList()))
    }

    @Test
    fun aValueThatIsNotAListIsNeverEmpty() {
        assertEquals(ScreenState(Content.Loaded("")), ScreenState<String>().loaded(""))
    }

    @Test
    fun aFailedFirstLoadFillsTheScreen() {
        assertEquals(failed, first.copy(isSlow = true).failed(timeout))
    }

    @Test
    fun aFailedRefreshKeepsTheData() {
        listOf(toast, ErrorDisplay.Silent, ErrorDisplay.AppAction(GlobalEvent.Maintenance)).forEach {
            assertEquals(withData, withData.loading().copy(isSlow = true).failed(it), "$it")
            assertEquals(empty, empty.loading().copy(isSlow = true).failed(it), "$it")
        }
    }

    @Test
    fun evenAFullScreenErrorNeverReplacesData() {
        assertEquals(withData, withData.loading().failed(timeout))
    }

    @Test
    fun aFirstLoadThatEndsInAnAppActionDoesNotLoadForever() {
        listOf(ErrorDisplay.AppAction(GlobalEvent.Maintenance), ErrorDisplay.Silent, toast).forEach {
            assertEquals(ScreenState(Content.Failed(genericRetry)), first.failed(it), "$it")
        }
    }
}
