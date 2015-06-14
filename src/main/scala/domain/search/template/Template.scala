package domain.search.template

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.contrib.pattern.{ClusterSharding, ShardRegion}
import akka.persistence.{RecoveryCompleted, PersistentActor}


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

  import TemplateState._
  import CommandQueryProtocol._

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
        templateState = templateState.update(event)
        ref ! ClauseAddedAck(templateId, templateState.version, event.id.toString)
      }
      persist(ClauseAdded(clause.hashCode(), clause))(eventHandler(sender(), _))

    case RemoveClauseCommand(templateId, clauseId) => {
      templateState.clauses.get(clauseId) match {
        case None =>
          log.info(s"The clause $clauseId doesn't exist")
        case Some(clause) =>
          val eventHandler = (ref: ActorRef, event: ClauseRemoved) => {
            templateState = templateState.update(event)
            ref ! ClauseRemovedAck(templateId, event.id, event.clause)
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
