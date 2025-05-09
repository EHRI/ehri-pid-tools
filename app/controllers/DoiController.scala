package controllers

import auth.AuthAction
import models.{Doi, JsonApiData, PidType, TombstoneReason}
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.json.JsError.toJson
import play.api.libs.json.{JsError, JsSuccess, Json, Reads}
import play.api.mvc._
import services.{DoiService, PidService}

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.{successful => immediate}

/**
 * This controller handles actions related to the DOI (Digital Object Identifier) service.
 */
@Singleton
class DoiController @Inject()(
  val controllerComponents: ControllerComponents,
  doiService: DoiService,
  pidService: PidService,
  AuthAction: AuthAction,
)(implicit ec: ExecutionContext, appConfig: AppConfig) extends BaseController with I18nSupport {

  private val logger = play.api.Logger(getClass)

  // To override the max request size we unfortunately need to define our own body parser here:
  // The max value is drawn from config:
  private def apiJson[A](implicit reader: Reads[A]): BodyParser[A] = BodyParser { request =>
    parse.tolerantJson(request).map {
      case Left(simpleResult) => Left(simpleResult)
      case Right(jsValue) =>
        jsValue.validate(reader).map { a =>
          Right(a)
        } recoverTotal { jsError =>
          Left(BadRequest(
            Json.obj("errors" -> Json.arr(
              Json.obj(
                "status" -> BadRequest.header.status,
                "title" -> "Unexpected JSON payload", // TODO: i18n?
                "details" -> toJson(jsError),
              )
            ))
          ))
        }
    }
  }

  private def jsonApiError(status: Status, message: String, args: String*)(implicit request: RequestHeader): Result = {
    val errorResponse = Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "status" -> status.header.status,
          "title" -> Messages(message, args: _*)
        )
      )
    )
    status(errorResponse).as("application/vnd.api+json")
  }

  /**
   * Renders the list of DOIs.
   */
  def index(): Action[AnyContent] = Action.async { implicit request =>
    doiService.listDoiMetadata(appConfig.doiPrefix).map { doiMetadata =>
      Ok(views.html.dois.list(doiMetadata))
    }
  }

  def get(prefix: String, suffix: String): Action[AnyContent] = Action.async { implicit request =>
    pidService.findById(PidType.DOI, s"$prefix/$suffix").flatMap {
      case Some(pid) => doiService.getDoiMetadata(pid.value).map { doiMetadata =>
        val status: Status = pid.tombstone.fold(Ok)(_ => Gone)
        render {
          case Accepts.Html() =>
            status(views.html.dois.show(pid, doiMetadata.asDataCiteMetadata))
          case _ =>
            status(Doi(doiMetadata, pid.target, pid.tombstone))
        }
      }
      case None => immediate(render {
        case Accepts.Html() => NotFound(views.html.errors.notFound("DOI not found"))
        case _ => jsonApiError(NotFound, "errors.doi.notFound")
      })
    }
  }

  def register(): Action[Doi] = AuthAction.async(apiJson[Doi]) { implicit request =>
    val metadata = request.body.metadata
    val target = request.body.target


    val newSuffix = doiService.generateSuffix()
    val prefix = appConfig.doiPrefix
    val doi = s"$prefix/$newSuffix"
    val serviceUrl = routes.DoiController.get(prefix, newSuffix).absoluteURL()
    val newMetadata = metadata.withDoi(doi).withUrl(serviceUrl)

    logger.debug(s"Registering new DOI with '$doi' and URL: $serviceUrl")

    for {
      doiMetadata <- doiService.registerDoi(newMetadata)
      _ <- pidService.create(PidType.DOI, doi, target, request.clientId)
    } yield Created(Doi(doiMetadata, target))
  }

  def update(prefix: String, suffix: String): Action[Doi] = AuthAction.async(apiJson[Doi]) { implicit request =>
    val metadata = request.body.metadata
    val target = request.body.target

    val doi = s"$prefix/$suffix"
    logger.debug(s"Updating DOI '$doi' (${metadata.state}) with target: $target")
    logger.trace(s"  Metadata: $metadata")

    for {
      dm <- doiService.updateDoi(doi, metadata)
      _ <- pidService.update(PidType.DOI, doi, target)
    } yield Ok(Doi(dm, target))
  }

  def delete(prefix: String, suffix: String): Action[AnyContent] = AuthAction.async { implicit request =>
    // Delete the DOI and the associated PID
    for {
      _ <- doiService.deleteDoi(s"$prefix/$suffix")
      _ <- pidService.delete(PidType.DOI, s"$prefix/$suffix")
    } yield NoContent
  }

  def tombstone(prefix: String, suffix: String): Action[JsonApiData] = AuthAction.async(apiJson[JsonApiData]) { implicit request =>
    request.body.data.validate[TombstoneReason] match {
      case JsSuccess(reason, _) =>
        val doi = s"$prefix/$suffix"
        logger.debug(s"Tombstoning DOI '$doi' with reason: '$reason'...")

        pidService.tombstone(PidType.DOI, doi, request.clientId, reason.reason).map {
          case true => NoContent
          case false =>
            jsonApiError(BadRequest, "errors.doi.tombstoneFailed", doi)
        }

      case JsError(errors) =>
        logger.error(s"Invalid request body: $errors")
        immediate(jsonApiError(BadRequest, "errors.invalidRequest"))
    }
  }

  def deleteTombstone(prefix: String, suffix: String): Action[AnyContent] = AuthAction.async { implicit request =>
    val doi = s"$prefix/$suffix"
    logger.debug(s"Deleting tombstone for DOI '$doi'...")

    pidService.deleteTombstone(PidType.DOI, doi).map {
      case true => NoContent
      case false =>
        jsonApiError(BadRequest, "errors.doi.tombstoneNotFound", doi)
    }
  }
}
