package models

import org.apache.pekko.util.ByteString
import play.api.libs.json._
import play.api.libs.ws.{BodyWritable, InMemoryBody}

object DoiState extends Enumeration with EnumToJSON {
  val Draft = Value("draft")
  val Registered = Value("registered")
  val Findable = Value("findable")
}

case class DoiMetadata(id: Option[String], `type`: Option[String], attributes: JsValue, meta: Option[JsObject] = None) {
  def state: DoiState.Value = (attributes \ "state").asOpt[DoiState.Value].getOrElse(DoiState.Draft)
  def prefix: String = id.flatMap(_.split("/").headOption).getOrElse("")
  def suffix: String = id.flatMap(_.split("/").lift(1)).getOrElse("")
  def title: Option[String] = (attributes \ "titles" \ 0 \ "title").asOpt[String]
  def target: Option[String] = meta.flatMap(json => (json \ "target").asOpt[String])

  def withDoi(doi: String): DoiMetadata = {
    val updatedAttributes = attributes.as[JsObject] + ("doi" -> JsString(doi))
    this.copy(id = Some(doi), attributes = updatedAttributes)
  }

  def withUrl(url: String): DoiMetadata = {
    val updatedAttributes = attributes.as[JsObject] + ("url" -> JsString(url))
    this.copy(attributes = updatedAttributes)
  }

  def withPidMeta(pid: Pid): DoiMetadata = {
    val targetMeta = Json.obj("target" -> pid.target)
    val tombstoneMeta = pid.tombstone.fold(Json.obj())(t => Json.obj("tombstone" -> t))
    val pidMeta = targetMeta ++ tombstoneMeta
    val newMeta = meta.fold(pidMeta)(existing => existing ++ pidMeta)
    this.copy(meta = Some(newMeta))
  }

  def asDataCiteMetadata: DataCiteMetadata = attributes.as[DataCiteMetadata]
}

object DoiMetadata {
  implicit val _format: Format[DoiMetadata] = Json.format[DoiMetadata]

  implicit val _writeable: BodyWritable[DoiMetadata] = BodyWritable(
    (d: DoiMetadata) => InMemoryBody(ByteString(Json.toBytes(Json.obj("data" -> d)))),
    "application/vnd.api+json"
  )
}
