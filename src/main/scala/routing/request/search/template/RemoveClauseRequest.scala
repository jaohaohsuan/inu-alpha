package routing.request.search.template

import akka.actor.{Terminated, ActorRef}
import akka.contrib.pattern.ClusterClient.{SendToAll, Send}
import domain.search.template.Gathering
import domain.search.template.Graph.{Routes, Inconsistency, Get}
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing.RequestContext
import domain.search.template.CommandQueryProtocol._
import scala.concurrent.duration._

case class RemoveClauseRequest(ctx: RequestContext, clusterClient: ActorRef,
                                templateId: String,
                                clauseType: String,
                                clauseId: Int) extends PerRequest {

  clusterClient ! Send(templateRegion, RemoveClauseCommand(templateId, clauseId), localAffinity = true)

  def processResult: Receive = {

    case ClauseRemovedAck(_, clauseId, clause) =>
      //startGathering(s"$clauseType/$clauseId")
  }
}
