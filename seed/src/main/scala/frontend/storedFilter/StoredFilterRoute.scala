package frontend.storedFilter

import frontend.{CollectionJsonSupport, ImplicitHttpServiceLogging}
import spray.routing._

trait StoredFilterRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging {

  implicit def client: org.elasticsearch.client.Client


  lazy val `_filter/`: Route = {
    path("_filter") {
      post { implicit ctx =>
        actorRefFactory.actorOf(NewFilterRequest.props)
      } ~
      path( Segment ) { id =>
        put { ctx =>

        }
      } ~
      get {
        `collection+json` { json =>
          complete(spray.http.StatusCodes.OK, json)
        }
      }
    }
  }

}
