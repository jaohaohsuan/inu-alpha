package common

import com.typesafe.config._
import org.elasticsearch.node.Node
import java.net.InetAddress

import scala.util.Try


case class NodeConfig(isSeed: Boolean = false,
                      isEventsStore: Boolean = false,
                      elasticsearch: org.elasticsearch.common.settings.ImmutableSettings.Builder =
                      org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder().put("node.data", false),
                      roles: Seq[String] = Seq.empty,
                      seedNodes: Seq[String] = Seq.empty){

  import ConfigFactory._
  import NodeConfig._

  // Initialize the config once
  lazy val config = asConfig

  // Name of the ActorSystem
  lazy val clusterName = config getString CLUSTER_NAME_PATH

  //lazy val node: Node = org.elasticsearch.node.NodeBuilder.nodeBuilder().settings(elasticsearch).node()

  private def asConfig(): Config = {

    val config: Config = load(
      getClass.getClassLoader,
      ConfigResolveOptions.defaults.setAllowUnresolved(true)
    )

    val name = config getString CLUSTER_NAME_PATH

    // which config should be used
    val clusterConfig = Some(ConfigFactory parseResources (if(isSeed) SEED_NODE else CLUSTER_NODE))

    //val frontendConfig = if(roles.contains("web")) Some(ConfigFactory parseResources "frontend.conf") else None

    val persistenceConfig: Option[Config] =
      if(isEventsStore) Some(ConfigFactory parseResources EVENT_STORE_NODE) else None

    val ip = if(config hasPath CLUSTER_IP_PATH) config getString CLUSTER_IP_PATH else HostIP.load() getOrElse "127.0.0.1"
    val ipValue = ConfigValueFactory fromAnyRef ip

    val portValue = ConfigValueFactory fromAnyRef(if(isSeed) 2551 else 0)

    val seedNodesString = seedNodes.map {
      node => s"""akka.cluster.seed-nodes += "akka.tcp://$name@${lookupNodeAddress(node)}""""
    }.mkString("\n")

    val rolesString = rolesBuilder().map {
      role => s"""akka.cluster.roles += $role"""
    }.mkString("\n")

    val zero = (ConfigFactory parseString seedNodesString)
      .withFallback(ConfigFactory parseString rolesString)
      .withValue(CLUSTER_IP_PATH, ipValue)
      .withValue(CLUSTER_PORT_PATH, portValue)

    (clusterConfig :: persistenceConfig :: Some(config) :: Nil).flatten
      .foldLeft(zero){(acc, p)=> acc.withFallback(p)}.resolve
  }

  lazy val isDataNode = elasticsearch.get("node.data") == "true"

  def rolesBuilder() =
     Seq(
      Option("data-node").filter(_ => isDataNode),
      Option("store").filter(_ => isEventsStore)
    ).flatten.toList ++ roles


}

object NodeConfig {

  val SYS_ROLES = Seq("seed", "store")
  /** static configuration for seed nodes*/
  val SEED_NODE = "node.seed.conf"

  /** static configuration for normal cluster nodes */
  val CLUSTER_NODE = "node.cluster.conf"

  val EVENT_STORE_NODE = "node.store.conf"

  val CLUSTER_IP_PATH = "clustering.ip"

  val CLUSTER_PORT_PATH = "clustering.port"

  private val CLUSTER_NAME_PATH = "clustering.cluster.name"

  def lookupNodeAddress(value: String) = {

    val node = """(\w*):*(\d*)""".r

    value match {
      case node(hostname, port) =>
        Try(InetAddress.getByName(hostname).getHostAddress).map(addr => s"$addr:$port").getOrElse(value)
      case _ => value
    }
  }

  def parse(args: Seq[String]): Option[NodeConfig] = {
    val parser = new scopt.OptionParser[NodeConfig]("inu") {
      opt[Unit]("seed") action { (_, c) =>
        c.copy(isSeed = true)
      } text "set this flag to start this system as a seed node"
      opt[Unit]("store") action { (_, c) =>
        c.copy(isEventsStore = true)
      } text "set this flag to start this system as a event store"
      opt[Unit]("data-node") action { (_,c) =>
        c.copy(elasticsearch = c.elasticsearch.put("node.data", true))
      } text "Set elasticsearch node can hold data"
      opt[Seq[String]]('r',"role") valueName("small,large...") action { (x, c) =>
        c.copy(roles = x)
      } text "set this flag to start this machine as a compute role node"
      arg[String]("<seed-node>...") unbounded() optional() action { (n, c) =>
        c.copy(seedNodes = c.seedNodes :+ n)
      } text "give a list of seed nodes like this: <ip>:<port> <ip>:<port>"
      checkConfig {
        case NodeConfig(false, _, _, _ ,Seq()) => failure("ClusterNodes need at least one seed node")
        case NodeConfig(_, _, _, roles ,_) if roles.intersect(SYS_ROLES).nonEmpty => failure("forbidden roles found such as seed or store")
        case _ => success
      }
    }

    parser.parse(args, NodeConfig())
  }
}
