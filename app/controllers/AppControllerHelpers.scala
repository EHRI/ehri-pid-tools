package controllers

import models.JsonApiError
import play.api.libs.json.JsError.toJson
import play.api.libs.json.Reads
import play.api.mvc.{BaseController, BodyParser}

trait AppControllerHelpers {
  self: BaseController =>

  implicit def ec: scala.concurrent.ExecutionContext

  // To override the max request size we unfortunately need to define our own body parser here:
  // The max value is drawn from config:
  protected def apiJson[A](implicit reader: Reads[A]): BodyParser[A] = BodyParser { request =>
    parse.tolerantJson(request).map {
      case Left(simpleResult) => Left(simpleResult)
      case Right(jsValue) =>
        jsValue.validate(reader).map { a =>
          Right(a)
        } recoverTotal { jsError =>
          Left(BadRequest(
            JsonApiError(
              "Unexpected JSON payload", // TODO: i18n?
              status = Some(BadRequest.header.status.toString),
              detail = Some(toJson(jsError)),
            )))
        }
    }
  }
}
