package common

import com.typesafe.config._

case class NodeConfig(isSeed: Boolean = false, isEventsStore: Boolean = false, seedNodes: Seq[String] = Seq.empty){

  import ConfigFactory._
  import NodeConfig._

  // Initialize the config once
  lazy val config = asConfig

  // Name of the ActorSystem
  lazy val clusterName = config getString CLUSTER_NAME_PATH

  private def asConfig(): Config = {

    val config = load(
      getClass.getClassLoader,
      ConfigResolveOptions.defaults.setAllowUnresolved(true)
    )

    val name = config getString CLUSTER_NAME_PATH

    // which config should be used
    val clusterConfig = Some(ConfigFactory parseResources (if(isSeed) SEED_NODE else CLUSTER_NODE))

    val persistenceConfig: Option[Config] =
      if(isEventsStore) Some(ConfigFactory parseResources EVENT_STORE_NODE) else None

    val ip = if(config hasPath CLUSTER_IP_PATH) config getString CLUSTER_IP_PATH else HostIP.load() getOrElse "127.0.0.1"
    val ipValue = ConfigValueFactory fromAnyRef ip

    val seedNodesString = seedNodes.map {
      node => s"""akka.cluster.seed-nodes += "akka.tcp://$name@$node""""
    }.mkString("\n")

    val rolesConfig = if(isEventsStore) Some(ConfigFactory parseString "akka.cluster.roles += store") else None

    val zero = (ConfigFactory parseString seedNodesString).withValue(CLUSTER_IP_PATH, ipValue)

    (rolesConfig :: persistenceConfig :: clusterConfig :: Some(config) :: Nil).flatten
      .foldLeft(zero){(acc, p)=> acc.withFallback(p)}.resolve
  }
}

object NodeConfig {

  /** static configuration for seed nodes*/
  val SEED_NODE = "node.seed.conf"

  /** static configuration for normal cluster nodes */
  val CLUSTER_NODE = "node.cluster.conf"

  val EVENT_STORE_NODE = "persistence.conf"

  val CLUSTER_IP_PATH = "clustering.ip"

  private val CLUSTER_NAME_PATH = "clustering.cluster.name"

  def parse(args: Seq[String]): Option[NodeConfig] = {
    val parser = new scopt.OptionParser[NodeConfig]("inu") {
      opt[Unit]("seed") action { (_, c) =>
        c.copy(isSeed = true)
      } text "set this flag to start this system as a seed node"
      opt[Unit]("store")  action { (_, c) =>
        c.copy(isEventsStore = true)
      } text "set this flag to start this system as a event store"
      arg[String]("<seed-node>...") unbounded() optional() action { (n, c) =>
        c.copy(seedNodes = c.seedNodes :+ n)
      } text "give a list of seed nodes like this: <ip>:<port> <ip>:<port>"
      checkConfig {
        case NodeConfig(false, _, Seq()) => failure("ClusterNodes need at least one seed node")
        case _ => success
      }
    }

    parser.parse(args, NodeConfig())
  }
}
