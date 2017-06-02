package com.inu.frontend

import org.elasticsearch.action.search.SearchResponse
import org.json4s
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.{HttpCharsets, HttpEntity, MediaType, MessageEntity}
import akka.http.scaladsl.server.{Directive1, Directives}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}

import scala.language.implicitConversions

trait CollectionJsonBuilder {
  def body(hits: Iterable[json4s.JValue], tags: String, pagination: Seq[String]): String

  def itemsMap(hits: Iterable[json4s.JValue]) = hits.map(extract)

  private def extract(hit: json4s.JValue): Option[(String, String)] = {
    implicit val formats = org.json4s.DefaultFormats
    hit match {
      case o: JObject =>
        (o \ "id").extractOpt[String].map { id =>
          val data = compact(render(o \ "data"))
          Some((id, data))
        }.getOrElse(None)
      case _ => None
    }
  }
}

object CollectionJsonSupport {

  val `application/vnd.collection+json`: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("vnd.collection+json", HttpCharsets.`UTF-8`)

  object DataField {
    private val namesOfDataField = List("value", "array")

    implicit class JValueWithProperty(o: JValue) {
      def toProperty: Option[(String, JValue)] = {
        val JString(name) = o \ "name"
        namesOfDataField.map { field => o \ field }.find(_.toOption.isDefined).map { value => (name, value) }
      }
    }

  }
}

trait CollectionJsonSupport extends Directives {

  import CollectionJsonSupport._

  implicit val serialization = org.json4s.native.Serialization // or native.Serialization
  implicit val formats = org.json4s.DefaultFormats

  def pagination(r: SearchResponse)(implicit uri: akka.http.scaladsl.model.Uri): Directive1[Pagination] = {
    parameter('size.as[Int] ? 10, 'from.as[Int] ? 0 ).tmap {
      case (size, from) => {
        import com.inu.frontend.Pagination._
        Pagination(size, from, r)
      }
    }
  }

  implicit class Template0[T <: AnyRef](value: T) {
    def asTemplate: JObject =
      "data" -> JArray(parse(write(value)) match {
        case JObject(xs) =>
          xs.map {
            case (f: String, v: JArray) => ("name" -> f) ~~ ("array" -> v)
            case (f: String, v: JValue) => ("name" -> f) ~~ ("value" -> v)
          }
        case _ => Nil
      })
  }

  implicit def unmarshaller[T <: AnyRef : Manifest] =
    Unmarshaller
      .stringUnmarshaller
      .forContentTypes(`application/vnd.collection+json`)
      .map { raw =>
        parse(raw) \ "template" \ "data" match {
          case JArray(xs) =>
            import DataField._
            xs.foldLeft(JObject()) { (acc, o) =>
              o.toProperty.map { p => acc ~ p }.getOrElse(acc)
            } extract
          case _ => throw new Exception(s"unable to parse data of template. $raw")
        }
      }

//    Unmarshaller[T](`application/vnd.collection+json`) {
//    case HttpEntity.NonEmpty(contentType, data) =>
//      (parse(data.asString) \ "template" \ "data" match {
//        case JArray(xs) => xs.foldLeft(JObject()){ (acc, o) =>
//          ((o \ "value").toOption :: (o \ "array").toOption :: Nil).find(_.isDefined) match {
//            case Some(value) => acc ~ ((o \ "name").extract[String], value)
//            case None => acc
//          }
//         }
//        case _ => JObject()
//      }).extract
//    }

  implicit val jsonStringMarshaller: Marshaller[String, MessageEntity] =
    Marshaller.stringMarshaller(`application/vnd.collection+json`)

//  { (value, contentType, ctx) =>
//      ctx.marshalTo(HttpEntity(contentType, value))
//    }

  implicit val jfieldsMarshaller: Marshaller[List[JField],MessageEntity] =
    Marshaller.stringMarshaller(`application/vnd.collection+json`).compose {
      case fields: List[JField] =>
        val json: JObject = ("version" -> "1.0")
        compact(render("collection" -> fields.foldLeft(json){ (obj, field) => obj ~~ field }))
    }
//    Marshaller.of[List[JField]](`application/vnd.collection+json`) { (value, contentType, ctx) =>
//      val json: JObject = ("version" -> "1.0")
//      ctx.marshalTo(HttpEntity(contentType,
//        compact(render("collection" -> value.foldLeft(json){ (obj, field) => obj ~~ field })))
//        )
//    }
}
