package protocol.storedQuery

import org.json4s.JsonAST.JString
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import scala.language.implicitConversions

object ImplicitJsonConversions {

  implicit def json4sFormats: Formats = DefaultFormats

  implicit def dataApply[T <: AnyRef: Manifest](value: T): JValue =
    JArray(parse(write(value)) match {
      case JObject(xs) =>
        xs.map { case (f: String,v: JValue) => ("name" -> f) ~ ("value" -> v) }
      case _ => Nil
    })

  def boolClauseCollectionItem(c: (Int, BoolClause)): JObject = {
    val (clauseId, boolClause) = c
    import protocol.storedQuery.Exchange._
    val (data, id) =  boolClause match {
      case NamedBoolClause(id, title, occur, _) => (NamedClause(id, title, occur), s"#{uri}/named/$clauseId")
      case MatchBoolClause(query, field, op, occur) => (MatchClause(query,field, op, occur), s"#{uri}/match/$clauseId")
      case SpanNearBoolClause(terms, field, slop, inOrder, occur) => (SpanNearClause(terms.mkString(" "), field, slop, inOrder, occur), s"#{uri}/near/$clauseId")
    }
    ("data" -> data) ~ ("href" -> id)
  }

}
