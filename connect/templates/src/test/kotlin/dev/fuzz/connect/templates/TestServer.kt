package dev.fuzz.connect.templates

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Мок-сервер: записывает запросы и отвечает по обработчику. */
class TestServer(
    private val handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()

    val client =
        HttpClient(
            MockEngine { request ->
                requests += request
                handler(request)
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
}

fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
