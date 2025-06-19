package services

import anorm.{Macro, RowParser, SqlStringInterpolation}
import models.{Pid, PidType, Tombstone}
import org.postgresql.util.PSQLException
import play.api.Configuration
import play.api.db.Database

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class SqlPidService @Inject()(db: Database, config: Configuration)(implicit ec: ExecutionContext) extends PidService {

  private implicit val tombstoneParser: RowParser[Option[Tombstone]] = Macro.parser[Tombstone]("deleted_at", "client", "reason").?
  private implicit val pidParser: RowParser[Pid] = Macro.parser[Pid]("ptype", "value", "target", "tombstone")

  override def findAll(ptype: PidType.Value): Future[Seq[Pid]] = Future {
    db.withConnection{ implicit conn =>
      SQL"SELECT ptype, value, target FROM pids WHERE ptype = $ptype::pid_type"
        .as(pidParser.*)
    }
  }(ec)

  override def findById(ptype: PidType.Value, value: String): Future[Option[Pid]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT p.ptype, p.value, p.target, t.client, t.reason, t.deleted_at
           FROM pids p
           LEFT JOIN tombstones t ON p.id = t.pid_id
           WHERE p.ptype = $ptype::pid_type AND p.value = $value"""
        .as(pidParser.singleOpt)
    }
  }(ec)

  override def findByTarget(ptype: PidType.Value, target: String): Future[Option[Pid]] = Future {
    db.withConnection { implicit conn =>
      SQL"""SELECT p.ptype, p.value, p.target, t.client, t.reason, t.deleted_at
           FROM pids p
           LEFT JOIN tombstones t ON p.id = t.pid_id
           WHERE p.ptype = $ptype::pid_type AND p.target = $target"""
        .as(pidParser.singleOpt)
    }
  }(ec)

  override def create(ptype: PidType.Value, value: String, target: String, client: String): Future[Pid] = Future {
    db.withConnection { implicit conn =>
      try {
        SQL"""INSERT INTO pids (ptype, value, target, client) VALUES ($ptype::pid_type, $value, $target, $client)
            RETURNING ptype, value, target"""
          .as(pidParser.single)
      } catch {
        case e: PSQLException if e.getSQLState == "23505" =>
          throw PidExistsException(s"PID with type $ptype and value $value already exists.")
      }
    }
  }(ec)

  override def update(ptype: PidType.Value, value: String, target: String): Future[Pid] = Future {
    db.withConnection { implicit conn =>
      SQL"""WITH updated_pids AS (
              UPDATE pids
              SET target = $target
              WHERE ptype = $ptype::pid_type AND value = $value
              RETURNING id, ptype, value, target
            )
            SELECT p.ptype, p.value, p.target, t.client, t.reason, t.deleted_at
            FROM updated_pids p
            LEFT JOIN tombstones t ON p.id = t.pid_id
            """
        .as(pidParser.single)
    }
  }(ec)

  override def delete(ptype: PidType.Value, value: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM pids WHERE ptype = $ptype::pid_type AND value = $value"
        .executeUpdate() == 1
    }
  }(ec)

  override def tombstone(ptype: PidType.Value, value: String, client: String, reason: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"""INSERT INTO tombstones (pid_id, client, reason)
           SELECT id, $client, $reason
           FROM pids
           WHERE ptype = $ptype::pid_type AND value = $value"""
        .executeUpdate() == 1
    }
  }(ec)

  override def deleteTombstone(ptype: PidType.Value, value: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM tombstones WHERE pid_id = (SELECT id FROM pids WHERE ptype = $ptype::pid_type AND value = $value)"
        .executeUpdate() == 1
    }
  }(ec)
}
