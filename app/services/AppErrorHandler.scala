package services

import play.api.http._
import play.api.mvc.ControllerHelpers.Accepts
import play.api.mvc.{Rendering, RequestHeader, Result}

import javax.inject._
import scala.concurrent.Future

class AppErrorHandler @Inject() (
  jsonHandler: JsonHttpErrorHandler,
  htmlHandler: DefaultHttpErrorHandler,
) extends PreferredMediaTypeHttpErrorHandler(
  "application/vnd.api+json" -> jsonHandler,
  "application/json" -> jsonHandler,
  "text/html"        -> htmlHandler,
) with Rendering {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    jsonHandler.onClientError(request, statusCode, message)
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val r: RequestHeader = request
    exception match {
      case e: DoiServiceException =>
        render.async {
          case Accepts.Html() =>
            htmlHandler.onServerError(request, exception)
          case _ =>
            // Handle DoiServiceException specifically by proxying it
            Future.successful(play.api.mvc.Results.Status(e.status)(e.underlying)
              .as("application/vnd.api+json"))
        }
    }
    jsonHandler.onServerError(request, exception)
  }
}
