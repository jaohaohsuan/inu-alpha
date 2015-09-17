package frontend.storedQuery

import akka.actor.Props
import frontend.storedQuery.deleteRequest.{ResetOccurrenceRequest, RemoveClauseRequest}
import frontend.storedQuery.postRequest._
import frontend.{CollectionJsonSupport, CorsSupport}
import spray.routing._
import spray.http.StatusCodes._
import protocol.storedQuery.Terminology._

trait StoredQueryRoute extends HttpService with CorsSupport with CollectionJsonSupport {

  lazy val `_query/template/`: Route =
  cors {
    get {
      path("_query" / "template" / "search") {
        parameters('q.?, 'tags.? ) { (q, tags) => implicit ctx =>
          actorRefFactory.actorOf(QueryStoredQueryRequest.props(q, tags))
        }
      } ~
      pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
        pathEnd { implicit ctx =>
          actorRefFactory.actorOf(GetStoredQueryRequest.props)
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

          import AddClauseRequest._

          //path("named") { entity(as[NamedClause])    { implicit e => implicit ctx => handle(props) } } ~
          path("match") {
            entity(as[MatchClause]) { e => implicit ctx =>
              actorRefFactory.actorOf(AddClauseRequest.props(e))
            }
          } ~
          //path("near")  { entity(as[SpanNearClause]) { implicit e => implicit ctx => handle(props) } }
          pathEnd {
            //save as new
            entity(as[NewTemplate]) { implicit entity => implicit ctx =>
              implicit val referredId = Some(storedQueryId)
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
