package domain.search.template

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence.{RecoveryCompleted, PersistentActor}

import scala.concurrent.duration._
import scala.util.Try

object Graph {

  import GraphState._

  def props: Props = Props[Graph]

  case class AddEdgeCommand(templateId: String, clauseTemplateId: String, occur: String)
  case class Get(templateId: String)
  case object DirectedCyclesNotAccepted
  case class Inconsistency(error: String)
  case class Routes(segments: List[(String, (String, String))])
  //case object TraverseComplete

  case class PersistedAck(event: DomainEvent)
}

class Graph extends PersistentActor with ActorLogging {

  import Graph._
  import GraphState._

  import context.dispatcher

  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => s"$role-searchTemplateGraph"
    case None => "searchTemplateGraph"
  }

  ClusterReceptionistExtension(context.system).registerService(self)

  def scheduler = context.system.scheduler

  private var graphState = GraphState.empty[String]()
  private var templateClauses = Map[(String, String), (String, String)]()

  override def receiveCommand = {
    case AddEdgeCommand(templateId, clauseTemplateId, occur) => {

      val edge = (templateId, clauseTemplateId)

      if (!graphState.isAccepted(edge))
        sender() ! DirectedCyclesNotAccepted

      persist(EdgeAdded(edge, (clauseTemplateId, occur))) { event =>
        updateState(event)
        sender() ! PersistedAck(event)
      }
    }
    case Get(templateId) => {
      startRouting(sender(), templateId, templateClauses)
    }
  }

  def updateState(event: DomainEvent): Unit = {
    event match {
      case event @ EdgeAdded(edge: (String, String), clause: (String, String)) =>
        graphState = graphState.update(edge)
        templateClauses = templateClauses + (edge -> clause)
    }
  }

  def startRouting(senderRef: ActorRef, start: String, clauses: Map[(String, String), (String, String)]) = {
    log.info(s"collecting path")
    val trySegments = Try(graphState.getUpdateRoutes(start).flatMap { _.map {
      case (provider, consumer) => (consumer, templateClauses get Tuple2(consumer, provider) get)
    }}.toList)

    trySegments.map { segments =>
      senderRef ! Routes(segments)
      log.info(s"Send $segments to ${sender()}")
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
      log.info("Replayed {}", event.getClass.getSimpleName)
    case RecoveryCompleted =>
      log.info(s"GraphState: {}", graphState.preds)
      log.info(s"Clauses: {}", templateClauses)
  }
}
