package services

import com.google.inject.ImplementedBy
import models.{DoiMetadata, DoiMetadataList}

import scala.concurrent.Future


@ImplementedBy(classOf[WsDoiService])
trait DoiService {

  val DOI_ALPHABET = "0123456789abcdefghjkmnpqrstuvwxyz"

  def listDoiMetadata(prefix: String, page: Int = 1, size: Int = 1000, sort: String = "-created"): Future[DoiMetadataList]

  def getDoiMetadata(doi: String): Future[DoiMetadata]

  def registerDoi(metadata: DoiMetadata): Future[DoiMetadata]

  def updateDoi(doi: String, metadata: DoiMetadata): Future[DoiMetadata]

  def deleteDoi(doi: String): Future[Boolean]

  def generateSuffix(): String =
    generateRandomString(DOI_ALPHABET) + "-" + generateRandomString(DOI_ALPHABET)

  private def generateRandomString(alphabet: String, length: Int = 4): String =
    (1 to length).map(_ => alphabet.charAt(scala.util.Random.nextInt(alphabet.length))).mkString
}
