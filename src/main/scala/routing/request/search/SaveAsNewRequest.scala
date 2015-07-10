package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{SendToAll}
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._

case class SaveAsNewRequest(ctx: RequestContext, clusterClient: ActorRef, referredId: Option[String], title: String) extends PerRequest {

  import domain.StoredQueryAggregateRoot._

  clusterClient ! SendToAll(storedQueryAggregateRootSingleton, CreateNewStoredQuery(title, referredId))

  def processResult = {
    case ItemCreated(StoredQuery(id, title, _, _), _)  =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"$href/$id".replaceAll("""/(\d|temporary)+(?=/\d)""", ""))){
            complete(Created)
          }
        }
      }
  }
}

case class UpdateRequest(ctx: RequestContext, clusterClient: ActorRef, storedQueryId: String,
                          title: String, tags: Option[String]) extends PerRequest {

  import domain.StoredQueryAggregateRoot._

  clusterClient ! SendToAll(storedQueryAggregateRootSingleton, UpdateStoredQuery(storedQueryId, title, tags))
  def processResult = {
    case UpdatedAck =>
      response {
        complete(OK)
      }

  }
}
