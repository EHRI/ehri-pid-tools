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

  private def checkImage(url: String): Future[Boolean] = {
    ws.url(url).head().map { response =>
      response.status == 200
    }.recover {
      case _: Exception => false // If we can't access the image, return false
    }
  }

  private def getMetaProperty(soup: org.jsoup.nodes.Document, property: String): Option[String] = {
    val prop = Jsoup.clean(soup.select(s"meta[property=$property]").attr("content"), Safelist.basic())
    if (prop.trim.nonEmpty) Some(prop) else None
  }

  def preview(url: String): Action[AnyContent] = Action.async { implicit request: RequestHeader =>
    val snippet: Future[Html] = ws.url(url).get().flatMap { response =>
      val soup = Jsoup.parse(response.body)
      // Extract: og:site_name, og:title, og:description, og:image, og:url
      val siteName = getMetaProperty(soup, "og:site_name")
      val title = getMetaProperty(soup, "og:title")
      val description = getMetaProperty(soup, "og:description").getOrElse("")
      val image = getMetaProperty(soup, "og:image")
      val canonicalUrl = getMetaProperty(soup, "og:url").getOrElse(url)
      val fullTitle = (for ( t <- title; s <- siteName) yield s"$t | $s").orElse(title).getOrElse("")

      // Check we can access the image via a HEAD request:
      image.map(url => checkImage(url)).getOrElse(Future.successful(false)).map { imageOk =>
        views.html.preview(canonicalUrl, fullTitle, description, image.filter( _ => imageOk))
      }
    }
    cache.getOrElseUpdate(s"preview:$url", 1.second)(snippet).map { html =>
      Ok(html).withHeaders(
        "Cache-Control" -> "max-age=3600, must-revalidate",
      )
    }.recover {
      case e: Exception =>
        InternalServerError("Error fetching preview: " + e.getMessage)
    }
  }
}
