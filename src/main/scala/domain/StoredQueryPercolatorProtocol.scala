package domain
import domain.StoredQueryAggregateRoot.{ StoredQuery }

object StoredQueryPercolatorProtocol {

  val `/user/stored-query-aggregate-root/active` = "/user/stored-query-aggregate-root/active"

  case object Pull
  case class Changes(items: Set[(StoredQuery, Int)])

  case class RegisterQueryOK(records: Set[(String, Int)])

}
