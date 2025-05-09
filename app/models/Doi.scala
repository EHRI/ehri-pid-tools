package models

import org.apache.pekko.util.ByteString
import play.api.http.Writeable
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Doi(metadata: DoiMetadata, target: String, tombstone: Option[Tombstone] = None)
object Doi {
  implicit val _reads: Reads[Doi] = (
    (__ \ "data").read[DoiMetadata] and
    (__ \ "meta" \ "target").read[String] and
    (__ \ "meta" \ "tombstone").readNullable[Tombstone]
  )(Doi.apply _)

  // Format that writes the metadata json+api format
  // with the target as a meta attribute
  implicit val _writes: Writes[Doi] = (
    (__ \ "data").write[DoiMetadata] and
    (__ \ "meta" \ "target").write[String] and
    (__ \ "meta" \ "tombstone").writeNullable[Tombstone]
  )(unlift(Doi.unapply))

  implicit val _format: Format[Doi] = Format(_reads, _writes)

  implicit val _writeable: Writeable[Doi]  = Writeable(
    d => ByteString(Json.toBytes(Json.toJson(d))),
      Some("application/vnd.api+json")
  )
}
