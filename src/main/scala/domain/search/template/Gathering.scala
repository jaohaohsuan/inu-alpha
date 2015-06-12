package domain.search.template

import akka.actor._
import akka.contrib.pattern.ClusterClient.Send
import akka.contrib.pattern.ClusterSharding
import scala.concurrent.duration._


object Gathering {

  def props(clusterClient: ActorRef, segments: List[(String, (String, String))]): Props =
    Props(classOf[Gathering], clusterClient, segments)

  case object Tick

}

class Gathering(clusterClient: ActorRef, private var segments: List[(String, (String, String))]) extends Actor with ActorLogging {

  import CommandQueryProtocol._
  import Gathering._

  import context.dispatcher

  def scheduler = context.system.scheduler

  log.info(s"There are ${segments.length} $segments to gather.")

//  val templateRegion: ActorRef = ClusterSharding(context.system).shardRegion(Template.shardName)
//  val templateViewRegion: ActorRef = ClusterSharding(context.system).shardRegion(TemplateView.shardName)

  self ! Tick

  def receive: Receive = {
    case Tick =>
      if (segments == Nil) {
        log.info(s"Gathering is completed")
        context.stop(self)
      } else {
        val ((templateId, (clauseTemplateId, occur)) :: xs) = segments
        val namedClause = NamedBoolClause(clauseTemplateId, occur = occur)
        context.become(processing(templateId, namedClause, xs), discardOld = false)
        log.info(s"$templateId consuming $clauseTemplateId")
        sendMessage2TemplateViewRegion(GetAsBoolClauseQuery(clauseTemplateId))
      }
  }

  def processing(consumerId: String, clause: NamedBoolClause, tail: List[(String, (String, String))]): Receive = {
    case BoolClauseResponse(_, name, clauses) =>
      log.info(s"${clause.templateId} provides $clauses")
      sendMessage2TemplateRegion(AddClauseCommand(consumerId, clause.copy(clauses = clauses, templateName = name)))
    case ClauseAddedAck(_, id) =>
      log.info(s"NamedBoolClause($id) Added")
        segments = tail
        context.unbecome()
        self ! Tick
//    case ReceiveTimeout =>
//      scheduler.scheduleOnce(3.seconds, templateViewRegion, GetAsBoolClauseQuery(clause.templateId))
  }

  def sendMessage2TemplateRegion(message: Any) = {
    clusterClient ! Send(s"/user/sharding/${domain.search.template.Template.shardName}", message, localAffinity = true)
  }

  def sendMessage2TemplateViewRegion(message: Any) = {
    clusterClient ! Send(s"/user/sharding/${domain.search.template.TemplateView.shardName}", message, localAffinity = true)
  }
}

