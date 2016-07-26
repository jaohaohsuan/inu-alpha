package com.inu.cluster

import java.net.{InetAddress, NetworkInterface}

import scala.collection.JavaConversions._
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

/**
  * Created by henry on 4/1/16.
  */
object NodeConfigurator {

  val getHostLocalAddress: PartialFunction[String, String] = {
    case ifac =>
      NetworkInterface.getNetworkInterfaces
        .find(_.getName equals ifac)
        .flatMap { interface =>
          interface.getInetAddresses.find(_.isSiteLocalAddress).map(_.getHostAddress)
        }
        .getOrElse("")
  }

  val getHostname: PartialFunction[String, String] = {
    case "" => InetAddress.getLocalHost.getHostName
  }

  implicit class SelfRegister(config: Config) {

    def onboard(): Config = {

      val storedqPart = config.getConfig("storedq")
      val clusterName = storedqPart.getString("cluster-name")
      val port = storedqPart.getString("port")
      val ifac = storedqPart.getString("ifac")
      val seedNodes = storedqPart.getString("seed-nodes")

      val roles = storedqPart.getString("roles")

      val host: String = getHostname.orElse(getHostLocalAddress)(ifac).trim

      val init: PartialFunction[String, Array[String]] = { case "" => Array(s"$host:$port") }
      val join: PartialFunction[String, Array[String]] = { case x: String => x.split("""[\s,]+""").map(_.trim).filterNot(_.isEmpty) }

      val `akka.cluster.seed-nodes` = init.orElse(join)(seedNodes).map {
        addr => s"""akka.cluster.seed-nodes += "akka.tcp://$clusterName@$addr""""
      }.mkString("\n")

      ConfigFactory.parseString(`akka.cluster.seed-nodes` + s"\nakka.cluster.roles = $roles")
        .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(host))
        .withFallback(config)
        .resolve()
    }
  }
}
