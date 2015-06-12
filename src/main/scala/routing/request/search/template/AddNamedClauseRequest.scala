package routing.request.search.template

import java.util.UUID

import akka.actor.{ActorRef, Terminated, Props}
import akka.contrib.pattern.ClusterClient.SendToAll
import akka.contrib.pattern.ClusterSingletonProxy
import akka.util.Timeout
import routing.request.PerRequest
import spray.routing._
import domain.search.template.Graph._
import scala.concurrent.duration._
import worker.{ Master, Work }
import akka.pattern._
import spray.http.StatusCodes._
import domain.search.template.Graph.Routes
import domain.search.template._

  case class AddNamedClauseRequest(ctx: RequestContext, clusterClient: ActorRef, templateId: String, clauseTemplateId: String, occur: String) extends PerRequest {

    clusterClient ! SendToAll("/user/searchTemplateGraph/active", AddNamedClause(templateId, clauseTemplateId, occur))

    import context.dispatcher
    def scheduler = context.system.scheduler


    def processResult: Receive = {
      case DirectedCyclesNotAccepted =>
        response {
          complete(NotAcceptable)
        }
      case GraphInconsistency(error) =>
        response {
          complete(InternalServerError, error)
        }
      case PersistedAck(event) =>
        responseWithoutStopActor {
          complete(NoContent)
        }
        clusterClient ! SendToAll("/user/searchTemplateGraph/active", Get(templateId))
      case Routes(segments) =>
        scheduler.scheduleOnce(2.seconds, new Runnable {
          def run(): Unit = {
            context.watch(context.actorOf(Gathering.props(clusterClient,(templateId, (clauseTemplateId, occur)) :: segments), name = "gathering"))
          }
        })
      case Terminated(child) =>
        log.info(s"$child is terminated")
        context.stop(self)
    }
  }


