package com.inu.frontend

import java.net.InetAddress

import akka.actor.{ActorSystem, Props}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import spray.can.Http
import com.typesafe.config.ConfigFactory
import com.inu.frontend.NodeConfigurator._

import scala.concurrent.duration._


object Main extends App {

  val config = ConfigFactory.load().onboard()

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
  println(status)

  system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/StoredQueryRepoAggRoot",
    settings = ClusterSingletonProxySettings(system)
  ), name = "StoredQueryRepoAggRoot-Proxy")

  val listener = system.actorOf(Props(classOf[ServiceActor], client), "service")

  val host = "0.0.0.0"
  val port = Config.port

  val release = () => {
    client.close()
    system.terminate()
  }

  IO(Http).ask(Http.Bind(listener, interface = host, port = port))
    .mapTo[Http.Event]
    .map {
      case Http.Bound(address) =>
        println(s"river service v${com.inu.frontend.storedq.BuildInfo.version} bound to $address")
      case Http.CommandFailed(cmd) =>
        println("river service could not bind to " +  s"$host:$port, ${cmd.failureMessage}")
        release()
    }

  sys.addShutdownHook(release())
}
