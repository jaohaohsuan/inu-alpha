package frontend.storedQuery

import frontend.storedQuery.deleteRequest.{RemoveClauseRequest, ResetOccurrenceRequest}
import frontend.storedQuery.getRequest._
import frontend.storedQuery.postRequest._
import frontend.{CollectionJsonSupport, CorsSupport}
import org.json4s._
import protocol.storedQuery.Exchange._
import protocol.storedQuery.Terminology._

import spray.httpx.unmarshalling._
import spray.routing._
import spray.http.StatusCodes._

trait StoredQueryRoute extends HttpService with CorsSupport with CollectionJsonSupport {

   def clause[T: Monoid](name: String)(implicit storedQueryId: String, um: FromRequestUnmarshaller[T]): Route =
    path(name) {
      entity(as[T]) { e => implicit ctx: RequestContext =>
        actorRefFactory.actorOf(AddClauseRequest.props(e))
      }
    }

  lazy val `_query/template/`: Route =
  cors {
    get {
      path("_query" / "template") {
        parameters('q.?, 'tags.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (q, tags, size, from) => implicit ctx =>
          actorRefFactory.actorOf(QueryStoredQueryRequest.props(q, tags, size, from))
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
        path("preview") { implicit ctx =>
          actorRefFactory.actorOf(Preview.props)
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
          clause[NamedClause]("named") ~
          clause[MatchClause]("match") ~
          clause[SpanNearClause]("near") ~
          pathEnd {
            entity(as[NewTemplate]) { implicit entity => implicit ctx => implicit val referredId = Some(storedQueryId)
              actorRefFactory.actorOf(NewTemplateRequest.props)
            }
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
}
