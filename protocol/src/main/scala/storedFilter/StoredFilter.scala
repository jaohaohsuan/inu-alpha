package protocol.storedFilter

import org.json4s.JsonAST.JValue


sealed trait BoolClause {
  val occurrence: String
}

case class StoredFilter(typ: String, title: String, clauses: Map[String, BoolClause] = Map.empty) {
  import common.Key._
  lazy val newClauseKey = clauses.newKey

  def addClauses(clause: BoolClause): StoredFilter = copy(clauses = clauses.+(newClauseKey -> clause))
}

final case class TermQuery(filterId: String ,occurrence: String, typ: String ,field: String, value: JValue) extends BoolClause

sealed trait TermLevelQueries

object EmptyQuery extends TermLevelQueries

case class RangeQuery(gte: String, gt: String, lte: String, lt: String) extends TermLevelQueries
