package com.inu.frontend

import java.net.InetAddress

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props, ReceiveTimeout}
import akka.cluster.{Cluster, UniqueAddress}
import akka.cluster.ClusterEvent._
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.cluster.health.ClusterHealthStatus
import org.elasticsearch.common.transport.InetSocketTransportAddress
import spray.can.Http

import scala.concurrent.Future
import scala.concurrent.duration._

class SeedMonitor extends  Actor with ActorLogging {


  implicit val system: ActorSystem = context.system
  implicit val ec = system.dispatcher

  //private val elasticsearchReadinessProbe = system.scheduler.schedule(2.seconds, 5.seconds, self, ProbeElasticsearch)

  private lazy val localAddress = {
    val tcp = config.getConfig("akka.remote.netty.tcp")
    Address("akka.tcp",system.name,Some(InetAddress.getLocalHost.getHostAddress), Some(tcp.getInt("port")))
    //s"${system.name}@${tcp.getString("hostname")}:${tcp.getString("port")}"
  }

  val cluster = Cluster(system)
  context.setReceiveTimeout(10.seconds)

  private val config = system.settings.config

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {

    case ReceiveTimeout =>
      log.error("join to cluster timeout")
      sys.exit(1)

    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
      if(member.roles.contains("backend"))
        sys.exit(1)

    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
      if(member.roles.contains("backend"))
        sys.exit(1)

    case MemberJoined(member) =>
      log.info(s"$member joined")
      log.info(s"${member.address} / $localAddress")
      context.setReceiveTimeout(Duration.Undefined)
//      if (member.address == localAddress && member.hasRole("frontend")) {
//      }

    case _ =>
  }

}

