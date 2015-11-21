package frontend

import common.ImplicitLogging
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.http.{HttpEntity, MediaType, MediaTypes}
import spray.httpx.Json4sSupport
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._


object CollectionJsonSupport {
  val `application/vnd.collection+json` = MediaTypes.register(MediaType.custom("application/vnd.collection+json"))
}

trait CollectionJsonSupport extends Json4sSupport with ImplicitLogging {

  import CollectionJsonSupport._

  import scala.language.implicitConversions

  implicit def json4sFormats: Formats = DefaultFormats

  implicit def xUnmarshaller[T <: AnyRef : Manifest] =
    Unmarshaller[T](`application/vnd.collection+json`) {
    case HttpEntity.NonEmpty(contentType, data) =>
      (parse(data.asString) \ "template" \ "data" match {
        case JArray(xs) => xs.foldLeft(JObject()){ (acc, o) =>  acc ~ ((o \ "name").extract[String], o \ "value" )}
        case _ => JObject()
      }).extract
    }

  implicit val jsonStringMarshaller: Marshaller[String] =
    Marshaller.of[String](`application/vnd.collection+json`) { (value, contentType, ctx) =>
      ctx.marshalTo(HttpEntity(contentType, value))
    }

}
