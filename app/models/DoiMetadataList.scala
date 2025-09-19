package models

import org.apache.pekko.util.ByteString
import play.api.http.Writeable
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.ws.{BodyWritable, InMemoryBody}

case class DoiMetadataList(items: Seq[DoiMetadata], total: Int, page: Int, totalPages: Int) extends Page[DoiMetadata] {
  override def numPages: Int = totalPages

  def withPidMeta(pids: Map[String, Pid]): DoiMetadataList =
    copy(items = items.map { item =>
      val pid: Option[Pid] = item.id.flatMap(id => pids.get(id))
      pid.fold(item)(url => item.withPidMeta(url))
    })
}

object DoiMetadataList {
  implicit def _reads: Reads[DoiMetadataList] = (
    (__ \ "data").read[Seq[DoiMetadata]] and
    (__ \ "meta" \ "total").read[Int] and
    (__ \ "meta" \ "page").read[Int] and
    (__ \ "meta" \ "totalPages").read[Int]
  )(DoiMetadataList.apply _)

  implicit def _writes: Writes[DoiMetadataList] = (
    (__ \ "data").write[Seq[DoiMetadata]] and
    (__ \ "meta" \ "total").write[Int] and
    (__ \ "meta" \ "page").write[Int] and
    (__ \ "meta" \ "totalPages").write[Int]
  )(unlift(DoiMetadataList.unapply))

  implicit val _writeable: Writeable[DoiMetadataList] = Writeable(
    d => ByteString(Json.toBytes(Json.toJson(d))),
    Some("application/vnd.api+json")
  )
}
