package frontend.storedFilter

import elastic.ImplicitConversions._
import es.indices.logs
import frontend.{CollectionJsonSupport, ImplicitHttpServiceLogging}
import org.elasticsearch.common.collect.ImmutableOpenMap
import org.elasticsearch.common.compress.CompressedXContent
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import protocol.elastics.boolQuery.OccurrenceRegex
import spray.http.StatusCodes._
import spray.routing._
import spray.routing.authentication.BasicAuth

import scala.collection.JavaConversions._
import scalaz.OptionT._
import scalaz.Scalaz._

trait StoredFilterRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging {

  implicit def client: org.elasticsearch.client.Client


  def properties(mapping: JValue) = {
    (for {
      JObject(props0) <- (mapping \ "_meta" \ "properties").toOption
      JObject(props1) <- (mapping \ "properties").toOption
    } yield props0.map(_._1).intersect(props1.map(_._1))) match {
      case Some(x) => x
      case None => Nil
    }
  }

 def fieldQueries(`type`: String, field: String): Directive1[JValue] = onSuccess( for {
   templates       <- logs.getTemplate
   template1       <- templates.getIndexTemplates.headOption.future(new Exception("template1 doesn't exist"))
   json            <- template1.mappings.find { _.key == `type` }.flatMap { x => (parse(x.value.string()) \ `type`).toOption }.future()
   queries <- (json \ "_meta" \ "properties" \ field \ "queries").toOption.future()
 } yield queries)

  def template: Directive1[ImmutableOpenMap[String, CompressedXContent]] = onSuccess(for {
    templates       <- logs.getTemplate
    template1       <- templates.getIndexTemplates.headOption.future(new Exception("template1 doesn't exist"))
  } yield template1.mappings)

  def fetchTypes: Directive1[List[String]] = onSuccess(
    (for {
      template <- optionT(logs.getTemplate.map(_.getIndexTemplates.headOption))
    } yield template.mappings.map(_.key).toList).run).flatMap {
        case Some(types) => provide(types)
        case _ => reject()
    }

  lazy val newRoute: Route = pathPrefix("_filter") {
    template { sources =>
      requestUri { uri =>
        pathEnd {
          collection { json =>
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
        } ~
        pathPrefix(sources.map(_.key.formatted("""^%s$""")).mkString("|").r){ source =>
          pathEnd {
            get { implicit ctx =>
              actorRefFactory.actorOf(QueryRequest.props)
            } ~
            post { implicit ctx =>
              actorRefFactory.actorOf(NewFilterRequest.props(source))
            }
          } ~
          pathPrefix(Segment) { id =>
            pathEnd {
              get { implicit ctx =>
                actorRefFactory.actorOf(GetItemRequest.props(source, id))
              } ~
              post {
                complete(Created)
              }
            } ~
            pathPrefix(properties(parse(sources.get(source).string()) \ source).map(_.formatted("""^%s$""")).mkString("|").r) { property =>
              complete(OK)
            }
          }
        }
      }
    }
  }

  lazy val postRoutes: Route = post {
    pathPrefix("_filter") {
      pathPrefix(Segment) { typ =>
        pathEnd { implicit ctx =>
          actorRefFactory.actorOf(NewFilterRequest.props(typ))
        } ~
        path(Segment) { filterId =>
          complete(Created)
        } ~
        pathPrefix(Segment / Segment) { (filterId, field) => implicit ctx =>
          actorRefFactory.actorOf(PostFieldQueryRequest.props(typ, filterId, field))
        }
      }
    }
  }

  def getItemField(typ: String): Route = pathPrefix(Segment) { field =>
    fieldQueries(typ, field) { case queries@JObject(xs) =>
      pathEnd {
        collection { json =>
          requestUri { uri =>
            complete(OK, json.mapField {
              case ("items", JArray(x :: Nil)) => "items" -> JArray(x.transformField { case f @ ("links", _) => ("links", JNothing) } :: Nil)
              case ("links", JNothing) =>
                "links" -> xs.map {
                case JField(q, _) => ("rel" -> "query") ~~ ("render" -> "option") ~~ ("href" -> s"${uri.withPath(uri.path / q)}") ~~ ("name" -> q)
              }
              case ("template", _) => "template" -> JNothing
              case x => x
            })
          }
        }
      } ~
        path(Segment) { query =>
          collection { json =>
            requestUri { uri =>
              complete(OK, json.mapField {
                case ("template", _) =>
                  "template" -> (queries \ query match {
                    case x: JObject => (("occurrence" -> "must") ~ x).asTemplate
                    case _ => JNothing
                  })
                //case ("queries", _) => "queries" -> JArray(("href" -> s"${uri.withPath(uri.path / "preview" )}") :: Nil)
                case x => x
              })
            }
          }
        }
    }
  }

  lazy val `_filter/`: Route =
    get {
      authenticate(BasicAuth("domain")) { username =>
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
            pathPrefix(Segment) { typ =>
              pathEnd { implicit ctx =>
                actorRefFactory.actorOf(QueryRequest.props)
              } ~
              pathPrefix(Segment) { id =>
                pathEnd { implicit ctx =>
                    actorRefFactory.actorOf(GetItemRequest.props(typ, id))
                } ~
                path(OccurrenceRegex) { occurrence => implicit ctx =>
                  actorRefFactory.actorOf(GetItemClausesRequest.props(typ, id, occurrence))
                } ~ getItemField(typ)
              }
            }
        }
      }
    } ~ postRoutes ~
    put {
      pathPrefix("_filter" / Segment / Segment ) { (typ, filterId) => implicit ctx =>
        actorRefFactory.actorOf(RenameRequest.props(filterId))
      }
    } ~
    delete {
      pathPrefix("_filter") {
        pathPrefix(Segment / Segment) { (typ, filterId) => implicit ctx =>
          actorRefFactory.actorOf(DeleteClauseRequest.props(typ, filterId))
        }
      }
    }
}
