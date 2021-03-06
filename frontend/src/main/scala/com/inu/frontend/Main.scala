package com.inu.frontend

import java.net.InetAddress

import akka.actor.Status.Success
import akka.actor.{ActorSystem, DeadLetter, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import spray.can.Http
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Try, Success, Failure }


object Main extends App with LazyLogging {

  val config = ConfigFactory.load()

  implicit val system = ActorSystem(config.getString("storedq.cluster-name"), config)
  implicit val executionContext = system.dispatcher

  private lazy val esClient = Try {

    val esAddr = InetAddress.getByName(config.getString("elasticsearch.transport-address"))
    val settings = org.elasticsearch.common.settings.Settings.settingsBuilder()
      .put("cluster.name", config.getString("elasticsearch.cluster-name"))
      .build()

    TransportClient.builder().settings(settings).build()
      .addTransportAddress(new InetSocketTransportAddress(esAddr, config.getInt("elasticsearch.transport-tcp")))
  }

  system.actorOf(Props[SeedNodeListener])

  val cluster = Cluster(system)

  cluster.registerOnMemberUp {

    system.actorOf(ClusterSingletonProxy.props(
      singletonManagerPath = "/user/storedQueryRepoAggRootGuardian",
      settings = ClusterSingletonProxySettings(system).withRole("backend")
    ), name = "StoredQueryRepoAggRoot-Proxy")

    implicit val timeout = Timeout(10 seconds)

    val host = Config.host
    val port = Config.port

    // try reach elasticsearch
    esClient match {
      case scala.util.Success(transportClient) =>
        IO(Http).ask(Http.Bind(system.actorOf(Props(classOf[ServiceActor], transportClient), "service"), interface = host, port = port))
          .mapTo[Http.Event]
          .map {
            case Http.Bound(address) =>
              println(s"storedq service version: ${com.inu.frontend.storedq.BuildInfo.version} bound to $address")
            case Http.CommandFailed(cmd) =>
              println("storedq service could not bind to " +  s"$host:$port, ${cmd.failureMessage}")
              sys.exit(1)
          }

        IO(Http).ask(Http.Bind(system.actorOf(Props(classOf[DigServiceActor], transportClient), "digService"), interface = host, port = 7880))
          .mapTo[Http.Event]
          .map {
            case Http.Bound(address) =>
              println(s"dig service version: ${com.inu.frontend.storedq.BuildInfo.version} bound to $address")
            case Http.CommandFailed(cmd) =>
              println("dig service could not bind to " +  s"$host:7880, ${cmd.failureMessage}")
              sys.exit(1)
          }

      case Failure(ex) =>
        system.log.error(ex,"elasticsearch service cannot be resolved")
        sys.exit(1)
    }

  }

  sys.addShutdownHook {
    cluster.leave(cluster.selfAddress)
    cluster.down(cluster.selfAddress)
    system.terminate()

    Await.result(system.whenTerminated, Duration.Inf)
    system.log.info("actorsystem shutdown gracefully")
  }
}
