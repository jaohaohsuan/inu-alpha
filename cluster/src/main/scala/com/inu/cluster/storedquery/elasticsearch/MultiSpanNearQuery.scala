package com.inu.cluster.storedquery.elasticsearch

import com.inu.cluster.storedquery.algorithm.ListOfListCombination
import org.json4s._
import org.json4s.JsonDSL._
import com.inu.protocol.storedquery.messages._

object SynonymMultiSpanNearQuery {

  def unapply(arg: SpanNearClause): Option[JValue] = {
    val SpanNearClause(q, _, slop, inOrder, occur) = arg

    import ListOfListCombination._
    // queryString contains slash
    SlashRegex.findFirstMatchIn(q) match {
      case Some(_) =>
        val subBoolQueries = ListOfListCombination.divideBySlash(q).gen.flatMap { el =>
          arg.copy(query = el.mkString(" ")) match {
            case MultiSpanNearQuery(query) => Some(query)
            case _ => None
          }
        }
        Some(
          "bool" -> (
                      ("minimum_should_match" -> 1) ~~
                      ("should" -> subBoolQueries.toSet)
                    )
            )
      case _ => None
    }

  }
}

object MultiSpanNearQuery {

  /*
  span_term sample:
  GET /_search
  {
      "query": {
          "span_near" : {
              "clauses" : [
                  { "span_term" : { "field" : "value1" } },
                  { "span_term" : { "field" : "value2" } },
                  { "span_term" : { "field" : "value3" } }
              ],
              "slop" : 12,
              "in_order" : false
          }
      }
  }
   */

  def unapply(arg: SpanNearClause): Option[JValue] = {
    val SpanNearClause(query, _, slop, inOrder, occur) = arg
    query.split("""[\s,]+""").toSet match {
      case xs if xs.isEmpty => Some(JNothing)
      case xs =>
        val spanNear = spanNearQuery(inOrder, slop, xs)(_)
        val clause = "bool" -> (
                                ("minimum_should_match" -> 1) ~~
                                ("should"               -> arg.fields.map(spanNear).toSet)
                               )
        Some("bool" -> (
                        ("minimum_should_match" -> 1) ~~
                        (occur                  -> Set(clause))
                       )
        )
    }
  }

  def spanNearQuery(inOrder: Boolean, slop: Int, terms: Set[String])(field: String) = {
    "span_near" ->
      ("clauses"            -> terms.map { value => "span_term" -> (field -> value) }) ~~
        ("in_order"         -> inOrder) ~~
        ("collect_payloads" -> false) ~~
        ("slop"             -> slop)
  }

}
