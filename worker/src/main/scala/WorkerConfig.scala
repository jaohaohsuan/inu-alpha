package worker

import akka.actor.{ActorPath, ActorSystem}
import akka.cluster.client.ClusterClientSettings
import com.typesafe.config.{ConfigFactory, ConfigResolveOptions, Config}
import com.typesafe.config.ConfigFactory._
import common.HostIP

case class WorkerConfig(initialContacts: Set[String] = Set.empty) {

  lazy val config = asConfig

  def clusterClientSettings(implicit system: ActorSystem) = {
    ClusterClientSettings(system)
      .withInitialContacts(initialContacts.map(address => ActorPath.fromString(s"akka.tcp://$address/system/receptionist")))
    //akka.tcp://OtherSys@host1:2552/system/receptionist
  }

  private def asConfig(): Config = {
    val config = load(
      getClass.getClassLoader,
      ConfigResolveOptions.defaults.setAllowUnresolved(true)
    )
    val ip = HostIP.load() getOrElse "127.0.0.1"
    ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$ip").withFallback(config).resolve
  }
}

object WorkerConfig {

  def parse(args: Seq[String]): Option[WorkerConfig] = {
    val parser = new scopt.OptionParser[WorkerConfig]("inu") {
      arg[String]("<initial-contacts>...") unbounded() optional() action { (n, c) =>
        c.copy(initialContacts = c.initialContacts + n)
      } text "give a list of initial contacts like this: <cluster>@<ip>:<port> <cluster>@<ip>:<port>"
      checkConfig {
        case c if c.initialContacts.isEmpty => failure("Worker need at least one initial contact")
        case _ => success
      }
    }
    parser.parse(args, WorkerConfig())
  }
}


