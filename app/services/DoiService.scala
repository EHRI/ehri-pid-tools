package services

import com.google.inject.ImplementedBy
import models.{DoiMetadata, DoiMetadataList, DoiProfile, ListParams}

import scala.concurrent.Future


@ImplementedBy(classOf[WsDoiServiceHandle])
trait DoiServiceHandle {
  def forProfile(profile: DoiProfile): DoiService
}

case class DoiListParams(
  query: Option[String] = None,
  page: Int = 1,
  size: Int = 20,
  sort: Option[String] = None,
)

object DoiListParams {
  def empty: DoiListParams = DoiListParams()
  def apply(lp: ListParams): DoiListParams = DoiListParams(
    query = lp.query,
    page = lp.page,
    size = lp.limit,
    sort = lp.sort
  )
}

trait DoiService {

  val DOI_ALPHABET = "0123456789abcdefghjkmnpqrstuvwxyz"

  def listDoiMetadata(prefix: String, params: DoiListParams = DoiListParams.empty): Future[DoiMetadataList]

  def getDoiMetadata(doi: String): Future[DoiMetadata]

  def registerDoi(metadata: DoiMetadata): Future[DoiMetadata]

  def updateDoi(doi: String, metadata: DoiMetadata): Future[DoiMetadata]

  def deleteDoi(doi: String): Future[Boolean]

  def generateSuffix(): String

  protected def generateRandomString(alphabet: String, length: Int = 4): String =
    (1 to length).map(_ => alphabet.charAt(scala.util.Random.nextInt(alphabet.length))).mkString
}
