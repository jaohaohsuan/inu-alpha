package protocol.storedFilter

case class StoredFilter(title: String = "",
                        id: String = "",
                        source: String = "", field: String = "")

sealed trait TermLevelQueries

object EmptyQuery extends TermLevelQueries

case class RangeQuery(gte: String, gt: String, lte: String, lt: String) extends TermLevelQueries
