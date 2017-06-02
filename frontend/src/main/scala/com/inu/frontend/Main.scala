package com.inu.frontend

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.inu.frontend.route.HelloRoute
import com.inu.frontend.service.Elasticsearch5xClient
import com.inu.frontend.service.ElasticsearchClientService.ElasticsearchClientFactory
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._


object Main extends App with LazyLogging {

  val config = ConfigFactory.load()

  implicit val system: ActorSystem = ActorSystem(config.getString("storedq.cluster-name"), config)
  implicit val executionContext = system.dispatcher

  private lazy val client = {
    val esAddr = InetAddress.getByName(config.getString("elasticsearch.transport-address"))
    val settings = org.elasticsearch.common.settings.Settings.settingsBuilder()
      .put("cluster.name", config.getString("elasticsearch.cluster-name"))
      .build()

    TransportClient.builder().settings(settings).build()
      .addTransportAddress(new InetSocketTransportAddress(esAddr, config.getInt("elasticsearch.transport-tcp")))
  }

  implicit val esClientFactory: ElasticsearchClientFactory = () => new Elasticsearch5xClient()

  //val helloRoute = new HelloRoute()

  //Http().bindAndHandle(helloRoute.route, "",7978)

  Cluster(system).registerOnMemberUp {

    system.actorOf(ClusterSingletonProxy.props(
      singletonManagerPath = "/user/StoredQueryRepoAggRoot",
      settings = ClusterSingletonProxySettings(system).withRole("backend")
    ), name = "StoredQueryRepoAggRoot-Proxy")

    implicit val timeout = Timeout(10 seconds)

    val host = Config.host
    val port = Config.port


    //Http().bindAndHandle(new HelloRoute().route, host = "localhost", port = 7880)

//    IO(Http).ask(Http.Bind(system.actorOf(Props(classOf[ServiceActor], client), "service"), interface = host, port = port))
//      .mapTo[Http.Event]
//      .map {
//        case Http.Bound(address) =>
//          println(s"storedq service version: ${com.inu.frontend.storedq.BuildInfo.version} bound to $address")
//        case Http.CommandFailed(cmd) =>
//          println("storedq service could not bind to " +  s"$host:$port, ${cmd.failureMessage}")
//          sys.exit(1)
//      }
//
//    IO(Http).ask(Http.Bind(system.actorOf(Props(classOf[DigServiceActor], client), "digService"), interface = host, port = 7880))
//      .mapTo[Http.Event]
//      .map {
//        case Http.Bound(address) =>
//          println(s"dig service version: ${com.inu.frontend.storedq.BuildInfo.version} bound to $address")
//        case Http.CommandFailed(cmd) =>
//          println("dig service could not bind to " +  s"$host:7880, ${cmd.failureMessage}")
//          sys.exit(1)
//      }
  }
}
