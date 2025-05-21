package services

import play.api.http._
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.ControllerHelpers.Accepts
import play.api.mvc.{Rendering, RequestHeader, Result}

import javax.inject._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

class AppErrorHandler @Inject() (
  jsonHandler: JsonHttpErrorHandler,
  htmlHandler: DefaultHttpErrorHandler,
  val messagesApi: MessagesApi
) extends PreferredMediaTypeHttpErrorHandler(
  "application/vnd.api+json" -> jsonHandler,
  "application/json" -> jsonHandler,
  "text/html"        -> htmlHandler,
) with Rendering with I18nSupport {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    jsonHandler.onClientError(request, statusCode, message)
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val r: RequestHeader = request
    exception match {
      case e: DoiNotFound =>
        render.async {
          case Accepts.Html() =>
            immediate(play.api.mvc.Results.NotFound(views.html.errors.notFound(e.message)))
          case _ =>
            // Handle DoiServiceException specifically by proxying it
            e.underlying.map { json =>
              immediate(play.api.mvc.Results.NotFound(json)
                .as("application/vnd.api+json"))
            }.getOrElse {
              immediate(play.api.mvc.Results.NotFound(Json.obj(
                  "errors" -> Json.arr(
                    Json.obj(
                      "status" -> Status.NOT_FOUND,
                      "message" -> Messages("errors.doi.notFound")
                    )
                  )
                ))
                .as("application/vnd.api+json"))
            }
        }
      case e: DoiServiceException =>
        render.async {
          case Accepts.Html() =>
            htmlHandler.onServerError(request, exception)
          case _ =>
            // Handle DoiServiceException specifically by proxying it
            immediate(play.api.mvc.Results.Status(e.status)(e.underlying)
              .as("application/vnd.api+json"))
        }
      case _ =>
        render.async {
          case Accepts.Html() => htmlHandler.onServerError(request, exception)
          case _ => jsonHandler.onServerError(request, exception)
        }
    }
  }
}
