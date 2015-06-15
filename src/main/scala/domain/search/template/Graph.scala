package domain.search.template

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence.{RecoveryCompleted, PersistentActor}

import scala.util.Try

object Graph {

  def props: Props = Props[Graph]

  case class Edge(consumer: String, provider: String)

  case class AddEdgeCommand(edge: Edge, occur: String)
  case class RemoveEdgeCommand(edge: Edge, occur: String)
  case class Get(templateId: String)
  case object DirectedCyclesNotAccepted
  case class Inconsistency(error: String)
  case class Routes(segments: List[(String, (String, String))])

  trait DomainEvent
  case class EdgeAdded(edge: Edge, clause: (String, String)) extends  DomainEvent
  case class EdgeRemoved(edge: Edge, clause: (String, String)) extends  DomainEvent

  case class PersistedAck(event: DomainEvent)
}

class Graph extends PersistentActor with ActorLogging {

  import Graph._

  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => s"$role-searchTemplateGraph"
    case None => "searchTemplateGraph"
  }

  ClusterReceptionistExtension(context.system).registerService(self)

  def scheduler = context.system.scheduler

  private var graphState = GraphState.empty()
  private var templateClauses = Map[Edge, (String, String)]()

  override def receiveCommand = {
    case AddEdgeCommand(edge, occur) => {

      if (graphState.isAccepted(edge))
        persist(EdgeAdded(edge, (edge.provider, occur)))(afterPersisted(sender(), _))
      else
        sender() ! DirectedCyclesNotAccepted
    }
    case RemoveEdgeCommand(edge, occur) =>
      persist(EdgeRemoved(edge, (edge.provider, occur)))(afterPersisted(sender(), _))

    case Get(templateId) => {
      sendingRouteSegments(sender(), templateId, templateClauses)
    }
  }

  def sendingRouteSegments(senderRef: ActorRef, start: String, clauses: Map[Edge, (String, String)]) = {
    val trySegments = Try(graphState.getUpdateRoutes(start).flatMap { _.map {
      case (provider, consumer) => (consumer, templateClauses get Edge(consumer, provider) get)
    }}.toList)

    trySegments.map { segments =>
      senderRef ! Routes(segments)
      log.info(s"Start routing with $segments")
    }.recover {
      case e =>
        log.info(s"Inconsistency: ${e.getMessage}")
        senderRef ! Inconsistency(e.getMessage)
    }
  }

  override def receiveRecover: Receive = {
    case event: DomainEvent =>
      // only update current state by applying the event, no side effects
      updateState(event)
      //log.info("Replayed {}", event.getClass.getSimpleName)
    case RecoveryCompleted =>
      log.info(s"GraphState: {}", graphState.edges)
      log.info(s"Clauses: {}", templateClauses)
  }

  def afterPersisted(senderRef: ActorRef, event: DomainEvent): Unit = {
    updateState(event)
    senderRef ! PersistedAck(event)
  }

  def updateState(event: DomainEvent): Unit = {
    event match {
      case event @ EdgeAdded(edge: Edge, clause) =>
        graphState = graphState.update(edge)
        templateClauses = templateClauses + (edge -> clause)

      case event @ EdgeRemoved(edge: Edge, _) =>
        graphState = graphState.remove(edge)
        templateClauses = templateClauses - edge
    }
  }
}
