package com.inu.cluster

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRefFactory, ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.inu.cluster.storedquery.{StoredQueryRepoAggRoot, StoredQueryRepoView}
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Try


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
    case MemberUp(member) =>
      log.info(s"Cluster member up: ${member.address} roles(${member.roles.mkString(",")})")
    case UnreachableMember(member) =>
      log.warning(s"Cluster member unreachable: ${member.address}")
    case _: MemberEvent =>
  }
}
