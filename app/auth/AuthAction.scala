package auth

import play.api.{Configuration, Environment}
import play.api.http.HttpConfiguration
import play.api.i18n.{I18nComponents, Messages}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results.Unauthorized
import play.api.mvc.{ActionBuilder, AnyContent, BodyParsers, RequestHeader, Result}
import services.AuthService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}

/**
 * AuthAction is an ActionBuilder that checked
 * for the existence of a valid token in the request
 * header and enriches the request with the bearer ID.
 */
case class AuthRequest[A](
  request: play.api.mvc.Request[A],
  clientId: String
) extends play.api.mvc.WrappedRequest[A](request)


case class AuthAction @Inject()(
  parser: BodyParsers.Default,
  authService: AuthService,
  executionContext: ExecutionContext,
  configuration: Configuration,
  httpConfiguration: HttpConfiguration,
  environment: Environment
) extends ActionBuilder[AuthRequest, AnyContent] with I18nComponents {
  private implicit val ec: ExecutionContext = executionContext
  private implicit val messages: Messages = messagesApi.preferred(Seq.empty)

  private def jsonError(implicit messages: Messages): Result = Unauthorized {
    Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "code" -> "invalid_token",
          "message" -> Messages("error.invalidToken"),
          "status" -> 401,
        )
      )
    )
  }.as("application/vnd.api+json")

  override def invokeBlock[A](request: play.api.mvc.Request[A], block: AuthRequest[A] => Future[play.api.mvc.Result]): Future[Result] = {
    val token = request.headers.get("Authorization").flatMap(_.split(" ").lift(1))
    token match {
      case Some(t) =>
        authService.authenticate(t).flatMap {
          case Some(clientId) =>
            block(AuthRequest(request, clientId))
          case None =>
            immediate(jsonError)
        }
      case None => immediate(jsonError)
    }
  }
}
