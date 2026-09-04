package services

import helpers.AppSpec
import play.api.http.HttpErrorHandler
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Future

class AppErrorHandlerSpec extends AppSpec {

  private def errorHandler: HttpErrorHandler = inject[HttpErrorHandler]

  "AppErrorHandler#onClientError" should {
    "render an HTML page for a client that accepts HTML" in {
      val request = FakeRequest(GET, "/no-such-route").withHeaders(ACCEPT -> "text/html")
      val result = await(errorHandler.onClientError(request, NOT_FOUND, "not found"))
      contentType(Future.successful(result)) mustBe Some("text/html")
    }

    "render JSON for a client that accepts JSON" in {
      val request = FakeRequest(GET, "/no-such-route").withHeaders(ACCEPT -> "application/json")
      val result = await(errorHandler.onClientError(request, NOT_FOUND, "not found"))
      contentType(Future.successful(result)) mustBe Some("application/json")
    }
  }
}
