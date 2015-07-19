package routing

import spray.routing.HttpService
import util.{CorsSupport, CollectionJsonSupport}
import akka.actor.{ActorRef, Props}
import net.hamnaberg.json.collection._
import spray.routing._
import spray.http.StatusCodes._

trait SearchPreviewRoute extends HttpService with CollectionJsonSupport with CorsSupport {

  def `_search/preview` = cors {
    get {
      path("_search" / "preview") {
        complete(OK)
      }
    }
  }
}
