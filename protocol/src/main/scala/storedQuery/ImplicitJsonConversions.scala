package protocol.storedQuery

import net.hamnaberg.json.collection.data.JavaReflectionData
import org.json4s._


object ImplicitJsonConversions {

  implicit def json4sFormats: Formats = DefaultFormats

  implicit def dataApply[T <: AnyRef: Manifest](value: T)(implicit formats: org.json4s.Formats): JValue =
     new JavaReflectionData[T]()(formats, manifest[T]).apply(value).map(_.underlying) match {
      case Nil => JNothing
      case xs => JArray(xs)
  }

   def boolClauseToJValue(boolClause: BoolClause): JValue = {

    import protocol.storedQuery.Exchange._
    import scala.language.implicitConversions
    boolClause match {
      case NamedBoolClause(id, title, occur, _) => NamedClause(id, title, occur)
      case MatchBoolClause(query, field, op, occur) => MatchClause(query,field, op, occur)
      case SpanNearBoolClause(terms, field, slop, inOrder, occur) => SpanNearClause(terms.mkString(" "), field, slop, inOrder, occur)
    }
  }

}
