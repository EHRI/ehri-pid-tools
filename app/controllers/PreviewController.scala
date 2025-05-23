package controllers

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import play.api.cache.AsyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.twirl.api.Html

import javax.inject._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class PreviewController @Inject()(
  val controllerComponents: ControllerComponents,
  ws: WSClient,
  cache: AsyncCacheApi)(implicit ec: ExecutionContext) extends BaseController with I18nSupport {

  def preview(url: String): Action[AnyContent] = Action.async { implicit request: RequestHeader =>
    val snippet: Future[Html] = ws.url(url).get().map { response =>
      val soup = Jsoup.parse(response.body)
      // Extract: og:title, og:description, og:image
      val title = Jsoup.clean(soup.select("meta[property=og:title]").attr("content"), Safelist.basic())
      val description = Jsoup.clean(soup.select("meta[property=og:description]").attr("content"), Safelist.basic())
      val image = soup.select("meta[property=og:image]").attr("content")
      views.html.preview(url, title, description, image)
    }
    cache.getOrElseUpdate(s"preview:$url", 60.minutes)(snippet).map { html =>
      Ok(html).withHeaders(
        "Cache-Control" -> "max-age=3600, must-revalidate",
      )
    }.recover {
      case e: Exception =>
        InternalServerError("Error fetching preview: " + e.getMessage)
    }
  }
}
