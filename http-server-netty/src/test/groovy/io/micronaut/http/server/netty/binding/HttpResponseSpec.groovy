/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.runtime.Micronaut
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HttpResponseSpec extends AbstractMicronautSpec {
    @Shared
            defaultHeaders = [:]

    @Unroll
    void "test custom HTTP response for java action #action"() {

        when:
        HttpResponse<?> response = Flux.from(rxClient.exchange("/java/response/$action", String))
                .onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        def actualHeaders = [:]
        for (name in response.headers.names()) {
            actualHeaders.put(name.toLowerCase(), response.header(name))
        }
        def responseBody = response.body.orElse(null)


        then:
        response.code() == status.code
        body == null || responseBody == body
        actualHeaders == headers


        where:
        action                | status                        | body                       | headers
        "ok"                  | HttpStatus.OK                 | null                       | [connection: 'close']
        "ok-with-body"        | HttpStatus.OK                 | "some text"                | ['content-length': '9', 'content-type': 'text/plain'] + [connection: 'close']
        "error-with-body"     | HttpStatus.INTERNAL_SERVER_ERROR | "some text"             | ['content-length': '9', 'content-type': 'text/plain'] + [connection: 'close']
        "ok-with-body-object" | HttpStatus.OK                 | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close']
        "status"              | HttpStatus.MOVED_PERMANENTLY  | null                       | [connection: 'close']
        "created-body"        | HttpStatus.CREATED            | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close']
        "created-uri"         | HttpStatus.CREATED            | null                       | [connection: 'close', 'location': 'http://test.com']
        "created-body-uri"    | HttpStatus.CREATED            | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close', 'location': 'http://test.com']
        "accepted"            | HttpStatus.ACCEPTED           | null                       | [connection: 'close']
        "accepted-uri"        | HttpStatus.ACCEPTED           | null                       | [connection: 'close', 'location': 'http://example.com']
        "disallow"            | HttpStatus.METHOD_NOT_ALLOWED | null                       | [connection: "close", 'allow': 'DELETE']
        "optional-response/false" | HttpStatus.OK             | null                       | [connection: 'close']
        "optional-response/true"  | HttpStatus.NOT_FOUND      | null                       | ['content-type': 'application/json', 'content-length': '162', connection: 'close']

    }

    @Unroll
    void "test custom HTTP response for action #action"() {
        when:
        HttpResponse<?> response = Flux.from(rxClient.exchange("/java/response/$action", String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                }).blockFirst()

        def actualHeaders = [:]
        for (name in response.headers.names()) {
            actualHeaders.put(name.toLowerCase(), response.header(name))
        }
        def responseBody = response.body.orElse(null)

        def defaultHeaders = [connection: 'close']

        then:
        response.code() == status.code
        body == null || responseBody == body
        actualHeaders == headers

        where:
        action                | status                       | body                       | headers
        "ok"                  | HttpStatus.OK                | null                       | [connection: 'close']
        "ok-with-body"        | HttpStatus.OK                | "some text"                | ['content-length': '9', 'content-type': 'text/plain'] + [connection: 'close']
        "error-with-body"     | HttpStatus.INTERNAL_SERVER_ERROR | "some text"            | ['content-length': '9', 'content-type': 'text/plain'] + [connection: 'close']
        "ok-with-body-object" | HttpStatus.OK                | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close']
        "status"              | HttpStatus.MOVED_PERMANENTLY | null                       | [connection: 'close']
        "created-body"        | HttpStatus.CREATED           | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close']
        "created-uri"         | HttpStatus.CREATED           | null                       | [connection: 'close', 'location': 'http://test.com']
        "accepted"            | HttpStatus.ACCEPTED          | null                       | [connection: 'close']
        "accepted-uri"        | HttpStatus.ACCEPTED          | null                       | [connection: 'close', 'location': 'http://example.com']
    }

    void "test content encoding"() {
        when:
        HttpResponse<String> response = Flux.from(rxClient.exchange(HttpRequest.GET("/java/response/ok-with-body").header("Accept-Encoding", "gzip"), String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                }).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "some text" //decoded by the client
        response.header("Content-Length") == "9" // changed by the decoder
        response.header("Content-Encoding") == null // removed by the decoder
    }

    void "test custom headers"() {
        when:
        HttpResponse<?> response = Flux.from(rxClient.exchange(HttpRequest.GET("/java/response/custom-headers")))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                }).blockFirst()
        HttpHeaders headers = response.headers

        then: // The content length header was replaced, not appended
        !headers.names().contains("content-type")
        !headers.names().contains("Content-Length")
        headers.contains("content-length")
        response.header("Content-Type") == "text/plain"
        response.header("Content-Length") == "3"
    }

    void "test server header"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['micronaut.server.server-header': 'Foo!', (SPEC_NAME_PROPERTY):getClass().simpleName])
        def ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/test-header'))

        then:
        resp.header("Server") == "Foo!"

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test default server header"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [(SPEC_NAME_PROPERTY):getClass().simpleName])
        def ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/test-header'))

        then:
        !resp.header("Server")

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test default date header"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [(SPEC_NAME_PROPERTY):getClass().simpleName])
        ApplicationContext ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/test-header'))

        then:
        resp.header("Date")

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test date header turned off"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['micronaut.server.date-header': false, (SPEC_NAME_PROPERTY):getClass().simpleName])
        ApplicationContext ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/test-header'))

        then:
        !resp.header("Date")

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test keep alive connection header is not set by default for > 499 response"() {
        when:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['micronaut.server.date-header': false, (SPEC_NAME_PROPERTY):getClass().simpleName])
        ApplicationContext ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        Flux.from(client.exchange(
          HttpRequest.GET('/test-header/fail')
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.header(HttpHeaders.CONNECTION) == 'close'

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test connection header is defaulted to keep-alive when configured to true for > 499 response"() {
        when:
        DefaultHttpClientConfiguration config = new DefaultHttpClientConfiguration()

        // The client will explicitly request "Connection: close" unless using a connection pool, so set it up
        config.connectionPoolConfiguration.enabled = true

        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
          (SPEC_NAME_PROPERTY):getClass().simpleName,
          'micronaut.server.netty.keepAliveOnServerError':true
        ])
        def ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, embeddedServer.getURL(), config)

        Flux.from(client.exchange(
          HttpRequest.GET('/test-header/fail')
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.header(HttpHeaders.CONNECTION) == 'keep-alive'

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    @Controller('/test-header')
    @Requires(property = 'spec.name', value = 'HttpResponseSpec')
    static class TestController {
        @Get
        HttpStatus index() {
            HttpStatus.OK
        }

        @Get("/fail")
        HttpResponse fail() {
            HttpResponse.serverError("server error")
        }
    }

    static class Foo {
        String name
        Integer age
    }

    @Override
    Map<String, Object> getConfiguration() {
        super.getConfiguration() << ['micronaut.server.date-header': false]
    }
}
