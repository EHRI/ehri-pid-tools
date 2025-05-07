package services

import models.{DoiMetadata, DoiMetadataList, JsonApiData}
import org.apache.pekko.util.ByteString
import play.api.Configuration
import play.api.http.Status.{CREATED, NO_CONTENT, OK}
import play.api.libs.json.{JsError, JsSuccess, Json, Reads}
import play.api.libs.ws.{BodyWritable, InMemoryBody, WSClient, WSResponse}

import java.util.Base64
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object WsDoiService {
  implicit val apiResponseWritable: BodyWritable[JsonApiData] = BodyWritable(
    (jsonApi: JsonApiData) => InMemoryBody(ByteString(Json.toBytes(jsonApi.data))),
    "application/vnd.api+json"
  )
}

case class WsDoiService @Inject()(ws: WSClient, config: Configuration)(implicit ec: ExecutionContext) extends DoiService {

  private val doiBaseUrl = config.get[String]("doi.api.baseUrl")

  override def listDoiMetadata(prefix: String, page: Int = 1, size: Int = 10, sort: String = "created"): Future[DoiMetadataList] = {
    val params = Map(
      "prefix" -> prefix,
      "page[number]" -> page.toString,
      "page[size]" -> size.toString,
      "sort" -> sort
    )
    ws.url(doiBaseUrl)
      .withHttpHeaders(headers.toSeq: _*)
      .withQueryStringParameters(params.toSeq: _*).get().map { response =>
        parseResponse[DoiMetadataList](response)
      }
  }

  override def getDoiMetadata(doi: String): Future[DoiMetadata] = {
    ws.url(s"$doiBaseUrl/$doi").withHttpHeaders(headers.toSeq: _*).get().map { response =>
      val jsonApiData = parseResponse[JsonApiData](response)
      jsonApiData.data.as[DoiMetadata]
    }
  }

  override def registerDoi(metadata: DoiMetadata): Future[DoiMetadata] = {
    ws.url(doiBaseUrl).withHttpHeaders(headers.toSeq: _*).post(metadata).map { response =>
      val jsonApiData = parseResponse[JsonApiData](response, CREATED)
      jsonApiData.data.as[DoiMetadata]
    }
  }

  override def updateDoi(doi: String, metadata: DoiMetadata): Future[DoiMetadata] = {
    ws.url(s"$doiBaseUrl/$doi").withHttpHeaders(headers.toSeq: _*).put(metadata).map { response =>
      val jsonApiData = parseResponse[JsonApiData](response)
      jsonApiData.data.as[DoiMetadata]
    }
  }

  override def deleteDoi(doi: String): Future[Boolean] = {
    ws.url(s"$doiBaseUrl/$doi").withHttpHeaders(headers.toSeq: _*).delete().map { response =>
      response.status == NO_CONTENT
    }
  }

  private def headers: Map[String, String] = Map(
    "Authorization" -> s"Basic $providerAuth",
    "Content-Type" -> "application/vnd.api+json",
    "Accept" -> "application/vnd.api+json"
  )

  private def providerAuth: String = {
    // Get a base64 encoded concatenation of the repository_id and repository_secret
    val repositoryId = config.get[String]("doi.api.repositoryId")
    val repositorySecret = config.get[String]("doi.api.repositorySecret")
    Base64.getEncoder.encodeToString(s"$repositoryId:$repositorySecret".getBytes("UTF-8"))
  }

  private def parseResponse[T: Reads](response: WSResponse, expected: Int = OK): T = {
    if (response.status == expected) {
      response.json.validate[T] match {
        case JsSuccess(data, _) => data
        case JsError(errors) =>
          throw new RuntimeException(s"Failed to parse response: ${response.status} ${response.statusText} - $errors")
      }
    } else {
      throw DoiServiceException("Unexpected DOI Service response", response.status, response.json)
    }
  }
}
