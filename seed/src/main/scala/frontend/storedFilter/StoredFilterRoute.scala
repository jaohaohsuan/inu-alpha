package frontend.storedFilter

import frontend.{CollectionJsonSupport, ImplicitHttpServiceLogging}
import org.json4s.JsonDSL._
import org.json4s._
import protocol.elastics.boolQuery.OccurrenceRegex
import spray.http.StatusCodes._
import spray.routing._

import scala.collection.JavaConversions._

trait StoredFilterRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging with TemplateExtractor {

  implicit def client: org.elasticsearch.client.Client

  lazy val newRoute: Route = pathPrefix("_filter") {
    template { sources =>
        pathEnd {
          collection { json =>
            requestUri { uri =>
              val body = json.transformField {
                case ("collection", child: JObject) =>
                  "collection" -> child.transformField {
                    case ("links", _) =>
                      "links" -> sources.map { x =>
                        ("name" -> x.key) ~~ ("href" -> s"${uri.withPath(uri.path / x.key)}")
                      }
                  }
              }
              complete(OK, body)
            }
          }
        } ~
        pathPrefix(sources.map(_.key.formatted("""^%s$""")).mkString("|").r) { source =>
          pathEnd { //_filter/ami-l8k
            get { implicit ctx =>
              actorRefFactory.actorOf(QueryRequest.props)
            } ~
            post { implicit ctx =>
              actorRefFactory.actorOf(NewFilterRequest.props(source))
            }
          } ~
          pathPrefix(Segment) { id =>
            delete { implicit ctx =>
              actorRefFactory.actorOf(DeleteClauseRequest.props(source, id))
            } ~
            pathEnd { //_filter/ami-l8k/371005001
              get { implicit ctx =>
                actorRefFactory.actorOf(GetItemRequest.props(source, id))
              } ~
              post {
                complete(Created)
              }
            } ~
            path(OccurrenceRegex) { occur =>
              get { implicit ctx => //_filter/ami-l8k/371005001/must
                actorRefFactory.actorOf(GetItemClausesRequest.props(source, id, occur))
              }
            } ~
            properties(source)(sources) { case (propertiesRegex, props) =>
              pathPrefix(propertiesRegex) { prop => //_filter/ami-l8k/371005001/recordTime
                post { implicit ctx =>
                  actorRefFactory.actorOf(PostFieldQueryRequest.props(source, id, prop))
                } ~
                get {
                  queries(props \ prop) { case (queriesRegex, jQueries@JObject(xs)) =>
                    pathEnd {
                      propertyQueries(xs, (props \ prop \ "type").extract[String])
                    } ~
                    path(queriesRegex) { query =>
                      queryTemplate(jQueries \ query) //_filter/ami-l8k/371005001/recordTime/terms
                    }
                  }
                }
              }
            }
          }
        }

    }
  }


  def propertyQueries(xs: List[JField], dataType: String): Route =
      item(Map("type" -> dataType)) { json =>
        requestUri { uri =>
          dataType.logInfo()
          complete(OK, json.mapField {
            case ("items", JArray(x :: Nil)) => "items" -> JArray(x.transformField { case ("links", _) => ("links", JNothing) } :: Nil)
            case ("links", JNothing) => "links" -> xs.map { case JField(q, _) =>
              ("rel" -> "query") ~~
              ("render" -> "option") ~~
              ("href" -> s"${uri.withPath(uri.path / q)}") ~~
              ("name" -> q)
            }
            case ("template", _) => "template" -> JNothing
            case x => x
          })
        }
      }

    def queryTemplate(query: JValue): Route =
      collection { json =>
        val occ = ("name" -> "occurrence") ~~ ("value" -> "must")
        val field = "render" -> "field"
        val jTemplate = query match {
          case x: JObject => x.asTemplate.transformField {
            case (data@"data", JArray(xs)) => data -> xs.collect { case o: JObject => field ~~ o }.+:(occ)
          }
          case _ => JNothing
        }
        complete(OK, json.mapField {
          case (template@"template", _) => template -> jTemplate
          case x => x
        })
      }
}