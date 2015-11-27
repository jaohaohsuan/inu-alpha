package frontend.storedFilter

import elastic.ImplicitConversions._
import es.indices.logs
import frontend.{CollectionJsonSupport, ImplicitHttpServiceLogging}
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import protocol.storedQuery.Terminology._
import spray.http.StatusCodes._
import spray.routing._

import scala.collection.JavaConversions._
import scalaz.OptionT._
import scalaz.Scalaz._

trait StoredFilterRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging {

  implicit def client: org.elasticsearch.client.Client

  def termLevelQuerySyntax(dataType: String)(query: String): List[JValue] = {
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

    ("name" -> "occurrence") ~~ ("value" -> "must") :: values
  }

  def property(typ: String, field: String): Directive1[(JObject, List[JValue])] = onSuccess(
    logs.getTemplate.asFuture.map(_.getIndexTemplates.headOption).filter(_.isDefined).map(_.get.mappings())
    .map { x =>
      val mapping = parse(s"${x.get(typ)}") \ typ

      mapping \ "_meta" \ "properties" \ field \ "queries" match {
        case JArray(queries) =>
          (mapping \ "properties" \ field \ "type") match {
            case JString(str) => (("type" -> str) ~~ ("field" -> field), queries)
            case _ => (JObject(), List.empty)
          }
        case _ => (JObject(), List.empty)
      }
    })

  def fetchTypes: Directive1[List[String]] = onSuccess((for {
    template <- optionT(logs.getTemplate.asFuture.map(_.getIndexTemplates.headOption))
  } yield template.mappings.map(_.key).toList).run).flatMap {
    case Some(types) => provide(types)
    case _ => reject()
  }

  lazy val `_filter/`: Route =
    get {
      pathPrefix("_filter") {
          pathEnd {
            fetchTypes { types =>
              requestUri { uri =>
                collection { json =>
                  complete(OK, json ~~
                    ("links" -> types.map { x =>
                      ("name" -> x) ~~
                        ("href" -> s"${uri.withPath(uri.path / x)}")
                    }))
                }
              }
            }
          } ~
          pathPrefix(Segment){ typ =>
            pathEnd { implicit ctx =>
              actorRefFactory.actorOf(QueryRequest.props)
            } ~
            pathPrefix(Segment) { id =>
              pathEnd { implicit ctx =>
                actorRefFactory.actorOf(GetItemRequest.props(typ, id))
              } ~
              path(OccurrenceRegex) { occurrence =>  implicit ctx =>
                actorRefFactory.actorOf(GetItemClausesRequest.props(typ, id, occurrence))
              } ~
              pathPrefix(Segment) { field =>
                property(typ, field) { case (prop, queries) =>
                  pathEnd {
                    item(prop) { json =>
                      requestUri { uri =>
                        complete(OK, json.mapField {
                          case ("items", JArray(x :: Nil)) => "items" -> JArray(x.transformField { case f @ ("links", _) => ("links", JNothing) } :: Nil)
                          case ("links", JNothing) => "links" -> queries.collect { case JString(q) => ("rel" -> "option") ~~ ("href" -> s"${uri.withPath(uri.path / q)}") }
                          case ("template", _) => "template" -> JNothing
                          case x => x
                        })
                      }
                    }
                  } ~
                  path(Segment) { query =>
                    collection { json =>
                      complete(OK, json.mapField {
                        case ("template", _) => "template" -> ("data" -> termLevelQuerySyntax((prop \ "type").extract[String])(query))
                        case x => x
                      })
                    }
                  }
                }
              }
            }
          }
      }
    } ~
    post {
      pathPrefix("_filter") {
        pathPrefix(Segment) { typ =>
          pathEnd { implicit ctx =>
            actorRefFactory.actorOf(NewFilterRequest.props(typ))
          } ~
          pathPrefix(Segment / Segment) { (filterId, field) => implicit ctx =>
            actorRefFactory.actorOf(PostFieldQueryRequest.props(typ, filterId, field))
          }
        }
      }
    }

}
