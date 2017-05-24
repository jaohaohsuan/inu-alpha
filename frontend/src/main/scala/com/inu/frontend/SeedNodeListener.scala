package com.inu.frontend

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

/**
  * Created by henry on 5/24/17.
  */
class SeedNodeListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) if member.roles.contains("seed") =>
      log.info(s"Seed-node $member is up.")
    case UnreachableMember(member) if member.roles.contains("seed") =>
      log.warning(s"Seed-node $member is unreachable.")
      log.warning(s"${context.system} will be shutdown immediately.")
      context.system.terminate()
    case MemberRemoved(member, previousStatus) if member.roles.contains("seed") =>
      log.warning(s"Seed-node $member is removed from $previousStatus status.")
      log.warning(s"${context.system} will be shutdown immediately.")
      context.system.terminate()
    case _: MemberEvent => // ignore
  }
}
