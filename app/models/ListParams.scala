package models

import play.api.mvc.QueryStringBindable
import utils.http.joinQueryString

case class ListParams(
  query: Option[String] = None,
  page: Int = 1,
  limit: Int = 20,
  sort: Option[String] = None
)

object ListParams {
  val QUERY = "q"
  val PAGE = "page"
  val LIMIT = "limit"
  val SORT = "sort"
  val DEFAULT_PAGE = 1
  val DEFAULT_LIMIT = 20

  def empty: ListParams = ListParams()

  implicit def _queryBinder: QueryStringBindable[ListParams] = new QueryStringBindable[ListParams] {
    def bindOrDefault[T](key: String, params: Map[String, Seq[String]], or: T)(implicit b: QueryStringBindable[T]): T = {
      b.bind(key, params).map(_.fold(_ => or, v => v)).getOrElse(or)
    }

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ListParams]] = {
      Some(
        Right(
          ListParams(
            bindOrDefault(QUERY, params, Option.empty[String]),
            bindOrDefault(PAGE, params, DEFAULT_PAGE).max(DEFAULT_PAGE),
            bindOrDefault(LIMIT, params, DEFAULT_LIMIT).min(DEFAULT_LIMIT).max(1),
            bindOrDefault(SORT, params, Option.empty[String]),
          )
        )
      )
    }

    override def unbind(key: String, value: ListParams): String = {
      val elems = value.query.map(q => QUERY -> q).toSeq ++
        (if (value.page == DEFAULT_PAGE) Seq.empty[(String, String)] else Seq(PAGE -> value.page.toString)) ++
        (if (value.limit == DEFAULT_LIMIT) Seq.empty[(String, String)] else Seq(LIMIT -> value.limit.toString)) ++
        value.sort.map(sort => SORT -> sort)
      joinQueryString(elems)
    }
  }
}


