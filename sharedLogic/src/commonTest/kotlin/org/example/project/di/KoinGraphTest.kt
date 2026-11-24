package org.example.project.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.categories.domain.model.Category
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.connectivity.FakeConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.ErrorPresenter
import org.example.project.core.error.RequestKind
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEvents
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Builds the real shared modules in an isolated container (not the global startKoin one),
 * swaps only the engine for a MockEngine, and resolves the use case end to end.
 * A missing binding or a forgotten qualifier fails here instead of crashing the app.
 */
class KoinGraphTest {

    private val config = NetworkConfig(AppEnvironment.Dev, AppInfo(platform = "android", appVersion = "1.0", languageTag = "en"))

    /**
     * Overrides platformModule's real engine and connectivity monitor (the Android one needs a real
     * Context). Declared last, so it wins.
     */
    private val fakeEngineModule = module {
        single<ConnectivityMonitor> { FakeConnectivityMonitor(NetworkStatus.Online) }
        single<HttpClientEngine> {
            MockEngine {
                respond(
                    content = """{"code":200,"message":"","data":[{"id":"1","name":"Burger","isAll":false},{"id":"2","name":"All","isAll":true}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
    }

    @Test
    fun technicalDetailsFollowTheEnvironment() {
        AppEnvironment.entries.forEach { environment ->
            val koinApp = koinApplication { modules(sharedModules(config.copy(environment = environment))) }
            try {
                val display = koinApp.koin.get<ErrorPresenter>().present(DataError.Unknown(), RequestKind.InitialLoad)
                val details = assertIs<ErrorDisplay.FullScreen>(display).message.technicalDetails
                assertEquals(environment.showTechnicalDetails, details != null, "$environment")
            } finally {
                koinApp.close()
            }
        }
    }

    @Test
    fun theAppRootSeesEventsRaisedByAnyRequest() = runTest {
        val blockedVersion = module {
            single<HttpClientEngine> { MockEngine { respond("", HttpStatusCode.UpgradeRequired) } }
        }
        val koinApp = koinApplication {
            allowOverride(true)
            modules(sharedModules(config) + fakeEngineModule + blockedVersion)
        }
        try {
            koinApp.koin.get<GetCategoriesUseCase>().invoke()

            assertEquals(setOf<GlobalEvent>(GlobalEvent.UpgradeRequired), koinApp.koin.get<GlobalEvents>().pending.first())
        } finally {
            koinApp.close()
        }
    }

    @Test
    fun sharedGraphResolvesUseCaseEndToEnd() = runTest {
        val koinApp = koinApplication {
            allowOverride(true)
            modules(sharedModules(config) + fakeEngineModule)
        }
        try {
            val getCategories: GetCategoriesUseCase = koinApp.koin.get()

            val result = getCategories() as ApiResult.Success
            assertEquals(listOf("All", "Burger"), result.data.map(Category::name))   // the "All first" rule ran too
        } finally {
            koinApp.close()
        }
    }
}
