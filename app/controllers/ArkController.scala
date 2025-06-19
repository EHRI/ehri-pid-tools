package controllers

import auth.AuthAction
import models._
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.json.JsError.toJson
import play.api.libs.json._
import play.api.mvc._
import services._

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.{successful => immediate}

/**
 * This controller handles actions related to the ARK (Archival Reference Key) service.
 */
@Singleton
class ArkController @Inject()(
  val controllerComponents: ControllerComponents,
  arkService: ArkService,
  pidService: PidService,
  AuthAction: AuthAction,
)(implicit val ec: ExecutionContext, appConfig: AppConfig) extends BaseController with I18nSupport with AppControllerHelpers {

  private val logger = play.api.Logger(getClass)

  private def jsonApiError(status: Status, message: String, args: String*)(implicit request: RequestHeader): Result = {
    status(JsonApiError(Messages(message, args: _*), status = Some(status.header.status.toString)))
  }

  /**
   * Renders the list of ARKs.
   */
  def index(): Action[AnyContent] = Action.async { implicit request =>
    pidService.findAll(PidType.ARK).map { arks =>
      Ok(views.html.arks.list(arks.map(p => Ark.create(p.value, p.target, p.tombstone))))
    }
  }

  def getByTarget(target: String): Action[AnyContent] = Action.async { implicit request =>
    pidService.findByTarget(PidType.ARK, target).map {
      case Some(pid) =>
        render {
          case Accepts.Html() => Ok(pid.value)
          case _ => Ok(Json.toJson(pid.value))
        }
      case None => render {
        case Accepts.Html() => NotFound(views.html.errors.notFound("ARK not found"))
        case _ => jsonApiError(NotFound, "errors.ark.notFound")
      }
    }
  }

  def get(prefix: String, suffix: String): Action[AnyContent] = Action.async { implicit request =>
    pidService.findById(PidType.ARK, s"$prefix/$suffix").map {
      case Some(pid) =>
        val target = pid.target
        pid.tombstone.map { tombstone =>
          render {
            case Accepts.Html() => Gone(views.html.arks.tombstone(tombstone.reason))
            case _ => jsonApiError(Gone, "errors.ark.gone.explanation")
          }
        }.getOrElse {
          render {
            case Accepts.Html() => SeeOther(target)
            case _ => Ok(Ark(
                metadata = ArkMetadata(Some(pid.value)),
                target = target,
                tombstone = None
              )
            )
          }
        }
      case None => render {
        case Accepts.Html() => NotFound(views.html.errors.notFound("ARK not found"))
        case _ => jsonApiError(NotFound, "errors.ark.notFound")
      }
    }
  }

  def register(): Action[JsValue] = AuthAction.async(apiJson[JsValue]) { implicit request =>
    request.body.validate[Ark] match {
      case JsSuccess(Ark(metadata, target, _), _) =>
        val newSuffix = arkService.generateSuffix()
        val prefix = appConfig.doiPrefix
        val newArk = s"$prefix/$newSuffix"
        val serviceUrl = routes.DoiController.get(prefix, newSuffix).absoluteURL()
        val newMetadata = metadata.withArk(newArk)

        logger.debug(s"Registering new ARK with '$newArk' and URL: $serviceUrl")

        (for {
          pid <- pidService.create(PidType.DOI, newArk, target, request.clientId)
        } yield Created(Ark(newMetadata, target, pid.tombstone)))
          .recover {
            case _: PidExistsException =>
              jsonApiError(UnprocessableEntity, "errors.ark.collisionError")
            case e =>
              logger.error(s"Failed to register DOI: ${e.getMessage}")
              jsonApiError(InternalServerError, "errors.ark.registrationFailed")
          }
      case JsError(errors) =>
        logger.error(s"Invalid request body: $errors")
        immediate(jsonApiError(BadRequest, "errors.invalidRequest"))
    }

  }

  def update(prefix: String, suffix: String): Action[JsValue] = AuthAction.async(apiJson[JsValue]) { implicit request =>
    request.body.validate[Ark] match {
      case JsSuccess(Ark(metadata, target, _), _) =>
        val ark = s"$prefix/$suffix"
        logger.debug(s"Updating ARK '$ark' with target: $target")

        for {
          pid <- pidService.update(PidType.ARK, ark, target)
        } yield Ok(Ark(metadata, target, pid.tombstone))
      case JsError(errors) =>
        logger.error(s"Invalid request body: $errors")
        immediate(jsonApiError(BadRequest, "errors.invalidRequest"))
    }
  }

  def delete(prefix: String, suffix: String): Action[AnyContent] = AuthAction.async { implicit request =>
    // Delete the PID
    for {
      _ <- pidService.delete(PidType.ARK, s"$prefix/$suffix")
    } yield NoContent
  }

  def tombstone(prefix: String, suffix: String): Action[JsonApiData] = AuthAction.async(apiJson[JsonApiData]) { implicit request =>
    request.body.data.validate[TombstoneReason] match {
      case JsSuccess(reason, _) =>
        val ark = s"$prefix/$suffix"
        logger.debug(s"Tombstoning ARK '$ark' with reason: '$reason'...")

        pidService.tombstone(PidType.ARK, ark, request.clientId, reason.reason).map {
          case true => NoContent
          case false =>
            jsonApiError(BadRequest, "errors.ark.tombstoneFailed", ark)
        }

      case JsError(errors) =>
        logger.error(s"Invalid request body: $errors")
        immediate(jsonApiError(BadRequest, "errors.invalidRequest"))
    }
  }

  def deleteTombstone(prefix: String, suffix: String): Action[AnyContent] = AuthAction.async { implicit request =>
    val ark = s"$prefix/$suffix"
    logger.debug(s"Deleting tombstone for ARK '$ark'...")

    pidService.deleteTombstone(PidType.ARK, ark).map {
      case true => NoContent
      case false =>
        jsonApiError(BadRequest, "errors.ark.tombstoneNotFound", ark)
    }
  }
}
