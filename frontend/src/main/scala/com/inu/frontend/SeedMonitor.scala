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

object SeedMonitor {
  private case object ProbeElasticsearch
}

class SeedMonitor extends  Actor with ActorLogging {

  import com.inu.frontend.SeedMonitor._

  implicit val system: ActorSystem = context.system
  implicit val ec = system.dispatcher

  //private val elasticsearchReadinessProbe = system.scheduler.schedule(2.seconds, 5.seconds, self, ProbeElasticsearch)

  private lazy val localAddress = {
    val tcp = config.getConfig("akka.remote.netty.tcp")
    Address("akka.tcp",system.name,Some(tcp.getString("hostname")), Some(tcp.getInt("port")))
    //s"${system.name}@${tcp.getString("hostname")}:${tcp.getString("port")}"
  }

  val cluster = Cluster(system)
  context.setReceiveTimeout(10.seconds)

  private val config = system.settings.config

  private lazy val client = {
    val esAddr = InetAddress.getByName(config.getString("elasticsearch.transport-address"))
    val settings = org.elasticsearch.common.settings.Settings.settingsBuilder()
      .put("cluster.name", config.getString("elasticsearch.cluster-name"))
      .build()

     TransportClient.builder().settings(settings).build()
      .addTransportAddress(new InetSocketTransportAddress(esAddr, config.getInt("elasticsearch.transport-tcp")))
  }

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

//    case ProbeElasticsearch =>
//      client.admin().cluster().prepareHealth().get().getStatus match {
//        case ClusterHealthStatus.RED =>
//          log.warning("elasticsearch unavailable to connect")
//        case ClusterHealthStatus.GREEN =>
//          elasticsearchReadinessProbe.cancel()
//          log.info(s"elasticsearch status: GREEN")
//          readyToServe()
//        case _ =>
//      }

    case MemberJoined(member) =>
      log.info(s"$member joined")
      log.info(s"${member.address} / $localAddress")
      if (member.address == localAddress) {
        system.actorOf(ClusterSingletonProxy.props(
          singletonManagerPath = "/user/StoredQueryRepoAggRoot",
          settings = ClusterSingletonProxySettings(system).withRole("backend")
        ), name = "StoredQueryRepoAggRoot-Proxy")

        readyToServe()
      }

    case _ =>
  }

  private def readyToServe(): Future[Unit] = {

//    if (elasticsearchReadinessProbe.isCancelled)
//      Future.successful(()=>{})

    implicit val timeout = Timeout(10 seconds)

    val host = Config.host
    val port = Config.port
    IO(Http).ask(Http.Bind(system.actorOf(Props(classOf[ServiceActor], client), "service"), interface = host, port = port))
      .mapTo[Http.Event]
      .map {
        case Http.Bound(address) =>
          println(s"storedq service version: ${com.inu.frontend.storedq.BuildInfo.version} bound to $address")
        case Http.CommandFailed(cmd) =>
          println("storedq service could not bind to " +  s"$host:$port, ${cmd.failureMessage}")
          sys.exit(1)
      }
  }
}

