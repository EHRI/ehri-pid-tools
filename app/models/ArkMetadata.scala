package models

import org.apache.pekko.util.ByteString
import play.api.libs.json._
import play.api.libs.ws.{BodyWritable, InMemoryBody}

case class ArkMetadata(id: Option[String], attributes: JsValue = JsObject.empty) {
  def prefix: String = id.flatMap(_.split("/").headOption).getOrElse("")
  def suffix: String = id.flatMap(_.split("/").lift(1)).getOrElse("")

  def withArk(ark: String): ArkMetadata = this.copy(id = Some(ark))
}

object ArkMetadata {
  implicit val _format: Format[ArkMetadata] = Json.format[ArkMetadata]

  implicit val _writeable: BodyWritable[ArkMetadata] = BodyWritable(
    (d: ArkMetadata) => InMemoryBody(ByteString(Json.toBytes(Json.obj("data" -> d)))),
    "application/vnd.api+json"
  )
}
