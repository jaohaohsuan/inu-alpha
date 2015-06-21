package domain.search

import akka.actor._
import akka.contrib.pattern.ClusterClient.{SendToAll, Send}
import domain.search.DependencyGraph.{ConsumerChainsResponse, StoredQueryId, ConsumerChainsQuery, ChainLink}
import domain.search.StoredQueryCommandQueryProtocol.{UpdatedAck, ClauseAddedAck}


object Gathering {

  def props(clusterClient: ActorRef,
            from: StoredQueryId,
            lastAck: Option[UpdatedAck] = None,
            initialJobs: Option[List[ChainLink]] = None): Props =
    Props(classOf[Gathering], clusterClient, from, lastAck, initialJobs)

  case object Tick
}

class Gathering(clusterClient: ActorRef,
                from: StoredQueryId,
                var lastAck: Option[UpdatedAck] = None,
                initialJobs: Option[List[ChainLink]]) extends Actor with ActorLogging {

  import StoredQueryCommandQueryProtocol._
  import Gathering._
  import context._

  var jobs = List[ChainLink]()

  clusterClient ! SendToAll(storedQueryDependencyGraphSingleton, ConsumerChainsQuery(from))

  def receive: Receive = {

    case ConsumerChainsResponse(chainLinks) =>
      jobs = initialJobs.getOrElse(List.empty) ++ chainLinks.toList
      log.info(s"$jobs")
      self ! Tick

    case Tick =>
      jobs match {
        case Nil =>
          stop(self)
        case x :: xs =>
          become(updating(x, xs), discardOld = false)
          clusterClient ! Send(storedQueryViewRegion, GetAsBoolClauseQuery(x.provider), localAffinity = true)
      }
  }

  def updating(link: ChainLink, rest: List[ChainLink]): Receive = {

    case BoolClauseResponse(storedQueryId, name, clauses, version) =>

      lastAck match {
        case Some(ack) if ack.storedQueryId == storedQueryId && version < ack.version =>
          unbecome()
          self ! Tick
        case _ =>
          log.info(s"$storedQueryId provides $clauses for ${link.consumer}")
          clusterClient ! Send(storedQueryRegion,
            AddClauseCommand(link.consumer, NamedBoolClause(storedQueryId, link.occurrence, Some(name), clauses)),
            localAffinity = true)
      }

    case ack @ ClauseAddedAck(templateId, version, id) =>
      lastAck = Some(ack)
      jobs = rest
      unbecome()
      self ! Tick
  }
}

