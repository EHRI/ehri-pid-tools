package controllers

import helpers.{AppSpec, DatabaseSupport, resourceAsJson}
import models.{DoiMetadata, JsonApiData}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api
import play.api.i18n.{I18nSupport, Lang, Messages, MessagesApi}
import play.api.libs.json.{JsDefined, JsObject, JsString, Json}
import play.api.test.Helpers._
import play.api.test._
import services._

import scala.concurrent.Future


class DoiControllerSpec extends AppSpec with DatabaseSupport with MockitoSugar with I18nSupport {

  override implicit def messagesApi: MessagesApi = inject[MessagesApi]
  implicit def messages: Messages = messagesApi.preferred(Seq(Lang.defaultLang))
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
      contentAsString(result) must include (Messages("pagination.nextPage"))
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

    "show hidden DOIs when profile has showHidden enabled" in {
      val request = FakeRequest(GET, routes.DoiController.get("NOT", "FINDABLE").url)
      val result = controller.get("NOT", "FINDABLE").apply(request)

      status(result) mustBe NOT_FOUND
      contentType(result) mustBe Some("text/html")
    }

    "show a 404 for hidden DOIs when profile has showHidden" in {
      val request = FakeRequest(GET, routes.DoiController.get("NOT", "FINDABLE").url + "?_profile=fakeprod")
      val result = controller.get("NOT", "FINDABLE").apply(request)

      status(result) mustBe NOT_FOUND
      contentType(result) mustBe Some("text/html")
    }

    "fetch a DOI containing multiple path sections" in {
      val request = FakeRequest(GET, routes.DoiController.get(prefix, "1234/1234/1234/1234").url)
      val result = controller.get(prefix, "1234/1234/1234/1234").apply(request)

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
      contentAsString(result) must include ("The DOI you provided does not exist, is unpublished, or is not valid")
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

    "handle (hopefully rare) DOI service collisions gracefully" in {
      // We have to create a new application here and override bind a
      // mock DoiService that throws a DoiExistsException
      val mockDoiService = mock[DoiService]
      when(mockDoiService.registerDoi(any())).thenReturn(
        Future.failed(DoiExistsException("DOI already exists"))
      )
      val mockDoiServiceHandle = mock[DoiServiceHandle]
      when(mockDoiServiceHandle.forProfile(any())).thenReturn(mockDoiService)

      val mockedApp = newAppBuilder(Seq(
        api.inject.bind[DoiServiceHandle].toInstance(mockDoiServiceHandle)
      )).build()

      val controller = mockedApp.injector.instanceOf[DoiController]
      val payload = resourceAsJson("example.json").as[JsObject] ++ Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )
      val request = FakeRequest(POST, routes.DoiController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe UNPROCESSABLE_ENTITY
      contentType(result) mustBe Some("application/vnd.api+json")
      contentAsString(result) must include ("This DOI has already been taken")
    }

    "handle (hopefully rare) PID service collisions gracefully" in {
      // When the PID service throws a PidExistsException, we should
      // return a 409 Conflict with a specific error message.
      val payload = resourceAsJson("example.json").as[JsObject] ++ Json.obj(
        "meta" -> Json.obj(
          "target" -> "https://example.com/resource"
        )
      )

      // Here we mock the DoiService to return a consistent DOI suffix.
      val mockDoiService = mock[DoiService]
      when(mockDoiService.generateSuffix()).thenReturn("abcd-efgh")
      when(mockDoiService.deleteDoi(any())).thenReturn(Future.successful(true))
      when(mockDoiService.registerDoi(any())).thenReturn(
        Future.successful(payload.as[models.JsonApiData].data.as[DoiMetadata])
      )
      val mockDoiServiceHandle = mock[DoiServiceHandle]
      when(mockDoiServiceHandle.forProfile(any())).thenReturn(mockDoiService)
      // And the PID service to throw an error...
      val mockPidService = mock[PidService]
      when(mockPidService.create(any(), any(), any(), any())).thenReturn(
        Future.failed(PidExistsException("DOI already exists"))
      )

      // We have to create a new application here and override bind a
      // mock DoiService that throws a DoiExistsException
      val mockedApp = newAppBuilder(Seq(
        api.inject.bind[DoiServiceHandle].toInstance(mockDoiServiceHandle),
        api.inject.bind[PidService].toInstance(mockPidService)
      )).build()

      val controller = mockedApp.injector.instanceOf[DoiController]
      val request = FakeRequest(POST, routes.DoiController.register().url)
        .withHeaders("Accept" -> "application/vnd.api+json", "Authorization" -> basicAuthString)
        .withJsonBody(payload)
      val result = call(controller.register(), request)

      status(result) mustBe UNPROCESSABLE_ENTITY
      contentType(result) mustBe Some("application/vnd.api+json")
      contentAsString(result) must include ("This DOI has already been taken")

      // Check the DoiService was called to delete the draft DOI...
      verify(mockDoiService, times(1)).deleteDoi(s"10.12345/abcd-efgh")
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
      contentType(result) mustBe Some("application/vnd.api+json")
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

    "fail with invalid authorization before invalid payload" in {
      val payload = Json.obj(
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

    "tombstone DOIs with multiple path sections" in {
      val request = FakeRequest(POST, routes.DoiController.tombstone(prefix, "1234/1234/1234/1234").url)
        .withHeaders("Authorization" -> basicAuthString)
        .withBody(JsonApiData(Json.obj("reason" -> "Test reason")))
      val result = call(controller.tombstone(prefix, "1234/1234/1234/1234"), request)

      status(result) mustBe NO_CONTENT
    }
  }
}
