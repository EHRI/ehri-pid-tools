package services

import play.api.Configuration

import java.nio.charset.StandardCharsets
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


case class ConfigAuthService @Inject()(config: Configuration) extends AuthService {
  private val decoder = java.util.Base64.getDecoder

  override def authenticate(token: String): Future[Option[String]] = {
    // Configured clients are stored in the application.conf file
    val clients = config.get[Map[String, String]]("auth.clients")
    // Check if the string is a base64 string consisting of
    // a registered client id and a secret
    // separated by a colon
    val decoded = new String(decoder.decode(token), StandardCharsets.UTF_8)
    val parts = decoded.split(":", 2)
    if (parts.length == 2) {
      val clientId = parts(0)
      val clientSecret = parts(1)
      val secret = clients.get(clientId)

      secret match {
        case Some(`clientSecret`) => immediate(Some(clientId))
        case _ => immediate(None)
      }
    } else {
      immediate(None)
    }
  }
}
