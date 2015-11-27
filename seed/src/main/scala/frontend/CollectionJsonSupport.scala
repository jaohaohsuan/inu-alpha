package frontend

import common.ImplicitLogging
import org.json4s.JsonAST.{JValue, JArray, JObject}
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import spray.http.{HttpEntity, MediaType, MediaTypes}
import spray.httpx.Json4sSupport
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._
import spray.routing._


object CollectionJsonSupport {
  val `application/vnd.collection+json` = MediaTypes.register(MediaType.custom("application/vnd.collection+json"))
}

trait CollectionJsonSupport extends Json4sSupport with Directives{

  import CollectionJsonSupport._

  import scala.language.implicitConversions

  implicit class Template0[T <: AnyRef](value: T) {
    def asTemplate: JObject =
      "data" -> JArray(parse(write(value)) match {
        case JObject(xs) =>
          xs.map { case (f: String, v: JValue) => ("name" -> f) ~~ ("value" -> v) }
        case _ => Nil
      })
  }

  def collection: Directive1[JObject] = requestUri.flatMap {
    case uri => provide("collection" ->
      ("version" -> "1.0") ~~
        ("href" -> s"${uri}") ~~
        ("links" -> JNothing) ~~
        ("queries" -> JNothing) ~~
        ("items" -> JNothing) ~~
        ("template" -> JNothing)
    )
  }

  def item[T <: AnyRef](value: T): Directive1[JObject] = requestUri.flatMap {
    case uri =>

      val data: JObject = value match {
        case x: JObject => x
        case x => x.asTemplate
      }
      val json: JObject = "collection" ->
        ("version" -> "1.0") ~~
        ("href" -> s"${uri.withPath(uri.path.reverse.tail.tail.reverse)}") ~~
        ("links" -> JNothing) ~~
        ("queries" -> JNothing) ~~
        ("items" -> List(("href" -> s"$uri") ~~ data ~~ ("links" -> JNothing))) ~~
        ("template" -> data)
      provide(json)
  }

  implicit def json4sFormats: Formats

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
