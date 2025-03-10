/*
 * Copyright 2022 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.client.scala

import com.google.common.collect.Iterables
import com.linecorp.armeria.client.RequestPreparationSetters
import com.linecorp.armeria.common.{AggregatedHttpRequest, Cookie, ResponseEntity}
import com.linecorp.armeria.scala.implicits._
import com.linecorp.armeria.server.annotation._
import com.linecorp.armeria.server.{ServerBuilder, ServerSuite}
import munit.FunSuite
import org.reflections.ReflectionUtils
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class RestClientSuite extends FunSuite with ServerSuite {
  override protected def configureServer: ServerBuilder => Unit = { sb =>
    sb.annotatedService(
      new {
        @Get
        @Post
        @Put
        @Delete
        @Patch
        @ProducesJson
        @Path("/rest/{id}")
        def restApi(@Param id: String, content: String): RestResponse = RestResponse(id, content)
      },
      Array.emptyObjectArray: _*
    )

    sb.annotatedService(
      new {
        @Get
        @Post
        @Put
        @Delete
        @Patch
        @ProducesJson
        @Path("/rest/complex/{id}")
        def restApi(
            @Param id: String,
            @Param query: String,
            @Header("x-header") header: String,
            agg: AggregatedHttpRequest): ComplexResponse = {
          ComplexResponse(
            id = id,
            method = agg.method().toString,
            query = query,
            header = header,
            trailer = agg.trailers().get("x-trailer"),
            cookie = Iterables.getFirst(agg.headers.cookies, null).value(),
            content = agg.contentUtf8()
          )
        }
      },
      Array.emptyObjectArray: _*
    )
  }

  test("should create ScalaRestClient with implicit class") {
    val restClient = server.webClient().asScalaRestClient()
    val future: Future[ResponseEntity[RestResponse]] =
      restClient
        .get("/rest/{id}")
        .pathParam("id", 1)
        .execute[RestResponse]()
    val content = Await.result(future, Duration.Inf).content()
    assertEquals(content.id, "1")
  }

  test("should create ScalaRestClient with factory method") {
    val restClient = ScalaRestClient(server.webClient())
    val future: Future[ResponseEntity[RestResponse]] =
      restClient
        .get("/rest/{id}")
        .pathParam("id", 1)
        .execute[RestResponse]()
    val content = Await.result(future, Duration.Inf).content()
    assertEquals(content.id, "1")
  }

  test("complex input") {
    val restClient = server.webClient().asScalaRestClient()
    val future =
      restClient
        .post("/rest/complex/{id}")
        .content("content")
        .headers(Map("x-header" -> "header-value"))
        .trailers(Map("x-trailer" -> "trailer-value"))
        .cookies(List(Cookie.ofSecure("cookie", "cookie-value")))
        .pathParams(Map("id" -> "1"))
        .queryParams(Map("query" -> "query-value"))
        .execute[ComplexResponse]()

    val content = Await.result(future, Duration.Inf).content()
    assertEquals(content.id, "1")
    assertEquals(content.method, "POST")
    assertEquals(content.query, "query-value")
    assertEquals(content.header, "header-value")
    assertEquals(content.trailer, "trailer-value")
    assertEquals(content.cookie, "cookie-value")
    assertEquals(content.content, "content")
  }

  test("ScalaRestClientPreparation should return self type") {
    ReflectionUtils
      .getMethods(classOf[RequestPreparationSetters])
      .forEach(method => {
        if ("execute" != method.getName) {
          val overridden = classOf[ScalaRestClientPreparation]
            .getMethod(method.getName, method.getParameterTypes: _*)
          assert(overridden.getReturnType == classOf[ScalaRestClientPreparation])
        }
      })
  }
}

case class RestResponse(id: String, content: String)

case class ComplexResponse(
    id: String,
    method: String,
    query: String,
    header: String,
    trailer: String,
    cookie: String,
    content: String)
