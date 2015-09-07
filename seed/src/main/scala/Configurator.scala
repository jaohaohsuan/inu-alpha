package seed

import akka.actor.{PoisonPill, Props, Actor}
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonManagerSettings, ClusterSingletonManager}

object Configurator {
  val Name = "conf"
}

class Configurator extends Actor with SharedLeveldbStoreUsage {
  import context.system

   def processReceive: Receive = {
     case SetSharedLeveldbJournalAck =>

       log.info("SharedLeveldbJournalAck")
       /*system.actorOf(ClusterSingletonManager.props(
         singletonProps = Props(classOf[domain.StoredQueryAggregateRoot]),
         terminationMessage = PoisonPill,
         settings = ClusterSingletonManagerSettings(system)),
         name = protocol.storedQuery.AggregateRoot.Name)
*/
       //val storedQueryAggregateRoot = system.actorOf(Props[domain.StoredQueryAggregateRoot], protocol.storedQuery.AggregateRoot.Name)

       //ClusterClientReceptionist(system).registerService(storedQueryAggregateRoot)

     case unknown =>
   }

  def receive = registration orElse processReceive
}
