package controllers

import models.PidType
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.{PidService, TargetCheckService}

import javax.inject._
import scala.concurrent.ExecutionContext

/**
 * Displays a summary of periodic PID target health checks (see
 * services.TargetHealthChecker), linking through to a details page per
 * PID type. Currently only DOIs are checked.
 */
@Singleton
class HealthController @Inject()(
  val controllerComponents: ControllerComponents,
  targetCheckService: TargetCheckService,
  pidService: PidService,
)(implicit ec: ExecutionContext, config: AppConfig) extends BaseController with I18nSupport {

  def index(): Action[AnyContent] = Action.async { implicit request =>
    HealthSummary.fetch(targetCheckService, pidService, PidType.DOI).map { case (doiSummary, tombstones) =>
      Ok(views.html.health(doiSummary, tombstones))
    }
  }
}
