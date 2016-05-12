package read.storedQuery

import protocol.storedQuery._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{read, write}
import org.json4s.native.Serialization

import scala.language.implicitConversions
import shapeless._
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

  def unapply(arg: SpanNearClause): Option[JValue] = {
    val SpanNearClause(terms, _, slop, inOrder, occur) = arg
    val spanNear = spanNearQuery(inOrder, slop, terms.split("""\s+""").toList)(_)
    val clause = "bool" -> ("should" -> arg.fields.map(spanNear).toSet)
    Some("bool" -> (occur -> Set(clause)))
  }
}

object MultiMatchQuery {
  def unapply(arg: MatchClause): Option[JValue] = {
    val MatchClause(q,f,o,occur) = arg
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
          case NamedClause(_, _, occur, innerClauses) => "bool" -> (occur -> Set(build(innerClauses.values)))
        }
        acc merge query
      }
    }

    Some(build(arg))
  }
}

object Occurs {

  implicit val formats = DefaultFormats +
    FieldSerializer[NamedClause](FieldSerializer.ignore("clauses")) +
    FieldSerializer[NamedClause](FieldSerializer.ignore("shortName"))

  def unapply(arg: Map[Int, BoolClause]): Option[JValue] = {
        val result = arg.map({ case (id, el) =>

          val JObject(xs) = parse(write(el))
          val data = JArray(xs.map { case JField(f,v) => ("name" -> f) ~~ ("value" -> v) })

          el.occurrence -> Set(
            ("data" -> data) ~~
            ("href" -> s"#{uri}/${el.shortName}/$id")
          ): JObject
        }).foldLeft(JObject(Nil)){ (acc, j) => acc.merge(j)}
        Some(result)
    }
}

object Percolator {
  def unapply(arg: StoredQuery): Option[(String,JObject)] = {
    val StoredQuery(id, title, clauses, tags) = arg
    val BoolQuery(query) = clauses.values
    val item =
      ("id"   -> id) ~~
      ("data" -> Set(
        ("name" -> "title") ~~ ("value" -> title),
        ("name" -> "tags") ~~ ("value" -> tags.mkString(" "))
      ))

    val doc =
      ("title" -> title) ~~
      ("tags" -> tags) ~~
      ("query" -> query) ~~
      ("item" -> item)
    Some((id, doc))
  }
}