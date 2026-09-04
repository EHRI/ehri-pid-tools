package controllers

import helpers.AppSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.ws.WSClient
import play.api.test.Helpers._
import play.api.test._
import services.PidService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class PreviewControllerSpec extends AppSpec with MockitoSugar {

  // preview() only fetches URLs that are a registered PID target, so the
  // controller under test here is given a PidService that always says yes -
  // this spec is about the HTML-generation logic, not PID persistence.
  private def controller: PreviewController = {
    val pidService = mock[PidService]
    when(pidService.targetExists(any())).thenReturn(Future.successful(true))
    new PreviewController(stubControllerComponents(), inject[WSClient], inject[play.api.cache.AsyncCacheApi], inject[play.api.Configuration], pidService)
  }
  private def messagesApi: MessagesApi = inject[MessagesApi]
  private val testUrl = "https://example.com/preview-test"
  private val testUrl2 = "https://example.com/preview-test2"

  "PreviewController GET" should {

    "return an HTML snippet for a given URL" in {
      val request = FakeRequest(GET, routes.PreviewController.preview(testUrl).url)
      val result = controller.preview(testUrl).apply(request)
      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")

      val html = contentAsString(result)
      html must include ("Preview Generator Test Page | Nice Site")
      html must include ("https://example.com/preview-test-image.svg")
    }

    "deal with missing images" in {
      val request = FakeRequest(GET, routes.PreviewController.preview(testUrl2).url)
      val result = controller.preview(testUrl2).apply(request)
      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")

      val html = contentAsString(result)
      html must not include ("https://example.com/preview-test-image2.svg")
    }

    "return not found for a URL that isn't a registered PID target" in {
      val pidService = mock[PidService]
      when(pidService.targetExists(any())).thenReturn(Future.successful(false))
      val controller = new PreviewController(stubControllerComponents(), inject[WSClient], inject[play.api.cache.AsyncCacheApi], inject[play.api.Configuration], pidService)

      val request = FakeRequest(GET, routes.PreviewController.preview(testUrl).url)
      val result = controller.preview(testUrl).apply(request)
      status(result) mustBe NOT_FOUND
    }

    "displayed percent-encoded URLs as UTF-8" in {
      implicit val messages: Messages = messagesApi.preferred(Seq(play.api.i18n.Lang.defaultLang))
      val html = views.html.preview(
        "http://example.com/a/%D1%83%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D1%81%D1%8C%D0%BA%D0%B0",
        "Ukrainian URL",
        "Test",
        None
      )
      html.body must include("http://example.com/a/українська")
    }
  }
}
