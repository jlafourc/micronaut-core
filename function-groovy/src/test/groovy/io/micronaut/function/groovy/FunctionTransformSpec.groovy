/*
 * Copyright 2017 original authors
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
package io.micronaut.function.groovy

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.codehaus.groovy.control.CompilerConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class FunctionTransformSpec extends Specification{
    def cleanup() {
        TestFunctionExitHandler.lastError = null
    }

    void 'test parse function'() {
        given:
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.optimizationOptions['micronaut.function.compile'] = true
        GroovyClassLoader gcl = new GroovyClassLoader(FunctionTransformSpec.classLoader, configuration)

        Class functionClass = gcl.parseClass('''
int round(float value) {
    Math.round(value) 
}
''')

        expect:
        functionClass.main(['-d','1.6f'] as String[])
        TestFunctionExitHandler.lastError == null
    }

    void 'test parse JSON marshalling function'() {
        given:
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.optimizationOptions['micronaut.function.compile'] = true
        GroovyClassLoader gcl = new GroovyClassLoader(FunctionTransformSpec.classLoader, configuration)
        gcl.parseClass('''
package test

class Test { String name }
''')
        Class functionClass = gcl.parseClass('''
import test.*

Test test(Test test) {
    test 
}
''')

        expect:
        functionClass.main(['-d','{"name":"Fred"}'] as String[])
        TestFunctionExitHandler.lastError == null
    }

    void "run function main method"() {

        expect:
        RoundFunction.main(['-d','1.6f'] as String[])
        TestFunctionExitHandler.lastError == null

    }
    void "run function"() {
        expect:
        new RoundFunction().round(1.6f) == 4
        new SumFunction().sum(new Sum(a: 10,b: 20)) == 30
        new MaxFunction().max() == Integer.MAX_VALUE.toLong()
    }

    void "run consumer"() {
        given:
        NotifyFunction function = new NotifyFunction()

        def message = new Message(title: "Hello", body: "World")
        when:
        function.send(message)

        then:
        function.messageService.messages.contains(message)
    }

    void "run bi-consumer"() {
        given:
        NotifyWithArgsFunction function = new NotifyWithArgsFunction()

        def message = new Message(title: "Hello", body: "World")
        when:
        function.send("Hello", "World")

        then:
        function.messageService.messages.contains(message)
    }

    void "test run JSON bi-consumer as REST service"() {

        given:
        def applicationContext = ApplicationContext.build()
                .environment({ env ->
            env.addPropertySource(MapPropertySource.of(
                    'test',
                    ['math.multiplier': '2']
            ))

        })
        EmbeddedServer server = applicationContext.start().getBean(EmbeddedServer).start()
        def message = new Message(title: "Hello", body: "World")
        String url = "http://localhost:$server.port"
        OkHttpClient client = new OkHttpClient()
        def data = '{"title":"Hello", "body":"World"}'

        def request = new Request.Builder()
                .url("$url/notify-with-args")
                .post(RequestBody.create( MediaType.parse(io.micronaut.http.MediaType.APPLICATION_JSON), data))

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        applicationContext.getBean(MessageService).messages.contains(message)

        cleanup:
        if(server != null)
            server.stop()
    }

    void "test run JSON function as REST service"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['math.multiplier':'2'], 'test')
        String url = "http://localhost:$server.port"
        OkHttpClient client = new OkHttpClient()
        def data = '{"a":10, "b":5}'
        def request = new Request.Builder()
                .url("$url/sum")
                .post(RequestBody.create( MediaType.parse(io.micronaut.http.MediaType.APPLICATION_JSON), data))

        when:
        def conditions = new PollingConditions()

        then:
        conditions.eventually {
            server.isRunning()
        }

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == '15'

        cleanup:
        server?.stop()
    }

    void "test run function as REST service"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['math.multiplier':'2'], 'test')
        String url = "http://localhost:$server.port"
        OkHttpClient client = new OkHttpClient()
        def data = '1.6'
        def request = new Request.Builder()
                .url("$url/round")
                .post(RequestBody.create( MediaType.parse("text/plain"), data))

        when:
        def conditions = new PollingConditions()

        then:
        conditions.eventually {
            server.isRunning()
        }

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == '4'

        cleanup:
        server?.stop()
    }

    void "test run supplier as REST service"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['math.multiplier':'2'], 'test')
        String url = "http://localhost:$server.port"
        OkHttpClient client = new OkHttpClient()
        def request = new Request.Builder()
                .url("$url/max")

        when:
        def conditions = new PollingConditions()

        then:
        conditions.eventually {
            server.isRunning()
        }

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == String.valueOf(Integer.MAX_VALUE)

        cleanup:
        server?.stop()
    }
}
