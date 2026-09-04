package controllers

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import play.api.cache.AsyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.twirl.api.Html
import services.PidService

import javax.inject._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class PreviewController @Inject()(
  val controllerComponents: ControllerComponents,
  ws: WSClient,
  cache: AsyncCacheApi,
  config: Configuration,
  pidService: PidService,
)(implicit ec: ExecutionContext) extends BaseController with I18nSupport {

  private val logger = Logger(classOf[PreviewController])

  private def checkImage(url: String): Future[Boolean] = {
    ws.url(url).head().map { response =>
      if (response.status != 200) {
        logger.debug(s"Unexpected preview image URL response ($url): ${response.status}")
        false
      } else true
    }.recover {
      case e: Exception =>
        logger.debug(s"Error validating preview image URL ($url): ${e.getMessage}")
        false // If we can't access the image, return false
    }
  }

  private def getMetaProperty(soup: org.jsoup.nodes.Document, property: String): Option[String] = {
    val prop = Jsoup.clean(soup.select(s"meta[property=$property]").attr("content"), Safelist.basic())
    if (prop.trim.nonEmpty) Some(prop) else None
  }

  def preview(url: String): Action[AnyContent] = Action.async { implicit request: RequestHeader =>
    pidService.targetExists(url).flatMap {
      case false =>
        Future.successful(NotFound("Not a registered PID target"))
      case true =>
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
        cache.getOrElseUpdate(s"preview:$url", config.get[Duration]("preview.cache.time"))(snippet).map { html =>
          Ok(html).withHeaders(
            "Cache-Control" -> "max-age=3600, must-revalidate",
          )
        }.recover {
          case e: Exception =>
            InternalServerError("Error fetching preview: " + e.getMessage)
        }
    }
  }
}
