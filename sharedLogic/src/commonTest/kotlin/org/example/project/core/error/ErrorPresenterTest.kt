package org.example.project.core.error

import org.example.project.core.config.AppEnvironment
import org.example.project.core.error.ErrorDisplay.AppAction
import org.example.project.core.error.ErrorDisplay.Dialog
import org.example.project.core.error.ErrorDisplay.FullScreen
import org.example.project.core.error.ErrorDisplay.Inline
import org.example.project.core.error.ErrorDisplay.Silent
import org.example.project.core.error.ErrorDisplay.Toast
import org.example.project.core.error.RequestKind.Background
import org.example.project.core.error.RequestKind.InitialLoad
import org.example.project.core.error.RequestKind.Refresh
import org.example.project.core.error.RequestKind.UserAction
import org.example.project.core.events.GlobalEvent
import org.example.project.core.result.DataError
import org.example.project.core.result.ErrorDetails
import org.example.project.core.result.TimeoutKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** R4: every row of the decision table, and R9: the same words in every environment. */
class ErrorPresenterTest {

    private val presenter = ErrorPresenter(showTechnicalDetails = false)

    private val get = ErrorDetails(method = "GET", path = "/v1/things")
    private val post = ErrorDetails(method = "POST", path = "/v1/orders")

    private fun message(key: MessageKey) = ErrorMessage(key)
    private fun fullScreen(key: MessageKey, canRetry: Boolean) = FullScreen(message(key), canRetry)
    private fun toast(key: MessageKey, canRetry: Boolean = true) = Toast(message(key), canRetry)
    private fun dialog(key: MessageKey, canRetry: Boolean) = Dialog(message(key), canRetry)

    private fun assertRow(
        error: DataError,
        initialLoad: ErrorDisplay,
        refresh: ErrorDisplay,
        userAction: ErrorDisplay,
        background: ErrorDisplay = Silent,
    ) {
        assertEquals(initialLoad, presenter.present(error, InitialLoad), "$error InitialLoad")
        assertEquals(refresh, presenter.present(error, Refresh), "$error Refresh")
        assertEquals(userAction, presenter.present(error, UserAction), "$error UserAction")
        assertEquals(background, presenter.present(error, Background), "$error Background")
    }

    // ---- The decision table: nothing was changed on the server --------------------------------

    @Test
    fun noInternet() = assertRow(
        DataError.NoInternet(details = get),
        fullScreen(MessageKey.NoInternet, canRetry = true),
        Silent,                                              // the banner already says it
        toast(MessageKey.NoInternet),
    )

    @Test
    fun connectionLost() = assertRow(
        DataError.ConnectionLost(get),
        fullScreen(MessageKey.ConnectionLost, canRetry = true),
        toast(MessageKey.ConnectionLost),
        dialog(MessageKey.ConnectionLost, canRetry = true),
    )

    @Test
    fun timeout() = TimeoutKind.entries.forEach { kind ->
        assertRow(
            DataError.Timeout(kind, get),
            fullScreen(MessageKey.Timeout, canRetry = true),
            toast(MessageKey.Timeout),
            dialog(MessageKey.Timeout, canRetry = true),
        )
    }

    @Test
    fun validationGoesUnderTheFields() = assertRow(
        DataError.Validation(mapOf("email" to "is not valid"), post),
        fullScreen(MessageKey.InvalidInput, canRetry = false),
        toast(MessageKey.InvalidInput, canRetry = false),
        Inline(mapOf("email" to "is not valid")),
    )

    @Test
    fun validationWithoutFieldsIsStillShown() {
        assertEquals(
            dialog(MessageKey.InvalidInput, canRetry = false),
            presenter.present(DataError.Validation(emptyMap(), post), UserAction),
        )
    }

    @Test
    fun appWideErrorsAreLeftToTheAppRootWhateverTheUserWasDoing() {
        mapOf(
            DataError.Unauthorized(hadSession = true, details = post) to GlobalEvent.SessionExpired,
            DataError.UpgradeRequired(get) to GlobalEvent.UpgradeRequired,
            DataError.Maintenance(get) to GlobalEvent.Maintenance,
        ).forEach { (error, event) ->
            assertRow(error, AppAction(event), AppAction(event), AppAction(event), AppAction(event))
        }
    }

    @Test
    fun notAllowed() = listOf(DataError.Forbidden(get), DataError.Unauthorized(hadSession = false, details = get)).forEach {
        assertRow(
            it,
            fullScreen(MessageKey.NotAllowed, canRetry = false),
            toast(MessageKey.NotAllowed, canRetry = false),
            dialog(MessageKey.NotAllowed, canRetry = false),
        )
    }

    @Test
    fun notFound() = assertRow(
        DataError.NotFound(get),
        fullScreen(MessageKey.NotFound, canRetry = false),
        toast(MessageKey.NotFound, canRetry = false),
        dialog(MessageKey.NotFound, canRetry = false),
    )

    @Test
    fun rateLimited() = assertRow(
        DataError.RateLimited(retryAfterSeconds = 30, details = post),
        fullScreen(MessageKey.RateLimited, canRetry = true),
        toast(MessageKey.RateLimited),
        toast(MessageKey.RateLimited),
    )

    @Test
    fun businessErrorsShowTheServersOwnWords() {
        val closed = ErrorMessage(MessageKey.Generic, serverText = "Restaurant closed")
        assertRow(
            DataError.Business("RESTAURANT_CLOSED", post.copy(serverMessage = "Restaurant closed")),
            FullScreen(closed, canRetry = false),            // a dialog would leave an empty screen behind it
            Dialog(closed, canRetry = false),
            Dialog(closed, canRetry = false),
        )
    }

    @Test
    fun aBusinessErrorWithoutWordsIsGeneric() {
        assertEquals(
            dialog(MessageKey.Generic, canRetry = false),
            presenter.present(DataError.Business(code = null, details = post), UserAction),
        )
    }

    @Test
    fun serverBadResponseAndUnknown() {
        mapOf(
            DataError.Server(500, get) to MessageKey.ServerError,
            DataError.Server(502, get) to MessageKey.ServerError,
            DataError.BadResponse(get) to MessageKey.ServerError,
            DataError.Unknown(get) to MessageKey.Generic,
        ).forEach { (error, key) ->
            assertRow(error, fullScreen(key, canRetry = true), toast(key), dialog(key, canRetry = true))
        }
    }

    // ---- R5: a change that may have reached the server ----------------------------------------

    @Test
    fun aChangeThatMayHaveGoneThroughSaysSo() {
        listOf(
            DataError.NoInternet(maybeSent = true, details = post),
            DataError.ConnectionLost(post),
            DataError.Timeout(TimeoutKind.Socket, post),
            DataError.Timeout(TimeoutKind.Request, post),
            DataError.Server(502, post),
            DataError.Server(504, post),
        ).forEach {
            assertEquals(dialog(MessageKey.OutcomeUnknown, canRetry = true), presenter.present(it, UserAction), "$it")
        }
    }

    @Test
    fun anUnreadableSuccessOffersNoRetryBecauseItProbablyWorked() {
        assertEquals(
            dialog(MessageKey.OutcomeUnknown, canRetry = false),
            presenter.present(DataError.BadResponse(post), UserAction),
        )
    }

    @Test
    fun aChangeThatNeverLeftTheDeviceIsAnOrdinaryFailure() {
        assertEquals(toast(MessageKey.NoInternet), presenter.present(DataError.NoInternet(details = post), UserAction))
        assertEquals(
            dialog(MessageKey.Timeout, canRetry = true),
            presenter.present(DataError.Timeout(TimeoutKind.Connect, post), UserAction),
        )
    }

    @Test
    fun aChangeTheServerRefusedIsAnOrdinaryFailure() {
        assertEquals(dialog(MessageKey.ServerError, canRetry = true), presenter.present(DataError.Server(500, post), UserAction))
    }

    @Test
    fun aReadLostAfterSendingIsAnOrdinaryFailure() {
        assertEquals(
            toast(MessageKey.NoInternet),
            presenter.present(DataError.NoInternet(maybeSent = true, details = get), UserAction),
        )
    }

    // ---- R9: same words everywhere, details only for developers -------------------------------

    private val everyError = listOf(
        DataError.NoInternet(details = get),
        DataError.ConnectionLost(post),
        DataError.Timeout(TimeoutKind.Request, get),
        DataError.Validation(emptyMap(), post),
        DataError.Unauthorized(hadSession = false, details = post),
        DataError.Forbidden(get),
        DataError.NotFound(get),
        DataError.Business("X", post.copy(serverMessage = "Closed")),
        DataError.RateLimited(5, get),
        DataError.Server(500, get),
        DataError.BadResponse(get),
        DataError.Unknown(get),
    )

    private fun ErrorDisplay.messageOrNull(): ErrorMessage? = when (this) {
        is FullScreen -> message
        is Toast -> message
        is Dialog -> message
        is Inline, Silent, is AppAction -> null
    }

    @Test
    fun theWordsAreIdenticalInEveryEnvironment() {
        everyError.forEach { error ->
            RequestKind.entries.forEach { kind ->
                val shown = AppEnvironment.entries.map { environment ->
                    val display = ErrorPresenter(environment.showTechnicalDetails).present(error, kind)
                    display::class to display.messageOrNull()?.copy(technicalDetails = null)
                }
                assertEquals(1, shown.distinct().size, "$error $kind: $shown")
            }
        }
    }

    @Test
    fun prodNeverShowsTechnicalDetails() {
        val prod = ErrorPresenter(AppEnvironment.Prod.showTechnicalDetails)
        everyError.forEach { error ->
            RequestKind.entries.forEach { kind -> assertNull(prod.present(error, kind).messageOrNull()?.technicalDetails) }
        }
    }

    @Test
    fun serverTextThatIsNotABusinessErrorNeverReachesTheUser() {
        val text = get.copy(serverMessage = "NullPointerException at OrderService:42")
        listOf(
            DataError.Validation(emptyMap(), text),
            DataError.Forbidden(text),
            DataError.NotFound(text),
            DataError.RateLimited(5, text),
            DataError.Server(500, text),
        ).forEach { assertNull(presenter.present(it, InitialLoad).messageOrNull()?.serverText, "$it") }
    }

    private fun technicalDetailsOf(error: DataError): String? =
        assertIs<FullScreen>(ErrorPresenter(showTechnicalDetails = true).present(error, InitialLoad)).message.technicalDetails

    @Test
    fun technicalDetailsSayWhatHappenedAndWhere() {
        assertEquals(
            "TIMEOUT·REQUEST · GET /v1/things · SocketTimeout: slow",
            technicalDetailsOf(DataError.Timeout(TimeoutKind.Request, get.copy(cause = "SocketTimeout: slow"))),
        )
        assertEquals(
            "HTTP 500 · GET /v1/things · req 8f2c · Oops",
            technicalDetailsOf(DataError.Server(500, get.copy(status = 500, requestId = "8f2c", serverMessage = "Oops"))),
        )
    }

    @Test
    fun technicalDetailsNameTheErrorEvenWithoutAnyDetails() {
        assertEquals("NO_INTERNET", technicalDetailsOf(DataError.NoInternet()))
        assertEquals("UNKNOWN", technicalDetailsOf(DataError.Unknown()))
    }
}
