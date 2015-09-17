package protocol.storedQuery


case object Pull
case class Changes(items: Set[(StoredQuery, Int)])
case class RegisterQueryOK(records: Set[(String, Int)])

object Terminology {

  val OccurrenceRegex = """^must$|^must_not$|^should$""".r
  val BoolQueryClauseRegex = """^match$|^near$|^named$""".r

}

sealed trait BoolClause {
  val occurrence: String
}
sealed trait Unallied extends BoolClause

case class NamedBoolClause(storedQueryId: String, title: String, occurrence: String, clauses: Map[Int, BoolClause] = Map.empty) extends BoolClause

case class MatchBoolClause(query: String, field: String, operator: String, occurrence: String) extends Unallied

case class SpanNearBoolClause(terms: List[String], field: String, slop: Int, inOrder: Boolean, occurrence: String) extends Unallied

case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty, tags: Set[String] = Set.empty)

object AggregateRoot {
  val Name = "stored-query-aggregate-root"
}

object AggregateRootClient {
  import akka.cluster.client.ClusterClient

  private val address = s"/user/${AggregateRoot.Name}"

  def PullChanges = ClusterClient.SendToAll(address, Pull)

  def SendToAllRegisterQueryOK(changes: Set[(String, Int)]) = ClusterClient.SendToAll(address, RegisterQueryOK(changes))
}
