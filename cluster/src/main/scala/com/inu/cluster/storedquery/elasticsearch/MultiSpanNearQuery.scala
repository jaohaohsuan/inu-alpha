package com.inu.cluster.storedquery.elasticsearch

import org.json4s._
import org.json4s.JsonDSL._
import com.inu.protocol.storedquery.messages._

object MultiSpanNearQuery {

  def spanNearQuery(inOrder: Boolean, slop: Int, terms: List[String])(field: String) =
    "span_near" ->
      ("clauses"          -> terms.map{ value => "span_term" -> (field -> value)}.toSet ) ~~
        ("in_order"         -> inOrder) ~~
        ("collect_payloads" -> false) ~~
        ("slop"             -> slop)

  def unapply(arg: SpanNearClause): Option[JValue] = {
    val SpanNearClause(terms, _, slop, inOrder, occur) = arg
    val spanNear = spanNearQuery(inOrder, slop, terms.split("""\s+""").toList)(_)
    val clause = "bool" -> ("should" -> arg.fields.map(spanNear).toSet)
    Some("bool" -> (occur -> Set(clause)))
  }
}
