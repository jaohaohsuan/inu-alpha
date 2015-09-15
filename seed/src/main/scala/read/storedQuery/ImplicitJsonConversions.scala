package read.storedQuery

import net.hamnaberg.json.collection.data.{JavaReflectionData, DataApply}
import org.json4s.JsonAST.JArray
import org.json4s._
import scala.language.implicitConversions

object ImplicitJsonConversions {

  implicit def json4sFormats: Formats = DefaultFormats

  implicit def dataApply[T <: AnyRef: Manifest](value: T)(implicit formats: org.json4s.Formats): JValue =
     new JavaReflectionData[T]()(formats, manifest[T]).apply(value).map(_.underlying) match {
      case Nil => JNothing
      case xs => JArray(xs)
  }

}
