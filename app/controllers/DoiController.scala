package controllers

import auth.AuthAction
import models.{Doi, PidType}
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.json.Json
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

  private def jsonApiError(status: Status, message: String)(implicit request: RequestHeader): Result = {
    val errorResponse = Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "status" -> status.header.status,
          "title" -> Messages(message)
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
        render {
          case Accepts.Html() =>
            Ok(views.html.dois.show(pid.value, pid.target, doiMetadata.asDataCiteMetadata))
          case _ =>
            Ok(Doi(pid.target, doiMetadata))
        }
      }
      case None => immediate(render {
        case Accepts.Html() => NotFound(views.html.errors.notFound("DOI not found"))
        case _ => jsonApiError(NotFound, "errors.doi.notFound")
      })
    }
  }

  def register(): Action[Doi] = AuthAction.async(parse.tolerantJson[Doi]) { implicit request =>
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
    } yield Created(Doi(target, doiMetadata))
  }

  def update(prefix: String, suffix: String): Action[Doi] = AuthAction.async(parse.tolerantJson[Doi]) { implicit request =>
    val metadata = request.body.metadata
    val target = request.body.target

    val doi = s"$prefix/$suffix"
    logger.debug(s"Updating DOI '$doi' (${metadata.state}) with target: $target")
    logger.trace(s"  Metadata: $metadata")

    for {
      dm <- doiService.updateDoi(doi, metadata)
      _ <- pidService.update(PidType.DOI, doi, target)
    } yield Ok(Doi(target, dm))
  }

  def delete(prefix: String, suffix: String): Action[AnyContent] = AuthAction.async { implicit request =>
    // Delete the DOI and the associated PID
    for {
      _ <- doiService.deleteDoi(s"$prefix/$suffix")
      _ <- pidService.delete(PidType.DOI, s"$prefix/$suffix")
    } yield NoContent
  }
}
