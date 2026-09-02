package controllers

import models.{PidType, TargetCheckSummary}
import services.{PidService, TargetCheckService}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Shared by HealthController and DoiController, which both need the same
 * target-check summary plus tombstone count for a given PID type's health page.
 */
private[controllers] object HealthSummary {
  def fetch(targetCheckService: TargetCheckService, pidService: PidService, pidType: PidType.Value)
           (implicit ec: ExecutionContext): Future[(Option[TargetCheckSummary], Int)] =
    for {
      summary <- targetCheckService.summary()
      tombstones <- pidService.countTombstones(pidType)
    } yield (summary.find(_.ptype == pidType), tombstones)
}
