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
package org.particleframework.context.router;

import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.context.DefaultApplicationContext;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.MediaType;
import org.particleframework.web.router.*;

import javax.inject.Inject;

import java.util.List;

import static org.particleframework.http.MediaType.*;
import static org.junit.Assert.*;
/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class RouteBuilderTests {

    @Test
    public void testRouterBuilder() {
        ApplicationContext beanContext = new DefaultApplicationContext("test").start();
        beanContext.registerSingleton(new BookController())
                    .registerSingleton(new AuthorController());
        MyRouteBuilder routeBuilder = new MyRouteBuilder(beanContext);
        routeBuilder.someRoutes(new BookController(), new AuthorController());
        List<UriRoute> builtRoutes = routeBuilder.getUriRoutes();
        Router router = new DefaultRouter(routeBuilder);

        // test invoking routes
        assertTrue(router.GET("/books/1").isPresent());

        assertEquals("Hello World", router.GET("/message/World").get().invoke());
        assertEquals("Book 1", router.GET("/books/1").get().invoke());
        assertEquals("not found", router.route(HttpStatus.NOT_FOUND).get().invoke());
        assertEquals("class not found: error", router.route(new ClassNotFoundException("error")).get().invoke());

        // test route state

        assertTrue(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/books/1/authors").isPresent() && route.getHttpMethod() == HttpMethod.GET)
        );
        assertTrue(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/books").isPresent() && route.getHttpMethod() == HttpMethod.POST)
        );
        assertTrue(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/book").isPresent() && route.getHttpMethod() == HttpMethod.POST)
        );
        assertFalse(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/boo").isPresent() && route.getHttpMethod() == HttpMethod.POST)
        );
        assertTrue(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/book/1").isPresent() && route.getHttpMethod() == HttpMethod.GET)
        );
    }

    static class MyRouteBuilder extends DefaultRouteBuilder {
        public MyRouteBuilder(ApplicationContext beanContext) {
            super(beanContext);
        }

        @Inject
        void someRoutes(BookController controller, AuthorController authorController) {
            GET("/conditional{/message}", controller, "hello", String.class)
                    .where((request)-> request.getContentType().equals(MediaType.APPLICATION_JSON_TYPE));

            GET("/message{/message}", controller, "hello", String.class).accept(APPLICATION_JSON_TYPE);
            GET("/books{/id}", controller, "show").nest(() ->
                    GET("/authors", controller)
            );
            GET(controller);
            POST(controller);
            PUT(controller, ID);
            GET(controller, ID);
            GET(BookController.class, ID);
//            GET(Book.class, ID);
            GET(controller, ID).nest(() ->
                    GET(authorController)
            );

            GET("/books", controller);
            POST("/books", controller, "save").accept(APPLICATION_JSON_TYPE);

            // handle errors TODO
            error(ClassNotFoundException.class, controller);
            error(ReflectiveOperationException.class, controller);
            // handle status codes
            status(HttpStatus.NOT_FOUND, controller, "notFound");

            // REST resources
            resources(controller);
//            resources(Book.class);
//            single(Book.class).nest(()->
//                resources(Author.class)
//            );
        }
    }

    static class BookController {
        String hello(String message) {
            return "Hello " + message;
        }

        String show(Long id) { return "Book " + id; }
        String index() { return "dummy"; }
        String save() { return "dummy"; }
        String delete() { return "dummy"; }
        String update() { return "dummy"; }

        String notFound() { return "not found";}

        String classNotFound(ClassNotFoundException e) {
            return "class not found: " + e.getMessage();
        }

        String reflectiveOperation(ReflectiveOperationException e) {
            return "reflect exception";
        }
    }

    static class AuthorController {
        String hello(String message) {
            return "Hello " + message;
        }

        String show() { return "dummy"; }
        String index() { return "dummy"; }
        String save() { return "dummy"; }
        String delete() { return "dummy"; }
        String update() { return "dummy"; }

    }

    static class Author {}
    static class Book {}
}