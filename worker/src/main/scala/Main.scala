package worker

import akka.actor._
import com.typesafe.config._
import java.net.{ InetAddress, NetworkInterface }
import scala.collection.JavaConversions._
import common.NodeConfig

object Main extends App {

  val nodeConfig = NodeConfig parse args

  nodeConfig map { c =>
    val system = ActorSystem(c.clusterName, c.config)

    system.log info s"ActorSystem ${system.name} started successfully"

  }
}
