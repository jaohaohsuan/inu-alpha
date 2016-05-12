package read.storedQuery

import protocol.storedQuery._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.language.implicitConversions
/**
  * Created by henry on 4/30/16.
  */

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

object MultiMatchQuery {
  def unapply(arg: MatchBoolClause): Option[JValue] = {
    val MatchBoolClause(q,f,o,occur) = arg
    Some("bool" ->
      (occur -> Set(
        "multi_match" ->
          ("query"    -> q) ~~
            ("fields"   -> f.split("""\s+""").toList) ~~
            ("operator" -> o)
      ))
    )
  }
}

object BoolQuery {

  def unapply(arg: Iterable[BoolClause]): Option[JValue] = {

    val empty: JValue = parse("""{ "bool": {} }""")
    def build(clauses: Iterable[BoolClause]): JValue = {
      clauses.foldLeft(empty) { (acc, clause) =>
        val query: JValue = clause match {
          case MultiMatchQuery(json) => json
          case MultiSpanNearQuery(json) => json
          case NamedBoolClause(_, _, occur, innerClauses) => "bool" -> (occur -> Set(build(innerClauses.values)))
        }
        acc merge query
      }
    }

    Some(build(arg))
  }
}

object Percolator {
  def unapply(arg: StoredQuery): Option[(String,JObject)] = {
    val StoredQuery(id, title, clauses, tags) = arg
    val BoolQuery(query) = clauses.values
    val doc =
      ("title" -> title) ~~
      ("tags" -> tags) ~~
      ("query" -> query)
    Some((id, doc))
  }
}
