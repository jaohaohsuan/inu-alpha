package frontend

import net.hamnaberg.json.collection.{JsonCollection, NativeJsonCollectionParser, Template}
import net.hamnaberg.json.collection.data.{DataApply, JavaReflectionData}
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.JsonMethods._
import spray.http.{HttpEntity, MediaType, MediaTypes}
import spray.httpx.Json4sSupport
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._

object CollectionJsonSupport {
  val `application/vnd.collection+json` = MediaTypes.register(MediaType.custom("application/vnd.collection+json"))
}

trait CollectionJsonSupport extends Json4sSupport {

  import CollectionJsonSupport._
  import scala.language.implicitConversions

  implicit def json4sFormats: Formats = DefaultFormats

  implicit val templateUnmarshaller: Unmarshaller[Template] =
    Unmarshaller[Template](`application/vnd.collection+json`) {
      case HttpEntity.NonEmpty(contentType, data) =>
        val string = data.asString
        NativeJsonCollectionParser.parseTemplate(string) match {
          case Right(o: Template) => o
          case Left(e) =>
            throw e
        }
    }

  implicit def templateToObjectUnmarshaller[T <: AnyRef : Manifest]: Unmarshaller[T] =
    Unmarshaller.delegate[Template, T](`application/vnd.collection+json`) { template =>
      new JavaReflectionData[T].unapply(template.data) match {
        case Some(o) => o
        case None =>
          throw new Exception(s"Unable to convert Template to '$manifest.getClass.getName' class.")
      }
    }

  implicit val collectionJsonMarshaller: Marshaller[JsonCollection] =
    Marshaller.of[JsonCollection](`application/vnd.collection+json`) { (value, contentType, ctx) =>
      ctx.marshalTo(HttpEntity(contentType, compact(render(value.toJson))))
    }

  implicit def asTemplate[T <: AnyRef : Manifest](value: T)(implicit formats: org.json4s.Formats): Option[Template] =
    Some(Template(value)(dataApply(manifest, formats)))

  implicit def dataApply[T <: AnyRef : Manifest](implicit formats: org.json4s.Formats): DataApply[T] = {
    new JavaReflectionData[T]()(formats, manifest[T])
  }
}
