package controllers

import helpers.{AppSpec, DatabaseSupport, resourceAsJson}
import models.JsonApiData
import play.api.libs.json.{JsDefined, JsObject, JsString, Json}
import play.api.test.Helpers._
import play.api.test._


class DoiControllerSpec extends AppSpec with DatabaseSupport {

  private def controller = inject[DoiController]
  private val (prefix, suffix) = ("10.14454", "fxws-0523")
  private val basicAuthString = "Basic " + java.util.Base64.getEncoder
    .encodeToString("system:changeme".getBytes)


  "DoiController GET" should {

    "list DOIs" in {
      val request = FakeRequest(GET, routes.DoiController.index().url)
      val result = controller.index().apply(request)

      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("Total Pages: 2") // FIXME: better test
    }

    "fetch a DOI as JSON" in {
      val request = FakeRequest(GET, routes.DoiController.get(prefix, suffix).url)
        .withHeaders("Accept" -> "application/json")
      val result = controller.get(prefix, suffix).apply(request)

      status(result) mustBe OK
      contentType(result) mustBe Some("application/vnd.api+json")
      contentAsString(result) must include (s""""prefix":"$prefix"""")
    }

    "fetch a DOI page as HTML by default" in {
      val request = FakeRequest(GET, routes.DoiController.get(prefix, suffix).url)
      val result = controller.get(prefix, suffix).apply(request)

      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("DataCite Metadata Schema")
    }

    "handle 404s with JSON" in {
      val request = FakeRequest(GET, routes.DoiController.get("NOT", "FOUND").url)
        .withHeaders("Accept" -> "application/vnd.api+json")
      val result = controller.get("NOT", "FOUND").apply(request)

      status(result) mustBe NOT_FOUND
      contentType(result) mustBe Some("application/vnd.api+json")
      // NB: this is the error message we get when we don't have the DOI registered
      // in our PID service.
      contentAsString(result) must include ("The DOI you provided does not exist or is not valid")
    }

    "handle 404s with HTML" in {
      val request = FakeRequest(GET, routes.DoiController.get("NOT", "FOUND").url)
        .withHeaders("Accept" -> "text/html")
      val result = controller.get("NOT", "FOUND").apply(request)

      status(result) mustBe NOT_FOUND
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("DOI not found")
    }

    "register a DOI" in {
      val payload = resourceAsJson("example.json").as[JsObject] ++ Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(POST, routes.DoiController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe CREATED
      contentType(result) mustBe Some("application/vnd.api+json")
      val out = contentAsJson(result)
      out \ "meta" \ "target" mustBe JsDefined(JsString("https://example.com/resource"))
      out \ "data" \ "type" mustBe JsDefined(JsString("dois"))
      out \ "data" \ "attributes" \ "prefix" mustBe JsDefined(JsString("10.14454"))
    }

    "update a DOI" in {
      val payload = resourceAsJson("example.json").as[JsObject] ++ Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(PUT, routes.DoiController.update(prefix, suffix).url)
        .withHeaders(
          "Accept" -> "application/vnd.api+json",
          "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.update(prefix, suffix), request)

      status(result) mustBe OK
      contentType(result) mustBe Some("application/vnd.api+json")
      val out = contentAsJson(result)
      out \ "meta" \ "target" mustBe JsDefined(JsString("https://example.com/resource"))
      out \ "data" \ "type" mustBe JsDefined(JsString("dois"))
      out \ "data" \ "attributes" \ "prefix" mustBe JsDefined(JsString("10.14454"))
    }

    "delete DOIs" in {
      val request = FakeRequest(DELETE, routes.DoiController.delete(prefix, suffix).url)
        .withHeaders("Authorization" -> basicAuthString)
      val result = call(controller.delete(prefix, suffix), request)

      status(result) mustBe NO_CONTENT
    }

    "fail to register a DOI with invalid payload" in {
      val payload = Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(POST, routes.DoiController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/json")
      val out = contentAsJson(result)
      (out \ "errors" \ 0 \ "title").asOpt[String] must be(defined)
    }

    "fail to register a DOI with invalid authorization" in {
      val payload = resourceAsJson("example.json").as[JsObject] ++ Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(POST, routes.DoiController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> "Basic invalid")
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/vnd.api+json")
      contentAsString(result) must include ("The token is missing, invalid or expired")
    }

    "tombstone a DOI" in {
      val request = FakeRequest(POST, routes.DoiController.tombstone(prefix, suffix).url)
        .withHeaders("Authorization" -> basicAuthString)
        .withBody(JsonApiData(Json.obj("reason" -> "Test reason")))
      val result = call(controller.tombstone(prefix, suffix), request)

      status(result) mustBe NO_CONTENT

      // Check if the DOI is tombstoned
      val doiRequest = FakeRequest(GET, routes.DoiController.get(prefix, suffix).url)
        .withHeaders("Accept" -> "application/vnd.api+json")
      val doiResult = call(controller.get(prefix, suffix), doiRequest)
      status(doiResult) mustBe GONE

    }

    "untombstone a DOI" in {
      val altSuffix = "fxws-0524"
      val request = FakeRequest(DELETE, routes.DoiController.deleteTombstone(prefix, altSuffix).url)
        .withHeaders("Authorization" -> basicAuthString)
      val result = call(controller.deleteTombstone(prefix, altSuffix), request)
      status(result) mustBe NO_CONTENT
    }
  }
}
