package domain

import java.util.UUID

import akka.actor.{Props, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.persistence.PersistentActor

import scala.concurrent.duration._

import scala.util.Try

object QueryTemplateGraph {


  def props: Props = Props[QueryTemplateGraph]

  case object Ok
  case object NotOk

  case class PropagatingRequest(routes: Set[Set[(UUID, UUID)]])


  case class AddNamedClause(edge: (UUID, UUID))

  case class NamedClauseAdded(edge: (UUID, UUID))

  case object Ack


  object GraphState {
    def empty[A]() = GraphState[A](Map.empty[A, Set[A]])
  }

  case class GraphState[A] private ( private val preds: Map[A, Set[A]]) {

    import algorithm.TopologicalSort._

    def isAccepted(edge: (A, A)) = {
      Try(sort(append(preds, edge))).isSuccess
    }

    def getUpdateRoutes(start: A) = collectPaths(start)(preds)

    def update(edge: (A, A)): GraphState[A] = {
      copy(preds = append(preds, edge))
    }
  }
}

class QueryTemplateGraph extends PersistentActor with ActorLogging {
  import QueryTemplateGraph._
  import context.dispatcher

  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => s"$role-queryTemplateGraph"
    case None => "queryTemplateGraph"
  }

  def scheduler = context.system.scheduler

  private var graphState = GraphState.empty[UUID]()

  override def receiveRecover: Receive = {
    case NamedClauseAdded(edge) =>
      // only update current state by applying the event, no side effects
      graphState = graphState.update(edge)
      log.info("Replayed {}", edge.getClass.getSimpleName)
    case _ =>
  }

  override def receiveCommand = {
    case  AddNamedClause(edge @ (consumer, _)) =>
      if(graphState.isAccepted(edge)) {

        val job = PropagatingRequest(graphState.getUpdateRoutes(start = consumer))
        context.become(propagating(sender(), NamedClauseAdded(edge), job), discardOld = false)
        sender() ! job
      }
  }

  def propagating(ref: ActorRef, event: Any, job: Any): Receive = {
    case Ok =>
      persist(event) { _ =>
        context.unbecome()
        ref ! Ack
      }
    case NotOk =>
      log.info("Work not accepted, retry after a while")
      scheduler.scheduleOnce(3.seconds, ref, job)
  }
}
