package models

import org.apache.pekko.util.ByteString
import play.api.http.Writeable
import play.api.libs.json._

case class JsonApiErrorInstance(
  title: Option[String],
  status: Option[String] = None,
  source: Option[JsValue] = None,
  detail: Option[JsValue] = None,
)

/**
 * A wrapper for the JSON+API error structure returned
 * from DataCite APIs.
 */
case class JsonApiError(errors: Seq[JsonApiErrorInstance]) {
  def firstMessage: Option[String] = errors.headOption.flatMap(_.title)
}
object JsonApiError {
  def apply(msg: String, detail: Option[JsValue] = None, status: Option[String] = None): JsonApiError = JsonApiError(
    Seq(JsonApiErrorInstance(
      source = None,
      title = Some(msg),
      detail = detail,
      status = status
    ))
  )

  implicit val _instanceFormat: Format[JsonApiErrorInstance] = Json.format[JsonApiErrorInstance]
  implicit val _format: Format[JsonApiError] = Json.format[JsonApiError]
  implicit val _writeable: Writeable[JsonApiError] = Writeable(
    data => ByteString(Json.toBytes(Json.toJson(data))),
    Some("application/vnd.api+json")
  )
}
