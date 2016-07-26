package com.inu.cluster

import java.net.{InetAddress, NetworkInterface}

import scala.collection.JavaConversions._
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try

/**
  * Created by henry on 4/1/16.
  */
object NodeConfigurator {

  implicit class SelfRegister(config: Config) extends LazyLogging {

    lazy val PEER_DISCOVERY_SERVICE = Option(System.getenv("PEER_DISCOVERY_SERVICE"))
    lazy val host = InetAddress.getLocalHost.getHostAddress

    private def getSeedNodes: List[String] = {
      Try(
        PEER_DISCOVERY_SERVICE match {
          case Some(value) if value.matches("""[\w.]+\.\w+""") =>
            java.net.InetAddress.getAllByName(value).map(_.getHostAddress).toList
          case _ => host :: Nil
        }) match  {
        case scala.util.Failure(ex) =>
          logger.warn(s"$PEER_DISCOVERY_SERVICE is unavailable", ex)
          logger.info(s"$host become the first seed")
          InetAddress.getLocalHost.getHostAddress :: Nil
        case scala.util.Success(Nil) =>
          logger.error("$PEER_DISCOVERY_SERVICE doesn't have any endpoints")
          sys.exit(1)
        case scala.util.Success(seeds) => seeds
      }
    }

    def onboard(): Config = {

      val storedqPart = config.getConfig("storedq")
      val clusterName = storedqPart.getString("cluster-name")

      val `akka.cluster.seed-nodes` = getSeedNodes.map {
        addr => s"""akka.cluster.seed-nodes += "akka.tcp://$clusterName@$addr:2551""""
      }.mkString("\n")

      ConfigFactory.parseString(`akka.cluster.seed-nodes`)
        .withFallback(config)
        .resolve()
    }
  }
}
