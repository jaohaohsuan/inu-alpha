package domain.search.template

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.contrib.pattern.ShardRegion
import akka.persistence.{PersistentActor, RecoveryCompleted}


object Template {

  import CommandQueryProtocol.Command

  def props(): Props = Props[Template]

  val idExtractor: ShardRegion.IdExtractor = {
    case m: Command => (m.templateId, m)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case m: Command => (math.abs(m.templateId.hashCode) % 100).toString
  }

  val shardName: String = "SearchTemplate"
}

class Template extends PersistentActor with ActorLogging {

  //val readRegion: ActorRef = ClusterSharding(context.system).shardRegion(TemplateView.shardName)

  import CommandQueryProtocol._
  import TemplateState._

  var templateState = TemplateState.empty()

  val from = Cluster(context.system).selfAddress.hostPort

  override def receiveRecover: Receive = {
    case event: DomainEvent =>
      templateState = templateState.update(event)
    case RecoveryCompleted =>
      log.info(s"Recovered state: ${templateState.clauses}")
  }

  override def receiveCommand: Receive = {

    case AddClauseCommand(templateId, clause) =>
      val eventHandler = (ref: ActorRef, event: ClauseAdded) => {
        log.info(s"${clause.getClass.getSimpleName}(${event.id}) is added")
        templateState = templateState.update(event)
        ref ! ClauseAddedAck(templateId, templateState.version, event.id.toString)
      }
      persist(ClauseAdded(clause.hashCode(), clause))(eventHandler(sender(), _))

    case RemoveClauseCommand(templateId, clauseId) => {
      templateState.clauses.get(clauseId) match {
        case None =>
          log.info(s"The clause $clauseId doesn't exist")
          sender() ! ClauseNotFoundAck
        case Some(clause) =>
          val eventHandler = (ref: ActorRef, event: ClauseRemoved) => {
            templateState = templateState.update(event)
            ref ! ClauseRemovedAck(templateId, templateState.version ,event.id, event.clause)
          }
          persist(ClauseRemoved(clauseId, clause))(eventHandler(sender(), _))
      }
    }
  }

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  override def unhandled(message: Any): Unit = message match {
    //case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
    case _              =>
      log.info(s"unhandled message: $message")
      super.unhandled(message)
  }
}
