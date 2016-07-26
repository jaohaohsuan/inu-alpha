package com.inu.frontend

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

class SeedMonitor extends  Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  val cluster = Cluster(system)


  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case UnreachableMember(member) =>
      log.warning(s"Cluster member unreachable: ${member.address}")
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
      if(member.roles.contains("backend"))
       system.terminate()
    case _: MemberEvent =>

  }
}

