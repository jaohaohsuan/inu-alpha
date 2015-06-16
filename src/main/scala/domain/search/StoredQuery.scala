package domain.search

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ShardRegion
import akka.persistence.{PersistentActor, RecoveryCompleted}


object StoredQuery {

  import StoredQueryCommandQueryProtocol.Command

  def props(): Props = Props[StoredQuery]

  val idExtractor: ShardRegion.IdExtractor = {
    case m: Command => (m.storedQueryId, m)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case m: Command => (math.abs(m.storedQueryId.hashCode) % 100).toString
  }

  val shardName: String = "StoredQuery"
}

class StoredQuery extends PersistentActor with ActorLogging {

  import StoredQueryCommandQueryProtocol._
  import StoredQueryState._

  var templateState = StoredQueryState.empty()

  override def receiveCommand: Receive = {

    case AddClauseCommand(templateId, clause) =>
      persist(ClauseAdded(clause.hashCode(), clause))(afterPersisted(templateId, sender(), _))

    case RemoveClauseCommand(templateId, clauseId) =>
      templateState.clauses.get(clauseId) match {
        case None =>
          sender() ! ClauseNotFoundAck
        case Some(clause) =>
          persist(ClauseRemoved(clauseId, clause))(afterPersisted(templateId, sender(), _))
      }
  }

  def afterPersisted(templateId: String, senderRef: ActorRef, event: DomainEvent) = {

    templateState = templateState.update(event)

    val ack = event match {
      case ClauseAdded(id, _) =>
        ClauseAddedAck(templateId, templateState.version, id)
      case ClauseRemoved(id, clause) =>
        ClauseRemovedAck(templateId, templateState.version , id, clause)
    }

    senderRef ! ack
  }

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  override def receiveRecover: Receive = {
    case event: DomainEvent =>
      templateState = templateState.update(event)
    case RecoveryCompleted =>
      log.info(s"recovered state: ${templateState.clauses}")
  }

  override def unhandled(message: Any): Unit = message match {
    //case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
    case _              =>
      log.info(s"unhandled message: $message")
      super.unhandled(message)
  }
}
