package controllers

import helpers.{AppSpec, DatabaseSupport, resourceAsJson}
import models.{DoiMetadata, JsonApiData}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api
import play.api.libs.json.{JsDefined, JsObject, JsString, Json}
import play.api.test.Helpers._
import play.api.test._
import services.{ArkService, DoiExistsException, DoiService, PidExistsException, PidService}

import scala.concurrent.Future


class ArkControllerSpec extends AppSpec with DatabaseSupport with MockitoSugar {

  private def controller = inject[ArkController]
  private val (prefix, suffix) = ("12345", "12345678")
  private val basicAuthString = "Basic " + java.util.Base64.getEncoder
    .encodeToString("system:changeme".getBytes)


  "ArkController GET" should {

    "list ARKs" in {
      val request = FakeRequest(GET, routes.ArkController.index().url)
      val result = controller.index().apply(request)

      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("12345/12345678") // FIXME: better test
    }

    "fetch an ARK redirect" in {
      val request = FakeRequest(GET, routes.ArkController.get(prefix, suffix).url)
      val result = controller.get(prefix, suffix).apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(s"https://example.com/pid-test-4")
    }

    "fetch ARK target info" in {
      val request = FakeRequest(GET, routes.ArkController.get(prefix, suffix).url)
        .withHeaders("Accept" -> "application/json")
      val result = controller.get(prefix, suffix).apply(request)

      status(result) mustBe OK
      contentType(result) mustBe Some("application/vnd.api+json")
      contentAsString(result) must include (s""""id":"$prefix/$suffix"""")
    }

    "fetch an ARK containing multiple path sections" in {
      val request = FakeRequest(GET, routes.ArkController.get(prefix, "1234/1234/1234/1234").url)
      val result = controller.get(prefix, "1234/1234/1234/1234").apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(s"https://example.com/pid-test-6")
    }

    "handle 404s with JSON" in {
      val request = FakeRequest(GET, routes.ArkController.get("NOT", "FOUND").url)
        .withHeaders("Accept" -> "application/vnd.api+json")
      val result = controller.get("NOT", "FOUND").apply(request)

      status(result) mustBe NOT_FOUND
      contentType(result) mustBe Some("application/vnd.api+json")
      // NB: this is the error message we get when we don't have the ARK registered
      // in our PID service.
      contentAsString(result) must include ("The identifier you provided does not exist, is unpublished, or is not valid")
    }

    "handle 404s with HTML" in {
      val request = FakeRequest(GET, routes.ArkController.get("NOT", "FOUND").url)
        .withHeaders("Accept" -> "text/html")
      val result = controller.get("NOT", "FOUND").apply(request)

      status(result) mustBe NOT_FOUND
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include ("ARK not found")
    }

    "register an ARK" in {
      // Here we mock the ArkService to return a consistent ARK suffix.
      val mockArkService = mock[ArkService]
      when(mockArkService.generateSuffix()).thenReturn("abcd-efgh")

      val payload = Json.obj(
        "data" -> Json.obj("attributes" -> JsObject.empty),
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )

      // We have to create a new application here and override bind a
      // mock DoiService that throws a DoiExistsException
      val mockedApp = newAppBuilder(Seq(
        api.inject.bind[ArkService].toInstance(mockArkService),
      )).build()

      val controller = mockedApp.injector.instanceOf[ArkController]
      val request = FakeRequest(POST, routes.ArkController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe CREATED
      contentType(result) mustBe Some("application/vnd.api+json")
      val out = contentAsJson(result)
      out \ "data" \ "id" mustBe JsDefined(JsString("10.12345/abcd-efgh"))
      out \ "meta" \ "target" mustBe JsDefined(JsString("https://example.com/resource"))
    }

    "handle (hopefully rare) PID service collisions gracefully" in {
      // When the PID service throws a PidExistsException, we should
      // return a 409 Conflict with a specific error message.
      val payload = Json.obj(
        "data" -> Json.obj("attributes" -> JsObject.empty),
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )

      // Here we mock the DoiService to return a consistent ARK suffix.
      // This suffix is already present in the fixtures.
      val mockArkService = mock[ArkService]
      when(mockArkService.generateSuffix()).thenReturn("12345678")

      // And the PID service to throw an error...
      val mockPidService = mock[PidService]
      when(mockPidService.create(any(), any(), any(), any())).thenReturn(
        Future.failed(PidExistsException("ARK already exists"))
      )

      // We have to create a new application here and override bind a
      // mock DoiService that throws a DoiExistsException
      val mockedApp = newAppBuilder(Seq(
        api.inject.bind[ArkService].toInstance(mockArkService),
        api.inject.bind[PidService].toInstance(mockPidService)
      )).build()

      val controller = mockedApp.injector.instanceOf[ArkController]
      val request = FakeRequest(POST, routes.ArkController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe UNPROCESSABLE_ENTITY
      contentType(result) mustBe Some("application/vnd.api+json")
      contentAsString(result) must include ("This ARK has already been taken")
    }

    "update a ARK" in {
      val payload = resourceAsJson("example.json").as[JsObject] ++ Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(PUT, routes.ArkController.update(prefix, suffix).url)
        .withHeaders(
          "Accept" -> "application/vnd.api+json",
          "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.update(prefix, suffix), request)

      status(result) mustBe OK
      contentType(result) mustBe Some("application/vnd.api+json")
      val out = contentAsJson(result)
      out \ "meta" \ "target" mustBe JsDefined(JsString("https://example.com/resource"))
    }

    "delete ARKs" in {
      val request = FakeRequest(DELETE, routes.ArkController.delete(prefix, suffix).url)
        .withHeaders("Authorization" -> basicAuthString)
      val result = call(controller.delete(prefix, suffix), request)

      status(result) mustBe NO_CONTENT
    }

    "fail to register a ARK with invalid payload" in {
      val payload = Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(POST, routes.ArkController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("application/vnd.api+json")
      val out = contentAsJson(result)
      (out \ "errors" \ 0 \ "title").asOpt[String] must be(defined)
    }

    "fail to register a ARK with invalid authorization" in {
      val payload = resourceAsJson("example.json").as[JsObject] ++ Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(POST, routes.ArkController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> "Basic invalid")
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/vnd.api+json")
      contentAsString(result) must include ("The token is missing, invalid or expired")
    }

    "fail with invalid authorization before invalid payload" in {
      val payload = Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(POST, routes.ArkController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> "Basic invalid")
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe UNAUTHORIZED
      contentType(result) mustBe Some("application/vnd.api+json")
      contentAsString(result) must include ("The token is missing, invalid or expired")
    }

    "tombstone a ARK" in {
      val request = FakeRequest(POST, routes.ArkController.tombstone(prefix, suffix).url)
        .withHeaders("Authorization" -> basicAuthString)
        .withBody(JsonApiData(Json.obj("reason" -> "Test reason")))
      val result = call(controller.tombstone(prefix, suffix), request)

      status(result) mustBe NO_CONTENT

      // Check if the ARK is tombstoned
      val doiRequest = FakeRequest(GET, routes.ArkController.get(prefix, suffix).url)
        .withHeaders("Accept" -> "application/vnd.api+json")
      val doiResult = call(controller.get(prefix, suffix), doiRequest)
      status(doiResult) mustBe GONE

    }

    "untombstone a ARK" in {
      val altSuffix = "56781234"
      val request = FakeRequest(DELETE, routes.ArkController.deleteTombstone(prefix, altSuffix).url)
        .withHeaders("Authorization" -> basicAuthString)
      val result = call(controller.deleteTombstone(prefix, altSuffix), request)
      status(result) mustBe NO_CONTENT
    }

    "tombstone ARKs with multiple path sections" in {
      val request = FakeRequest(POST, routes.ArkController.tombstone(prefix, "1234/1234/1234/1234").url)
        .withHeaders("Authorization" -> basicAuthString)
        .withBody(JsonApiData(Json.obj("reason" -> "Test reason")))
      val result = call(controller.tombstone(prefix, "1234/1234/1234/1234"), request)

      status(result) mustBe NO_CONTENT
    }
   }
}
