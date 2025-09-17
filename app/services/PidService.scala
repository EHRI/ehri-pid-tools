package services

import com.google.inject.ImplementedBy
import models.{Pid, PidType}

import scala.concurrent.Future

@ImplementedBy(classOf[SqlPidService])
trait PidService {
  def findAll(ptype: PidType.Value, offset: Int = 0, limit: Int = 100): Future[Seq[Pid]]

  def findById(ptype: PidType.Value, value: String): Future[Option[Pid]]

  def findByTarget(ptype: PidType.Value, target: String): Future[Option[Pid]]

  def create(ptype: PidType.Value, value: String, target: String, client: String): Future[Pid]

  def update(ptype: PidType.Value, value: String, target: String): Future[Pid]

  def delete(ptype: PidType.Value, value: String): Future[Boolean]

  def tombstone(ptype: PidType.Value, value: String, client: String, reason: String): Future[Boolean]

  def deleteTombstone(ptype: PidType.Value, value: String): Future[Boolean]
}
