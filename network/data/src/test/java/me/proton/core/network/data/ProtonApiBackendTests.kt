/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.proton.core.network.data

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import me.proton.core.network.data.di.ApiFactory
import me.proton.core.network.data.util.MockApiClient
import me.proton.core.network.data.util.MockUserData
import me.proton.core.network.data.util.TestRetrofitApi
import me.proton.core.network.data.util.prepareResponse
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.NetworkManager
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.net.HttpURLConnection
import kotlin.test.*

// Can't use runBlockingTest with MockWebServer. See:
// https://github.com/square/retrofit/issues/3330
// https://github.com/Kotlin/kotlinx.coroutines/issues/1204
@ExperimentalCoroutinesApi
internal class ProtonApiBackendTests {

    lateinit var apiFactory: ApiFactory
    lateinit var webServer: MockWebServer
    lateinit var backend: ProtonApiBackend<TestRetrofitApi>

    private var isNetworkAvailable = true

    @MockK
    lateinit var networkManager: NetworkManager

    @BeforeTest
    fun before() {
        MockKAnnotations.init(this)
        val client = MockApiClient()
        apiFactory = ApiFactory(client)
        val user = MockUserData()

        every { networkManager.isConnectedToNetwork() } returns isNetworkAvailable

        isNetworkAvailable = true
        webServer = MockWebServer()
        backend = ProtonApiBackend(
            webServer.url("/").toString(),
            client,
            user,
            apiFactory.baseOkHttpClient,
            listOf(
                ScalarsConverterFactory.create(),
                apiFactory.jsonConverter
            ),
            TestRetrofitApi::class,
            networkManager,
            {} // TODO: test pinning
        )
    }

    @AfterTest
    fun after() {
        webServer.shutdown()
    }

    @Test
    fun `test ok call`() = runBlocking {
        webServer.prepareResponse(
            HttpURLConnection.HTTP_OK,
            """{ "Number": 5, "String": "foo" }""")

        val result = backend(ApiManager.Call(0) { test() })
        assertTrue(result is ApiResult.Success)

        val data = result.valueOrNull
        assertEquals(5, data.number)
        assertEquals("foo", data.string)
    }

    @Test
    fun `test http error`() = runBlocking {
        webServer.prepareResponse(404)

        val result = backend(ApiManager.Call(0) { test() })
        assertTrue(result is ApiResult.Error.Http)
        assertEquals(404, result.httpCode)
    }

    @Test
    fun `test too many requests`() = runBlocking {
        val response = MockResponse()
            .setResponseCode(429)
            .setHeader("Retry-After", "5")
        webServer.enqueue(response)

        val result = backend(ApiManager.Call(0) { test() })
        assertTrue(result is ApiResult.Error.TooManyRequest)
        assertEquals(429, result.httpCode)
        assertEquals(5, result.retryAfterSeconds)
    }

    @Test
    fun `test proton error`() = runBlocking {
        webServer.prepareResponse(
            401,
            """{ "Code": 10, "Error": "darn!" }""")

        val result = backend(ApiManager.Call(0) { test() })
        assertTrue(result is ApiResult.Error.Http)

        assertEquals(10, result.proton?.code)
        assertEquals(401, result.httpCode)
        assertEquals("darn!", result.proton?.error)
    }

    @Test
    fun `test Accept header override`() = runBlocking {
        webServer.prepareResponse(HttpURLConnection.HTTP_OK, "plain")

        val result = backend(ApiManager.Call(0) { testPlain() })
        assertEquals("text/plain", webServer.takeRequest().headers["Accept"])
        assertTrue(result is ApiResult.Success)

        assertEquals("plain", result.value)
    }

    @Test
    fun `test extra field ignored`() = runBlocking {
        webServer.prepareResponse(
            HttpURLConnection.HTTP_OK,
            """{ "Number": 5, "String": "foo", "Extra": "bar" }""")

        val result = backend(ApiManager.Call(0) { test() })
        assertTrue(result is ApiResult.Success)

        val data = result.valueOrNull
        assertEquals(5, data.number)
        assertEquals("foo", data.string)
    }

    @Test
    fun `test missing field`() = runBlocking {
        webServer.prepareResponse(
            HttpURLConnection.HTTP_OK,
            """{ "NumberTypo": 5, "String": "foo" }""")

        val result = backend(ApiManager.Call(0) { test() })
        assertTrue(result is ApiResult.Error.Parse)
    }

    @Test
    fun `test default val`() = runBlocking {
        webServer.prepareResponse(
            HttpURLConnection.HTTP_OK,
            """{ "Number": 5, "String": "foo" }""")

        val result = backend(ApiManager.Call(0) { test() })
        assertEquals(true, result.valueOrNull?.bool)
    }
}
