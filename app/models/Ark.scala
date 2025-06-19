package models

import org.apache.pekko.util.ByteString
import play.api.http.Writeable
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Ark(metadata: ArkMetadata, target: String, tombstone: Option[Tombstone] = None)
object Ark {
  def create(ark: String, target: String, tombstone: Option[Tombstone] = None): Ark = Ark(
    metadata = ArkMetadata(Some(ark)),
    target = target,
    tombstone = tombstone
  )

  implicit val _reads: Reads[Ark] = (
    (__ \ "data").read[ArkMetadata] and
    (__ \ "meta" \ "target").read[String] and
    (__ \ "meta" \ "tombstone").readNullable[Tombstone]
  )(Ark.apply _)

  // Format that writes the metadata json+api format
  // with the target as a meta attribute
  implicit val _writes: Writes[Ark] = (
    (__ \ "data").write[ArkMetadata] and
    (__ \ "meta" \ "target").write[String] and
    (__ \ "meta" \ "tombstone").writeNullable[Tombstone]
  )(unlift(Ark.unapply))

  implicit val _format: Format[Ark] = Format(_reads, _writes)

  implicit val _writeable: Writeable[Ark]  = Writeable(
    d => ByteString(Json.toBytes(Json.toJson(d))),
      Some("application/vnd.api+json")
  )
}
