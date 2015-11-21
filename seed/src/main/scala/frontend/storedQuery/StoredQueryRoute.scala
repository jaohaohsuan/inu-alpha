package frontend.storedQuery

import frontend.{ImplicitHttpServiceLogging, CollectionJsonSupport}
import frontend.storedQuery.deleteRequest.{RemoveClauseRequest, ResetOccurrenceRequest}
import frontend.storedQuery.getRequest._
import frontend.storedQuery.postRequest._
import frontend.storedQuery.putRequest.UpdateStoredQueryItemRequest
import org.json4s
import protocol.storedQuery.Exchange._
import protocol.storedQuery.Terminology._
import spray.httpx.unmarshalling._
import spray.routing._

trait StoredQueryRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging {

  implicit def client: org.elasticsearch.client.Client

  def clausePath[T: Monoid](name: String)(implicit storedQueryId: String, um: FromRequestUnmarshaller[T]): Route =
    path(name) {
      entity(as[T]) { e => implicit ctx: RequestContext =>
        actorRefFactory.actorOf(AddClauseRequest.props(e))
      }
    }

  lazy val `_query/template/`: Route =
    get {
      path("_query" / "template") {
        parameters('q.?, 'tags.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (q, tags, size, from) =>
          requestUri { uri => implicit ctx =>
            val b = new CollectionJsonBuilder {
              def body(hits: Iterable[json4s.JValue], tags: String, pagination: Seq[String]): String = {
                val prefix = uri.withQuery(Map.empty[String, String])
                val items = itemsMap(hits).flatMap(_.map { case (id, data) => s"""{ "href" : "$prefix/$id", "data" : $data }""" }).mkString(",")
                val links = pagination.+:(s"""{ "href" : "$prefix/temporary", "rel" : "edit" }""").filter(_.trim.nonEmpty).mkString(",")

                s"""{
                   | "collection" : {
                   |   "version" : "1.0",
                   |   "href" : "$prefix",
                   |
                   |   "links" : [ $links ],
                   |
                   |   "queries" : [ {
                   |      "href" : "$prefix",
                   |      "rel" : "search",
                   |      "data" : [
                   |        { "name" : "q", "prompt" : "search title or any terms" },
                   |        { "name" : "tags", "prompt" : "${tags}" },
                   |        { "name" : "size", "prompt" : "size of displayed items" },
                   |        { "name" : "from", "prompt" : "items display from" }
                   |      ]
                   |    } ],
                   |
                   |   "items" : [$items ],
                   |
                   |   "template" : {
                   |      "data" : [
                   |        {"name":"title","value":""},
                   |        {"name":"tags","value":""}
                   |      ]
                   |   }
                   | }
                   |}""".stripMargin
              }
            }
            actorRefFactory.actorOf(QueryStoredQueryRequest.props(b, q, tags, size, from))
          }
        }
      } ~
      pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
        pathEnd { implicit ctx =>
          actorRefFactory.actorOf(GetStoredQueryRequest.props)
        } ~
        path(OccurrenceRegex ) { occurrence => implicit ctx =>
          actorRefFactory.actorOf(GetStoredQueryDetailRequest.props(occurrence))
        } ~
        path(BoolQueryClauseRegex ) { clause => implicit ctx =>
          actorRefFactory.actorOf(GetClauseTemplateRequest.props) ! clause
        } ~
        pathPrefix("preview") {
          pathEnd {
            parameter('size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (size, from) => implicit ctx =>
              actorRefFactory.actorOf(Preview.props(size, from))
            }
          } ~
          path("status") { implicit ctx =>
            actorRefFactory.actorOf(Status.props)
          }
        }
      }
    } ~
    post {
      pathPrefix("_query" / "template") {
        pathEnd {
          entity(as[NewTemplate]) { implicit entity => implicit ctx =>
            actorRefFactory.actorOf(NewTemplateRequest.props)
          }
        } ~
        pathPrefix(Segment) { implicit storedQueryId =>
          clausePath[NamedClause]("named") ~
          clausePath[MatchClause]("match") ~
          clausePath[SpanNearClause]("near") ~
          pathEnd {
            entity(as[NewTemplate]) { implicit entity => implicit ctx => implicit val referredId = Some(storedQueryId)
              actorRefFactory.actorOf(NewTemplateRequest.props)
            }
          }
        }
      }
    } ~
    put {
      pathPrefix("_query" / "template") {
        path(Segment) { implicit  storedQueryId =>
          entity(as[StoredQueryItem]) { entity => implicit ctx =>
            actorRefFactory.actorOf(UpdateStoredQueryItemRequest.prop(entity))
          }
        }
      }
    } ~
    delete {
      pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
        path( BoolQueryClauseRegex / IntNumber) { (clauseType, clauseId) => implicit ctx =>
          actorRefFactory.actorOf(RemoveClauseRequest.props(clauseId))
        } ~
        path( OccurrenceRegex ) { occurrence => implicit ctx =>
          actorRefFactory.actorOf(ResetOccurrenceRequest.props(occurrence))
        }
      }
    }

}
