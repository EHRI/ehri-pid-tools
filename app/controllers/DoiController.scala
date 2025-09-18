package controllers

import auth.AuthAction
import models.{Doi, DoiMetadata, DoiProfile, DoiState, JsonApiData, JsonApiError, ListParams, Pid, PidType, TombstoneReason}
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.json.JsError.toJson
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json, Reads}
import play.api.mvc._
import services.{DoiExistsException, DoiListParams, DoiNotFound, DoiService, DoiServiceHandle, PidExistsException, PidService}

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.{successful => immediate}

/**
 * This controller handles actions related to the DOI (Digital Object Identifier) service.
 */
@Singleton
class DoiController @Inject()(
  val controllerComponents: ControllerComponents,
  doiServiceHandle: DoiServiceHandle,
  pidService: PidService,
  AuthAction: AuthAction,
)(implicit ec: ExecutionContext, appConfig: AppConfig) extends BaseController with I18nSupport {

  private val logger = play.api.Logger(getClass)

  private implicit def doiProfile(implicit req: RequestHeader): DoiProfile = {
    val id = req.getQueryString("_profile").getOrElse("default")
    DoiProfile.fromConfig(id, appConfig.config)
  }

  private def doiService(implicit req: RequestHeader): DoiService =
    doiServiceHandle.forProfile(doiProfile(req))

  private def doiPrefix(implicit req: RequestHeader) = doiProfile.prefix

  private def jsonApiError(status: Status, message: String, args: String*)(implicit request: RequestHeader): Result = {
    val errorObj = JsonApiError(Messages(message, args: _*), status = Some(status.header.status.toString))
    status(errorObj).as("application/vnd.api+json")
  }

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
            JsonApiError(
              "Unexpected JSON payload", // TODO: i18n?
              status = Some(BadRequest.header.status.toString),
              detail = Some(toJson(jsError)),
            )))
        }
    }
  }

  /**
   * Renders the list of DOIs.
   */
  def index(params: ListParams = ListParams.empty): Action[AnyContent] = Action.async { implicit request =>
    doiService.listDoiMetadata(doiPrefix, DoiListParams(params)).map { doiMetadata =>
      Ok(views.html.dois.list(doiMetadata))
    }
  }

  def example(): Action[AnyContent] = Action { implicit request =>
    val fakePid = Pid(PidType.DOI, "10.82422/B09Z-4K37", "https://example.com/", None)
    val raw = Json.parse(getClass.getResourceAsStream("/datacite-example-B09Z-4K37.json").readAllBytes())
    val fakeDoi = Doi(
      metadata = DoiMetadata(
        id = Some(fakePid.value),
        `type` = Some("dois"),
        attributes = (raw \ "data" \ "attributes").as[JsValue]
      ),
      target = fakePid.target,
      tombstone = fakePid.tombstone
    )
    Ok(views.html.dois.show(fakePid, fakeDoi.metadata.asDataCiteMetadata, prod = true))
  }

  def get(prefix: String, suffix: String): Action[AnyContent] = Action.async { implicit request =>
    pidService.findById(PidType.DOI, s"$prefix/$suffix").flatMap {
      case Some(pid) => doiService.getDoiMetadata(pid.value).map { doiMetadata =>
        val retStatus: Status = pid.tombstone.fold(Ok)(_ => Gone)
        render {
          // If we're returning HTML and configured to not show hidden items, throw a 404
          case Accepts.Html() if doiMetadata.state != DoiState.Findable && !doiProfile.showHidden =>
            throw DoiNotFound(Messages("errors.doi.notFound"))
          // If we're returning HTML and hidden items are visible, show the page:
          case Accepts.Html() =>
            retStatus(views.html.dois.show(pid, doiMetadata.asDataCiteMetadata))
          // Otherwise, show metadata...
          case _ =>
            retStatus(Doi(doiMetadata, pid.target, pid.tombstone))
        }
      }
      case None => immediate(render {
        case Accepts.Html() => NotFound(views.html.errors.notFound("DOI not found"))
        case _ => jsonApiError(NotFound, "errors.doi.notFound")
      })
    }
  }

  def register(): Action[JsValue] = AuthAction.async(apiJson[JsValue]) { implicit request =>
    request.body.validate[Doi] match {
      case JsSuccess(Doi(metadata, target, _), _) =>
        val newSuffix = doiService.generateSuffix()
        val newDoi = s"$doiPrefix/$newSuffix"
        val serviceUrl = routes.DoiController.get(doiPrefix, newSuffix).absoluteURL()
        val newMetadata = metadata.withDoi(newDoi).withUrl(serviceUrl)

        logger.debug(s"Registering new DOI with '$newDoi' and URL: $serviceUrl")

        (for {
          doiMetadata <- doiService.registerDoi(newMetadata)
          pid <- pidService.create(PidType.DOI, newDoi, target, request.clientId)
        } yield Created(Doi(doiMetadata, target, pid.tombstone)))
          .recoverWith {
            case e: DoiExistsException =>
              logger.error(s"Failed to register DOI: ${e.getMessage}")
              immediate(jsonApiError(UnprocessableEntity, "errors.doi.collisionError"))
            case _: PidExistsException =>
              // Clean up the draft DOI if it already exists
              doiService.deleteDoi(newDoi).map { _ =>
                logger.error(s"DOI '$newDoi' already exists, returning error.")
                jsonApiError(UnprocessableEntity, "errors.doi.collisionError")
              }
            case e =>
              logger.error(s"Failed to register DOI: ${e.getMessage}")
              immediate(jsonApiError(InternalServerError, "errors.doi.registrationFailed"))
          }
      case JsError(errors) =>
        logger.error(s"Invalid request body: $errors")
        immediate(jsonApiError(BadRequest, "errors.invalidRequest"))
    }

  }

  def update(prefix: String, suffix: String): Action[JsValue] = AuthAction.async(apiJson[JsValue]) { implicit request =>
    request.body.validate[Doi] match {
      case JsSuccess(Doi(metadata, target, _), _) =>
        val doi = s"$prefix/$suffix"
        logger.debug(s"Updating DOI '$doi' (${metadata.state}) with target: $target")
        logger.trace(s"  Metadata: $metadata")

        for {
          dm <- doiService.updateDoi(doi, metadata)
          pid <- pidService.update(PidType.DOI, doi, target)
        } yield Ok(Doi(dm, target, pid.tombstone))
      case JsError(errors) =>
        logger.error(s"Invalid request body: $errors")
        immediate(jsonApiError(BadRequest, "errors.invalidRequest"))
    }
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
