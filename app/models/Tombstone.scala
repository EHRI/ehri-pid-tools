package models

import play.api.libs.json.{Format, Json}

import java.time.Instant

case class Tombstone(
  deletedAt: Instant,
  client: String,
  reason: String,
)

object Tombstone {
  implicit val _format: Format[Tombstone] = Json.format[Tombstone]
}

case class TombstoneReason(reason: String)
object TombstoneReason {
  implicit val _format: Format[TombstoneReason] = Json.format[TombstoneReason]
}
