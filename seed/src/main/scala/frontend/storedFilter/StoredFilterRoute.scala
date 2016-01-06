package frontend.storedFilter

import akka.actor.{Props}
import domain.storedFilter.StoredFilterAggregateRoot.{DeleteItem, EmptyClauses, RemoveClause}
import frontend.{CollectionJsonSupport, ImplicitHttpServiceLogging}
import org.json4s.JsonDSL._
import org.json4s._
import protocol.elastics.boolQuery.OccurrenceRegex
import spray.http.StatusCodes._
import spray.routing._
import spray.routing.authentication.BasicAuth

import scala.collection.JavaConversions._

trait StoredFilterRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging with TemplateExtractor {

  implicit def client: org.elasticsearch.client.Client

  private implicit class Sender0(props: Props) {
    def send = actorRefFactory.actorOf(props)
  }

  lazy val `_filter`: Route = pathPrefix("_filter") {
    authenticate(BasicAuth("logs")) { usrCtx =>
      template { sources =>
        dataSources(usrCtx.username)(sources.keySet) { authorizedDs =>
          pathEnd {
            entry(authorizedDs)
          } ~
          pathPrefix(authorizedDs.map(_.formatted( """^%s$""")).mkString("|").r) { source =>
            pathEnd {
              //_filter/ami-l8k
              get { implicit ctx =>
                QueryRequest.props.send
              } ~
              post { implicit ctx => NewFilterRequest.props(source).send }
            } ~
            pathPrefix(Segment) { id => //_filter/ami-l8k/371005001
              pathEnd {
                get { implicit ctx =>
                  GetItemRequest.props(source, id).send
                } ~
                post { implicit ctx =>
                  NewFilterRequest.props(source, Some(id)).send
                } ~
                delete { implicit ctx =>
                  DeleteRequest.props(DeleteItem(id, source)).send
                }
              } ~
              path(OccurrenceRegex) { occur =>
                get { implicit ctx => //_filter/ami-l8k/371005001/must
                  GetItemClausesRequest.props(source, id, occur).send
                } ~
                delete { implicit ctx =>
                  DeleteRequest.props(EmptyClauses(id, source, occur)).send
                }
              } ~
              properties(sources(source)) { case (propertiesRegex, props) =>
                pathPrefix(propertiesRegex) { prop => //_filter/ami-l8k/371005001/recordTime
                  post { implicit ctx =>
                    PostFieldQueryRequest.props(source, id, prop).send
                  } ~
                  queries(props \ prop) { case (queriesRegex, jQueries@JObject(xs)) =>
                    pathEnd {
                      get {
                        propertyQueries(xs, (props \ prop \ "type").extract[String])
                      }
                    } ~
                    pathPrefix(queriesRegex) { query =>
                      pathEnd {
                        get {
                          queryTemplate(jQueries \ query) //_filter/ami-l8k/371005001/recordTime/terms
                        }
                      } ~
                      path(Segment) { clauseId =>
                        delete { implicit ctx =>
                          DeleteRequest.props(RemoveClause(id, source, clauseId)).send
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def entry(sources: Iterable[String]): Route = {
    collection { json =>
      requestUri { uri =>
        val body = json.transformField {
          case ("collection", child: JObject) =>
            "collection" -> child.transformField {
              case ("links", _) =>
                "links" -> sources.map { x =>
                  ("name" -> x) ~~ ("href" -> s"${uri.withPath(uri.path / x)}")
                }.toList
            }
        }
        complete(OK, body)
      }
    }
  }

  def propertyQueries(xs: List[JField], dataType: String): Route =
      item(Map("type" -> dataType)) { json =>
        requestUri { uri =>
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