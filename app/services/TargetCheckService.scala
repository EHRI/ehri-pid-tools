package services

import com.google.inject.ImplementedBy
import models.{PidType, TargetCheck, TargetCheckSummary}

import scala.concurrent.Future

@ImplementedBy(classOf[SqlTargetCheckService])
trait TargetCheckService {
  def recordResult(ptype: PidType.Value, value: String, target: String, ok: Boolean, statusCode: Option[Int], error: Option[String]): Future[Unit]

  /**
   * Currently-failing checks, optionally restricted to a single PID type.
   * Pass None to get failures across every PID type.
   */
  def latestFailures(ptype: Option[PidType.Value] = None): Future[Seq[TargetCheck]]

  /**
   * A checked/failing/last-checked summary per PID type that has been checked at least once.
   */
  def summary(): Future[Seq[TargetCheckSummary]]
}
