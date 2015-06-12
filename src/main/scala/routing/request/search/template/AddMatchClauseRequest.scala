package routing.request.search.template


import akka.actor.{Terminated, ActorRef}
import akka.contrib.pattern.ClusterClient.{SendToAll, Send}
import akka.contrib.pattern.ClusterSharding
import domain.search.template.Graph.{Routes, GraphInconsistency, Get}
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._
import domain.search.template._
import CommandQueryProtocol._
import scala.concurrent.duration._


case class AddMatchClauseRequest(ctx: RequestContext, clusterClient: ActorRef,
                                 templateId: String,
                                 query: String,
                                 operator: String,
                                 occur: String) extends PerRequest {

  import context.dispatcher

  def scheduler = context.system.scheduler

  //val searchTemplateRegion = ClusterSharding(context.system).shardRegion(Template.shardName)

  clusterClient ! Send(s"/user/sharding/${domain.search.template.Template.shardName}", AddClauseCommand(templateId, MatchClause(query, operator, occur)), localAffinity = true)

  //searchTemplateRegion ! AddClauseCommand(templateId, MatchClause(query, operator, occur))

  def processResult = {
    case ack@ClauseAddedAck(_, clauseId) =>
      responseWithoutStopActor {
        URI { href =>
          respondWithHeader(RawHeader("Location", href.resolve(s"match/${ack.clauseId}").toString)) {
            complete(Accepted)
          }
        }
      }
      context.become(UpdatingGraph(ack))
      clusterClient ! SendToAll(s"/user/searchTemplateGraph/active", Get(templateId))
  }

  def UpdatingGraph(ack: ClauseAddedAck): Receive = {
    case GraphInconsistency(error) =>
      response {
        complete(InternalServerError, error)
      }
    case Routes(segments) =>
      scheduler.scheduleOnce(2.seconds, new Runnable {
        def run(): Unit = {
          context.watch(context.actorOf(Gathering.props(clusterClient, segments), name = "gathering"))
        }
      })
    case Terminated(child) =>
      log.info(s"$child is terminated")
      context.stop(self)
  }
}
