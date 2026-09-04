package services

import models.{DoiMetadata, DoiMetadataList, DoiProfile, JsonApiData, JsonApiError}
import org.apache.pekko.util.ByteString
import play.api.Configuration
import play.api.http.Status
import play.api.http.Status.{CREATED, NO_CONTENT, OK}
import play.api.libs.json.{JsError, JsNull, JsSuccess, JsValue, Json, Reads}
import play.api.libs.ws.{BodyWritable, InMemoryBody, WSClient, WSResponse}

import java.util.Base64
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Class for instantiating a DOI service with a given profile.
 *
 * @param ws     the web service instance
 * @param config the config instance
 * @param ec     an execution context
 */
case class WsDoiServiceHandle @Inject()(ws: WSClient, config: Configuration)(implicit ec: ExecutionContext) extends DoiServiceHandle {
  override def forProfile(profile: DoiProfile): DoiService = WsDoiService(profile, ws, config)
}

object WsDoiService {
  implicit val apiResponseWritable: BodyWritable[JsonApiData] = BodyWritable(
    (jsonApi: JsonApiData) => InMemoryBody(ByteString(Json.toBytes(jsonApi.data))),
    "application/vnd.api+json"
  )
}

case class WsDoiService(profile: DoiProfile, ws: WSClient, config: Configuration)(implicit ec: ExecutionContext) extends DoiService {

  private def doiBaseUrl: String = profile.apiBaseUrl

  override def listDoiMetadata(prefix: String, params: DoiListParams): Future[DoiMetadataList] = {
    val listParams: Seq[(String, String)] = Map(
      "prefix" -> prefix,
      "query" -> params.query.getOrElse(""),
      "page[number]" -> params.page.toString,
      "page[size]" -> params.size.toString,
      "sort" -> params.sort.getOrElse("-created"),
      "publisher" -> true.toString,
    ).toSeq ++ apiParams
    ws.url(profile.apiBaseUrl)
      .withHttpHeaders(headers.toSeq: _*)
      .withQueryStringParameters(listParams: _*).get().map { response =>
        parseResponse[DoiMetadataList](response)
      }
  }

  override def getDoiMetadata(doi: String): Future[DoiMetadata] = {
    ws.url(s"$doiBaseUrl/$doi")
      .withQueryStringParameters(apiParams: _*)
      .withHttpHeaders(allHeaders.toSeq: _*)
      .get().map { response =>
        val jsonApiData = parseResponse[JsonApiData](response)
        jsonApiData.data.as[DoiMetadata]
      }
  }

  override def registerDoi(metadata: DoiMetadata): Future[DoiMetadata] = {
    ws.url(doiBaseUrl).withHttpHeaders(allHeaders.toSeq: _*)
      .withQueryStringParameters(apiParams: _*)
      .post(metadata).map { response =>
      val jsonApiData = parseResponse[JsonApiData](response, CREATED)
      jsonApiData.data.as[DoiMetadata]
    }
  }

  override def updateDoi(doi: String, metadata: DoiMetadata): Future[DoiMetadata] = {
    ws.url(s"$doiBaseUrl/$doi")
      .withQueryStringParameters(apiParams: _*)
      .withHttpHeaders(allHeaders.toSeq: _*)
      .put(metadata).map { response =>
      val jsonApiData = parseResponse[JsonApiData](response)
      jsonApiData.data.as[DoiMetadata]
    }
  }

  override def deleteDoi(doi: String): Future[Boolean] = {
    ws.url(s"$doiBaseUrl/$doi").withHttpHeaders(allHeaders.toSeq: _*).delete().map { response =>
      response.status == NO_CONTENT
    }
  }

  override def generateSuffix(): String = {
    generateRandomString(DOI_ALPHABET) + "-" + generateRandomString(DOI_ALPHABET)
  }

  private def apiParams: Seq[(String, String)] = Seq("publisher" -> true.toString, "affiliation" -> true.toString)

  private def allHeaders: Map[String, String] = headers ++ authHeaders

  private def headers: Map[String, String] = Map(
    "Content-Type" -> "application/vnd.api+json",
    "Accept" -> "application/vnd.api+json"
  )

  private def authHeaders: Map[String, String] = Map(
    "Authorization" -> s"Basic $providerAuth",
  )

  private def providerAuth: String = {
    // Get a base64 encoded concatenation of the repository_id and repository_secret
    val repositoryId = profile.repositoryId
    val repositorySecret = profile.repositorySecret
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
      // The error body isn't guaranteed to be valid JSON (e.g. an upstream
      // proxy error page), so don't let parsing it throw before we've had a
      // chance to classify the response by status code.
      val bodyJson: JsValue = Try(response.json).getOrElse(JsNull)
      val errorObj = bodyJson.asOpt[JsonApiError]
      if (response.status == Status.NOT_FOUND) {
        throw DoiNotFound("errors.doi.notFound", Some(bodyJson))
      } else if (response.status == Status.UNPROCESSABLE_ENTITY
        && errorObj.exists(_.firstMessage.contains("This DOI has already been taken"))) {
        throw DoiExistsException("errors.doi.collisionError", Some(bodyJson))
      } else {
        throw DoiServiceException("errors.doi.exception", response.status, bodyJson)
      }
    }
  }
}
