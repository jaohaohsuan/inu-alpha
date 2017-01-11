package com.inu.frontend

import java.net.InetAddress

import akka.actor.{ActorSystem, DeadLetter, Props}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import spray.can.Http
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._


object Main extends App with LazyLogging {

  val config = ConfigFactory.load()

  implicit val timeout = Timeout(10 seconds)
  implicit val system = ActorSystem(config.getString("storedq.cluster-name"), config)
  implicit val executionContext = system.dispatcher

  val settings = org.elasticsearch.common.settings.Settings.settingsBuilder()
    .put("cluster.name", config.getString("elasticsearch.cluster-name"))
    .build()

  val esAddr = InetAddress.getByName(config.getString("elasticsearch.transport-address"))

  val client = TransportClient.builder().settings(settings).build()
    .addTransportAddress(new InetSocketTransportAddress(esAddr, config.getInt("elasticsearch.transport-tcp")))

  val status = client.admin().cluster().prepareHealth().get().getStatus
  logger.info(s"elasticsearch status: $status")

  lazy val listener = system.actorOf(Props(classOf[ServiceActor], client), "service")

  val host = Config.host
  val port = Config.port

  val release = () => {
    client.close()
    system.terminate()
  }

  system.actorOf(Props[SeedMonitor])

  system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/StoredQueryRepoAggRoot",
    settings = ClusterSingletonProxySettings(system).withRole("backend")
  ), name = "StoredQueryRepoAggRoot-Proxy")

  IO(Http).ask(Http.Bind(listener, interface = host, port = port))
    .mapTo[Http.Event]
    .map {
      case Http.Bound(address) =>
        println(s"river service v${com.inu.frontend.storedq.BuildInfo.version} bound to $address")
      case Http.CommandFailed(cmd) =>
        println("river service could not bind to " +  s"$host:$port, ${cmd.failureMessage}")
        sys.exit(1)
    }

  system.eventStream.subscribe(system.actorOf(Props[ClusterDoctor]), classOf[DeadLetter])

  sys.addShutdownHook(release())
}
