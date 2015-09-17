package frontend.storedQuery

import frontend.storedQuery.postRequest._
import frontend.{CollectionJsonSupport, CorsSupport}
import spray.routing._
import spray.http.StatusCodes._

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
        }
      }
    }
  }
}
