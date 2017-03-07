package com.inu.frontend

import java.net.InetAddress

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.client.PreBuiltTransportClient
import spray.can.Http

import scala.concurrent.duration._


object Main extends App with LazyLogging {

  val config = ConfigFactory.load()

  implicit val system = ActorSystem(config.getString("storedq.cluster-name"), config)
  implicit val executionContext = system.dispatcher

  private lazy val client = {
    val esAddr = InetAddress.getByName(config.getString("elasticsearch.transport-address"))

    val settings = org.elasticsearch.common.settings.Settings.builder()
      .put("cluster.name", config.getString("elasticsearch.cluster-name"))
      .build()

    new PreBuiltTransportClient(settings)
      .addTransportAddress(new InetSocketTransportAddress(esAddr, config.getInt("elasticsearch.transport-tcp")))
  }

  Cluster(system).registerOnMemberUp {
    system.actorOf(ClusterSingletonProxy.props(
      singletonManagerPath = "/user/StoredQueryRepoAggRoot",
      settings = ClusterSingletonProxySettings(system).withRole("backend")
    ), name = "StoredQueryRepoAggRoot-Proxy")

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

    IO(Http).ask(Http.Bind(system.actorOf(Props(classOf[DigServiceActor], client), "digService"), interface = host, port = 7880))
      .mapTo[Http.Event]
      .map {
        case Http.Bound(address) =>
          println(s"dig service version: ${com.inu.frontend.storedq.BuildInfo.version} bound to $address")
        case Http.CommandFailed(cmd) =>
          println("dig service could not bind to " +  s"$host:7880, ${cmd.failureMessage}")
          sys.exit(1)
      }
  }
}
