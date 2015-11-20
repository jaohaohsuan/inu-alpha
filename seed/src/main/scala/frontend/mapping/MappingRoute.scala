package frontend.mapping

import common.ImplicitLogging
import frontend.{CollectionJsonSupport}
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData
import org.elasticsearch.common.collect.ImmutableOpenMap
import org.elasticsearch.common.compress.CompressedXContent
import spray.routing.{Directive1, Route, HttpService}
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Try, Success}
import es.indices.logs
import org.json4s._
import org.json4s.native.JsonMethods._

trait MappingRoute extends HttpService with CollectionJsonSupport with ImplicitLogging {

  implicit def client: org.elasticsearch.client.Client

  def getTemplate: Future[ImmutableOpenMap[String, CompressedXContent]] =
      logs.getTemplate.asFuture.map(_.getIndexTemplates.headOption).filter(_.isDefined).map(_.get.mappings())

  val template: Directive1[ImmutableOpenMap[String, CompressedXContent]] = onSuccess(getTemplate)
  def mapping(typ: String): Directive1[JValue] = onSuccess(getTemplate.map { x => parse(s"${x.get(typ)}") \ typ })

  lazy val `_mapping/`: Route =
    get {
      requestUri {  uri =>
        pathPrefix( "_mapping" ) {
          pathEnd {
            template { mappings => ctx =>

              val types = mappings.map(m =>
                s"""{
                   | "href" : "${uri}/${m.key}",
                   | "data" : [
                   |   { "name" : "type" , "value" : "${m.key}"}
                   | ]
                   |}""".stripMargin).mkString(",")

              ctx.complete(OK, s"""
                                  | {
                                  |   "collection" : {
                                  |     "version" : "1.0",
                                  |     "href" : "$uri",
                                  |     "items" : [ $types ]
                                  |   }
                                  | }
                                  | """.stripMargin)
            }
          } ~ pathPrefix( Segment ) { typ =>
                mapping(typ) { mapping =>
                    pathEnd { ctx =>
                      val JsonAST.JObject(xs) = mapping \ "properties"
                      val properties = xs.map {
                        case (field, detail) =>
                          s"""{
                             | "href" : "$uri/$field",
                             | "data" : [
                             |   { "name" : "field", "value" : "$field" },
                             |   { "name" : "type", "value" : "${(detail \\ "type").extract[String]}" }
                             | ]
                             |}""".stripMargin
                      }.mkString(",")

                      val content = s"""{
                                       | "collection" : {
                                       |   "version" : "1.0",
                                       |   "href" : "${uri}",
                                       |   "items" : [ $properties ]
                                       | }
                                       |}""".stripMargin

                      ctx.complete(OK, content)
                    } ~ pathPrefix( Segment ) { field =>
                          pathEnd {
                            val links = mapping \ "_meta" \ "properties" \ field \ "queries" match {
                              case JArray(xs) => xs.map{ case JString(s) =>
                                s
                              }.mkString(",")
                              case JNothing => ""
                            }
                            complete(OK, links)
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


//{ "rel" : "query", "name" : "$s", "render" : "option", "href": "$uri/$field/query/$s" }
