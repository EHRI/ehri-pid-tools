package models

import play.api.libs.json.{Json, Writes}

import java.time.Instant

case class TargetCheck(
  ptype: PidType.Value,
  value: String,
  target: String,
  checkedAt: Instant,
  statusCode: Option[Int],
  ok: Boolean,
  error: Option[String],
)

object TargetCheck {
  implicit val _writes: Writes[TargetCheck] = Json.writes[TargetCheck]
}

case class TargetCheckSummary(
  ptype: PidType.Value,
  checked: Int,
  failing: Int,
  lastCheckedAt: Option[Instant],
)

object TargetCheckSummary {
  implicit val _writes: Writes[TargetCheckSummary] = Json.writes[TargetCheckSummary]
}
