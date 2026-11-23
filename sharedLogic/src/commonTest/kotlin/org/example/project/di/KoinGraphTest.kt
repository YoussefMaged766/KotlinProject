package org.example.project.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.example.project.categories.domain.model.Category
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.network.ApiConfig
import org.example.project.network.ApiResult
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Builds the real shared modules in an isolated container (not the global startKoin one),
 * swaps only the engine for a MockEngine, and resolves the use case end to end.
 * A missing binding or a forgotten qualifier fails here instead of crashing the app.
 */
class KoinGraphTest {

    private val config = ApiConfig(ApiConfig.DEV_BASE_URL, "android", "1.0", "en")

    /** Overrides platformModule's real engine. Declared last, so it wins. */
    private val fakeEngineModule = module {
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
