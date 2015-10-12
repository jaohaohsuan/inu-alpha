package protocol.storedQuery

import net.hamnaberg.json.collection.data.JavaReflectionData
import org.json4s.JsonAST.JString
import org.json4s._
import scala.language.implicitConversions
import org.json4s.JsonDSL._

object ImplicitJsonConversions {

  implicit def json4sFormats: Formats = DefaultFormats

  implicit def dataApply[T <: AnyRef: Manifest](value: T)(implicit formats: org.json4s.Formats): JValue =
     new JavaReflectionData[T]()(formats, manifest[T]).apply(value).map(_.underlying) match {
      case Nil => JNothing
      case xs => JArray(xs)
  }

  def boolClauseToJObject(c: (Int, BoolClause)): JObject = {
    val (clauseId, boolClause) = c
    import protocol.storedQuery.Exchange._
    val (data, id) =  boolClause match {
      case NamedBoolClause(id, title, occur, _) => (NamedClause(id, title, occur), JString(s"#{uri}/named/$clauseId"))
      case MatchBoolClause(query, field, op, occur) => (MatchClause(query,field, op, occur), JString(s"#{uri}/match/$clauseId"))
      case SpanNearBoolClause(terms, field, slop, inOrder, occur) => (SpanNearClause(terms.mkString(" "), field, slop, inOrder, occur), JString(s"#{uri}/near/$clauseId"))
    }
    JObject(("data", data), ("href", id))
  }

}
