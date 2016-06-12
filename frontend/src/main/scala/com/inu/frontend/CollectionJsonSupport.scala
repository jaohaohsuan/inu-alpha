package com.inu.frontend

import org.elasticsearch.action.search.SearchResponse
import org.json4s
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import spray.http.{HttpEntity, MediaType, MediaTypes}
import spray.httpx.Json4sSupport
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._

import scala.language.implicitConversions
import spray.routing._

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
  val `application/vnd.collection+json` = MediaTypes.register(MediaType.custom("application/vnd.collection+json"))
}

trait CollectionJsonSupport extends Json4sSupport with Directives{

  import CollectionJsonSupport.`application/vnd.collection+json`

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

  def collection: Directive1[JObject] = requestUri.flatMap {
    case uri => provide("collection" ->
      ("version" -> "1.0") ~~
        ("href" -> s"$uri") ~~
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

  implicit def unmarshaller[T <: AnyRef : Manifest] =
    Unmarshaller[T](`application/vnd.collection+json`) {
    case HttpEntity.NonEmpty(contentType, data) =>
      (parse(data.asString) \ "template" \ "data" match {
        case JArray(xs) => xs.foldLeft(JObject()){ (acc, o) =>
          ((o \ "value").toOption :: (o \ "array").toOption :: Nil).find(_.isDefined) match {
            case Some(value) => acc ~ ((o \ "name").extract[String], value)
            case None => acc
          }
         }
        case _ => JObject()
      }).extract
    }

  implicit val jsonStringMarshaller: Marshaller[String] =
    Marshaller.of[String](`application/vnd.collection+json`) { (value, contentType, ctx) =>
      ctx.marshalTo(HttpEntity(contentType, value))
    }

  implicit val jfieldMarshaller: Marshaller[List[JField]] =
    Marshaller.of[List[JField]](`application/vnd.collection+json`) { (value, contentType, ctx) =>
      val json: JObject = ("version" -> "1.0")
      ctx.marshalTo(HttpEntity(contentType,
        compact(render("collection" -> value.foldLeft(json){ (obj, field) => obj ~~ field })))
        )
    }

//  implicit val searchResponseMarshaller: Marshaller[SearchResponse] =
//    Marshaller.of[SearchResponse](`application/vnd.collection+json`) { (res, contentType, ctx) =>
//      val items = parse(s"$res") \\ "item" \ "item"
//      ctx.marshalTo(HttpEntity(contentType,
//        compact(render("collection" -> ("version" -> "1.0") ~~ ("items" -> items))))
//      )
//    }
}
