package frontend.storedQuery

import frontend.storedQuery.postRequest._
import frontend.{CollectionJsonSupport, CorsSupport}
import spray.routing._

trait StoredQueryRoute extends HttpService with CorsSupport with CollectionJsonSupport {

  lazy val `_query/template/`: Route =
  cors {
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
