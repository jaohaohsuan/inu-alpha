package com.inu.frontend

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.util.Timeout

import scala.concurrent.duration._

/**
  * Created by henry on 4/1/16.
  */
class ClusterMonitor extends  Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val executionContext = context.dispatcher
  val cluster = Cluster(system)


  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  def registerBackendActors = {

    implicit val timeout = Timeout(3 seconds)
    system.actorSelection("/user/StoredQueryRepoAggRoot-Proxy").resolveOne.onFailure { case ex =>
      log.info(ex.getMessage)
      system.actorOf(ClusterSingletonProxy.props(
        singletonManagerPath = "/user/StoredQueryRepoAggRoot",
        settings = ClusterSingletonProxySettings(system)
      ), name = "StoredQueryRepoAggRoot-Proxy")
    }
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case MemberUp(member) =>
      log.info(s"Cluster member up: ${member.address} roles(${member.roles.mkString(",")})")
      registerBackendActors

    case UnreachableMember(member) =>
      if(member.roles.contains("compute")) {
        system.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! PoisonPill
      }
      log.warning(s"Cluster member unreachable: ${member.address}")
    case _: MemberEvent =>
  }
}
