package com.inu.cluster

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.dispatch.sysmsg.Recreate
import akka.util.Timeout
import com.inu.cluster.storedquery.{StoredQueryRepoAggRoot, StoredQueryRepoView}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionException
import scala.concurrent.duration._

object Main extends App {

  val config: Config = ConfigFactory.load()

  implicit val timeout = Timeout(5.seconds)
  implicit val system = ActorSystem(config.getString("storedq.cluster-name"), config)

  system.log.info("Configured seed nodes: " + config.getStringList("akka.cluster.seed-nodes").mkString(", "))
  system.log.info("Configured cassandra nodes: " + config.getStringList("cassandra-journal.contact-points").mkString(", "))

  system.actorOf(Props[ClusterMonitor], "cluster-monitor")

  implicit class clustering(props: Props) {
    def singleton(role: String = "backend")(implicit system: ActorSystem) = ClusterSingletonManager.props(
      singletonProps = props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system).withRole(role))
  }

  system.eventStream.subscribe(system.actorOf(Props[CassandraProbe]), classOf[DeadLetter])

  system.actorOf(StoredQueryRepoAggRoot.propsWithBackoff.singleton(), "StoredQueryRepoAggRoot")

  system.actorOf(StoredQueryRepoView.propsWithBackoff)

  system.log.info(s"running version ${com.inu.cluster.storedq.BuildInfo.version}")

  val release = () => {
    system.terminate()
  }
  sys.addShutdownHook(release())
}

class CassandraProbe extends Actor with ActorLogging {

  case class RetryTimes(times: Int)
  case object Probe

  implicit val ec = context.system.dispatcher

  context.system.scheduler.schedule(0.seconds, 1.minutes, self, Probe)

  private var retry = RetryTimes(1)

  def receive: Receive = {
    case Probe =>
      retry = RetryTimes(0)

    case RetryTimes(c) if 3 <= c =>
      sys.exit(1)

    case state: RetryTimes =>
      retry = state
      log.info(s"cassnadra lost ${retry.times} times")

    case DeadLetter(x: PossiblyHarmful, _, _) =>
      self ! retry.copy(times = retry.times + 1)

    case DeadLetter(m, from, to) =>

    case _ =>
  }
}