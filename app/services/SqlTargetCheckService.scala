package services

import anorm.{Macro, RowParser, SqlStringInterpolation}
import models.{PidType, TargetCheck, TargetCheckSummary}
import play.api.db.Database

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class SqlTargetCheckService @Inject()(db: Database)(implicit ec: ExecutionContext) extends TargetCheckService {

  private implicit val targetCheckParser: RowParser[TargetCheck] =
    Macro.parser[TargetCheck]("ptype", "value", "target", "checked_at", "status_code", "ok", "error")

  private implicit val summaryParser: RowParser[TargetCheckSummary] =
    Macro.parser[TargetCheckSummary]("ptype", "checked", "failing", "last_checked_at")

  override def recordResult(ptype: PidType.Value, value: String, target: String, ok: Boolean, statusCode: Option[Int], error: Option[String]): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL"""INSERT INTO target_checks (ptype, value, target, checked_at, status_code, ok, error)
           VALUES ($ptype::pid_type, $value, $target, now(), $statusCode, $ok, $error)
           ON CONFLICT (ptype, value) DO UPDATE
             SET target = EXCLUDED.target,
                 checked_at = EXCLUDED.checked_at,
                 status_code = EXCLUDED.status_code,
                 ok = EXCLUDED.ok,
                 error = EXCLUDED.error"""
        .executeUpdate()
      ()
    }
  }(ec)

  override def latestFailures(ptype: Option[PidType.Value] = None): Future[Seq[TargetCheck]] = Future {
    val ptypeStr = ptype.map(_.toString)
    db.withConnection { implicit conn =>
      SQL"""SELECT ptype, value, target, checked_at, status_code, ok, error
           FROM target_checks
           WHERE ($ptypeStr::pid_type IS NULL OR ptype = $ptypeStr::pid_type) AND ok = false
           ORDER BY ptype, value"""
        .as(targetCheckParser.*)
    }
  }(ec)

  override def summary(): Future[Seq[TargetCheckSummary]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT ptype,
                  COUNT(*)::int AS checked,
                  COUNT(*) FILTER (WHERE NOT ok)::int AS failing,
                  MAX(checked_at) AS last_checked_at
           FROM target_checks
           GROUP BY ptype
           ORDER BY ptype"""
        .as(summaryParser.*)
    }
  }(ec)
}
