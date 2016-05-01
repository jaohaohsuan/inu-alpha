import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import domain.storedQuery.StoredQueryAggregateRoot
import seed.{NodeConfigurator, PersistenceConfigurator}

/**
  * Created by henry on 4/29/16.
  */
class StoredQueryBackend extends Actor {

  val cluster = Cluster(context.system)

  def receive = {

    case MemberUp(m) =>
  }
}

object StoredQueryBackend {

  def main(args: Array[String]): Unit = {

    import PersistenceConfigurator._
    import NodeConfigurator._
    import com.typesafe.config._

    val config = ConfigFactory.load().register().enableCassandraPlugin()

    val system = ActorSystem(config.getString("storedq.cluster-name"), config)

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(classOf[StoredQueryAggregateRoot]),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)),
      name = s"${protocol.storedQuery.NameOfAggregate.root}")
  }
}
