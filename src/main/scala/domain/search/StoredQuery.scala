package domain.search

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.contrib.pattern.{DistributedPubSubExtension, DistributedPubSubMediator, ShardRegion}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import domain.search.StoredQuery.StoredQueryItem


object StoredQuery {

  import StoredQueryCommandQueryProtocol.Command

  case class StoredQueryItem(storedQueryId: String, name: String)

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

  val mediator = DistributedPubSubExtension(context.system).mediator

  var templateState = StoredQueryState.empty()

  override def receiveCommand: Receive = {

    case NameCommand(storedQueryId, name) =>
      persist(Named(name))(afterPersisted(storedQueryId, sender(), _))

    case AddClauseCommand(storedQueryId, clause) =>
      persist(ClauseAdded(clause.hashCode(), clause))(afterPersisted(storedQueryId, sender(), _))

    case RemoveClauseCommand(storedQueryId, clauseId) =>
      templateState.clauses.get(clauseId) match {
        case None =>
          sender() ! ClauseNotFoundAck
        case Some(clause) =>
          persist(ClauseRemoved(clauseId, clause))(afterPersisted(storedQueryId, sender(), _))
      }
  }

  def afterPersisted(storedQueryId: String, senderRef: ActorRef, event: DomainEvent) = {

    templateState = templateState.update(event)

    val ack = event match {
      case ClauseAdded(id, _) =>
        ClauseAddedAck(storedQueryId, templateState.version, id)
      case ClauseRemoved(id, clause) =>
        ClauseRemovedAck(storedQueryId, templateState.version , id, clause)
      case Named(name) =>
        mediator ! DistributedPubSubMediator.Publish("sortedQueryItem", StoredQueryItem(storedQueryId, name))
        NamedAck(storedQueryId)
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
