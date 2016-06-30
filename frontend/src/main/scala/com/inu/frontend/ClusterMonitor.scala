package com.inu.frontend

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}


/**
  * Created by henry on 4/1/16.
  */
class ClusterMonitor extends  Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  val cluster = Cluster(system)


  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case MemberUp(member) if member.roles.contains("frontend") =>
      val storedQueryRepoAggRootProxy = system.actorOf(ClusterSingletonProxy.props(
        singletonManagerPath = "/user/StoredQueryRepoAggRoot",
        settings = ClusterSingletonProxySettings(system)
      ), name = "StoredQueryRepoAggRoot-Proxy")
      log.info(s"$storedQueryRepoAggRootProxy actor created")
    case UnreachableMember(member) =>
      log.warning(s"Cluster member unreachable: ${member.address}")
    case _: MemberEvent =>
  }
}
