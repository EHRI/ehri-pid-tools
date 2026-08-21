package controllers

import play.api.i18n.I18nSupport
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(
  val controllerComponents: ControllerComponents
)(implicit config: AppConfig) extends BaseController with I18nSupport {

  /**
   * Create an Action to render an HTML page.
   */
  def index(): Action[AnyContent] = Action { implicit request: RequestHeader =>
    Ok(views.html.index())
  }

  def policy(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.policy())
  }
}
