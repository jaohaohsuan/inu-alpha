package frontend.storedFilter

import frontend.{ImplicitHttpServiceLogging, CollectionJsonSupport}
import spray.routing._

trait StoredFilterRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging {

  implicit def client: org.elasticsearch.client.Client
  implicit private val executionContext = actorRefFactory.dispatcher

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
        complete(spray.http.StatusCodes.OK)
      }
    }
  }

}
