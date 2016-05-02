package read.storedQuery

import protocol.storedQuery.{BoolClause, MatchBoolClause, NamedBoolClause, SpanNearBoolClause}
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.JValue
import org.json4s.native.JsonMethods._
import scala.language.implicitConversions
/**
  * Created by henry on 4/30/16.
  */

object BoolQuery2 {

  object MultiSpanNearQuery {

    def spanNearQuery(inOrder: Boolean, slop: Int, terms: List[String])(field: String) =
      "span_near" ->
        ("clauses"          -> terms.map{ value => "span_term" -> (field -> value)}.toSet ) ~~
        ("in_order"         -> inOrder) ~~
        ("collect_payloads" -> false) ~~
        ("slop"             -> slop)

    def unapply(arg: SpanNearBoolClause): Option[JValue] = {
      val SpanNearBoolClause(terms, _, slop, inOrder, occur) = arg
      val spanNear = spanNearQuery(inOrder, slop, terms)(_)
      val clause = "bool" -> ("should" -> arg.fields.map(spanNear).toSet)
      Some("bool" -> (occur -> Set(clause)))
    }

  }

  val empty: JValue = parse("""{ "bool": {} }""")

  implicit def getClauses(clauses: Map[Int, BoolClause]): Iterable[BoolClause] = clauses.values

  def toBoolQuery(clauses: Iterable[BoolClause]): JValue = {

    clauses.foldLeft(empty) { (acc, clause) =>
      val query: JValue = clause match {
        case MatchBoolClause(q,f,o,occur) =>
          "bool" ->
            (occur -> Set(
              "multi_match" ->
                ("query"    -> q) ~~
                ("fields"   -> f.split("""\s+""").toList) ~~
                ("operator" -> o)
            ))
        case MultiSpanNearQuery(json) => json
        case NamedBoolClause(_, _, occur, innerClauses) => "bool" -> (occur -> Set(toBoolQuery(innerClauses)))
        case _ => empty
      }
      acc merge query
    }
  }
}
