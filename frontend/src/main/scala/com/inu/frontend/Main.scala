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



object Main extends App with LazyLogging {

  val config = ConfigFactory.load()


  implicit val system = ActorSystem(config.getString("storedq.cluster-name"), config)
  implicit val executionContext = system.dispatcher

  val release = () => {
    //client.close()
    system.terminate()
  }

  system.actorOf(Props[SeedMonitor])

  system.eventStream.subscribe(system.actorOf(Props[ClusterDoctor]), classOf[DeadLetter])

  sys.addShutdownHook(release())
}
