package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{SendToAll}
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._

case class SaveAsNewRequest(ctx: RequestContext, clusterClient: ActorRef, referredId: String, title: String) extends PerRequest {

  import domain.StoredQueryAggregateRoot._

  clusterClient ! SendToAll(storedQueryAggregateRootSingleton, CreateNewStoredQuery(title, referredId))

  def processResult = {
    case ItemCreated(StoredQuery(id, title, _), _)  =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"${href.resolve(id)}")){
            complete(Created)
          }
        }
      }
  }
}
