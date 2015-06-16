package domain.search

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence.{RecoveryCompleted, PersistentActor}

object DependencyGraph {

  type StoredQueryId = String

  def props: Props = Props[DependencyGraph]

  case class Edge(consumer: StoredQueryId, provider: StoredQueryId)

  case class AddOccurredEdgeCommand(edge: Edge, occurrence: String)
  case class RemoveOccurredEdgeCommand(edge: Edge, occurrence: String)

  case class PersistedAck(event: DomainEvent)
  
  case class ConsumerChainsQuery(from: StoredQueryId)
  case object CycleInDirectedGraphError
  case class ConsumerChainsResponse(chainLinks: Seq[ChainLink])

  case class ChainLink(consumer: StoredQueryId, provider: StoredQueryId, occurrence: String)

  // 1 2 3 chain
  // 1 2 4 chain
  // [1 2 3] [1 2 4] chains
  // [1 2] [2 3] [1 2] [2 4] chainLinks

  // [456 789] ....[xxx 456]

  trait DomainEvent
  case class OccurredEdgeAdded(edge: Edge, occurrence: String) extends  DomainEvent
  case class OccurredEdgeRemoved(edge: Edge, occurrence: String) extends  DomainEvent
}

class DependencyGraph extends PersistentActor with ActorLogging {

  import DependencyGraph._

  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => s"$role-searchTemplateGraph"
    case None => "searchTemplateGraph"
  }

  ClusterReceptionistExtension(context.system).registerService(self)

  private var state = DependencyGraphState.empty()

  override def receiveCommand = {

    case AddOccurredEdgeCommand(edge, occur) => {
      if (state.isAcyclic(edge))
        persist(OccurredEdgeAdded(edge, occur))(afterPersisted(sender(), _))
      else
        sender() ! CycleInDirectedGraphError
    }

    case RemoveOccurredEdgeCommand(edge, occur) =>
      persist(OccurredEdgeRemoved(edge, occur))(afterPersisted(sender(), _))

    case ConsumerChainsQuery(from) => {
      state.tryConvertToExecutableSeq(from).map {
        sender() ! ConsumerChainsResponse(_)
      }.recover {
        case e => log.error(s"${e.getMessage}")
      }
    }
  }

  override def receiveRecover: Receive = {
    case event: DomainEvent =>
      state = state.update(event)
    case RecoveryCompleted =>
      log.info(s"Recovered DependencyGraph occurred edges: {}", state.occurredEdges)
  }

  def afterPersisted(senderRef: ActorRef, event: DomainEvent): Unit = {
    state = state.update(event)
    senderRef ! PersistedAck(event)
  }
}
