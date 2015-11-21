package frontend.mapping

import common.ImplicitLogging
import frontend.{ImplicitHttpServiceLogging, CollectionJsonSupport}
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData
import org.elasticsearch.common.collect.ImmutableOpenMap
import org.elasticsearch.common.compress.CompressedXContent
import org.json4s._
import org.json4s.JsonAST.JValue
import shapeless.HNil
import spray.http.Uri
import spray.routing.{Directive, Directive1, Route, HttpService}
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.{Try, Success}
import es.indices.logs
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import shapeless._
import spray.routing._
import Directives._

trait MappingRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging {

  implicit def client: org.elasticsearch.client.Client

  def getTemplate: Future[ImmutableOpenMap[String, CompressedXContent]] =
      logs.getTemplate.asFuture.map(_.getIndexTemplates.headOption).filter(_.isDefined).map(_.get.mappings())

  def template = onSuccess(getTemplate)
  def mapping(typ: String): Directive1[JValue] = onSuccess(getTemplate.map { x => parse(s"${x.get(typ)}") \ typ })

  def termLevelQuerySyntax(dataType: String = "string")(query: String): List[JValue] = {
    val sampleValue: JValue = dataType match {
      case "int" => JInt(0)
      case "long" => JInt(0)
      case "date" => JString("2015-12-31")
      case "string" => JString("word")
      case _ => JNull
    }

    val values =  query match {
      case "terms" => ("name" -> "value") ~~ ("array" -> List(sampleValue)) :: Nil
      case "term" => ("name" -> "value") ~~ ("value" -> sampleValue) :: Nil
      case "range" => ("name" -> "gte") ~~ ("value" -> sampleValue) :: ("name" -> "lte") ~~ ("value" -> sampleValue) :: Nil
      case _ => Nil
    }

    ("name" -> "query") ~~ ("value" -> query) :: values
  }

  lazy val `_mapping/`: Route =
    get {
      requestUri {  uri =>
        pathPrefix( "_mapping" ) {
          pathEnd {
            `collection+json` { json =>
              template { mappings =>
                complete(OK, json.mapField {
                  case ("items", _) => ("items" -> mappings.map( m =>
                    ("href" -> s"${uri}/${m.key}") ~~
                      ("data" -> List(("name" -> "type") ~~ ("value" -> s"${m.key}")))))
                  case x => x
                })
              }
            }
          } ~ pathPrefix( Segment ) { typ =>
                mapping(typ) { mapping =>
                    pathEnd {
                      `collection+json`{ json =>
                        val JsonAST.JObject(properties) = mapping \ "properties"
                        complete(OK, json.mapField {
                          case ("items", _) => ("items" -> properties.map {
                            case (field, detail) =>
                              ("href" -> s"$uri/$field") ~~
                                ("data" -> List(
                                  ("name" -> "field") ~~ ("value" -> s"$field"),
                                  ("name" -> "type") ~~ ("value" -> s"${(detail \\ "type").extract[String]}")
                                ))
                          })
                          case x => x
                        })
                      }
                    } ~ pathPrefix( Segment ) { field =>
                          pathEnd {
                            `collection+json` { json =>
                              val syntaxFields = termLevelQuerySyntax((mapping \ "properties" \ field \ "type").extract[String])(_)
                              val items = mapping \ "_meta" \ "properties" \ field \ "queries" match {
                                case JArray(xs) => xs.collect { case JString(q) => ("data" -> syntaxFields(q)): JObject }
                                case _ => Nil
                              }
                              complete(OK, json.mapField {
                                case ("items", _) => ("items" -> items)
                                case x => x
                              })
                            }
                          } ~
                          path( Segment) { query =>
                            complete(OK,query)
                          }
                    }
                  }
              }
        }
      }
    }
}
