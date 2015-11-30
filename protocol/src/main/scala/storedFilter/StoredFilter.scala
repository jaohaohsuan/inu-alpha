package protocol.storedFilter

import org.json4s.JsonAST.{JArray, JValue}


sealed trait BoolClause {
  val occurrence: String
  val field: String
}

case class StoredFilter(typ: String, title: String, clauses: Map[String, BoolClause] = Map.empty) {
  import common.Key._
  lazy val newClauseKey = clauses.newKey

  def add(clause: BoolClause): StoredFilter = copy(clauses = clauses.+(newClauseKey -> clause))
  def remove(clauseId: String): StoredFilter = copy(clauses = clauses.-(clauseId))
}

final case class TermQuery(occurrence: String, field: String, value: JValue) extends BoolClause
final case class TermsQuery(occurrence: String, field: String, value: JArray) extends BoolClause
final case class RangeQuery(occurrence: String, field: String, gte: JValue, lte: JValue) extends BoolClause

