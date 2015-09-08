package seed

import akka.actor.{PoisonPill, Props, Actor}
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxySettings, ClusterSingletonProxy, ClusterSingletonManagerSettings, ClusterSingletonManager}
import akka.stream.ActorMaterializer
import protocol.storedQuery.AggregateRoot
import seed.domain.StoredQueryAggregateRoot.CreateNewStoredQuery

object Configurator {
  val Name = "conf"
}

class Configurator extends Actor with SharedLeveldbStoreUsage {
  import context.system

   def processReceive: Receive = {
     case LeveldbStoreRegistration(m) =>
       log.info("LeveldbJournalReady")

        if(m.hasRole("compute")) {
          system.actorOf(ClusterSingletonManager.props(
            singletonProps = Props(classOf[domain.StoredQueryAggregateRoot]),
            terminationMessage = PoisonPill,
            settings = ClusterSingletonManagerSettings(system)),
            name = AggregateRoot.Name)
        }

       if(m.hasRole("web")){
         system.log.info("web sending")
         system.actorOf(ClusterSingletonProxy.props(
           singletonManagerPath = s"/user/${AggregateRoot.Name}",
           settings = ClusterSingletonProxySettings(system)
         ), name = "aggregateRootProxy") ! CreateNewStoredQuery("new1", None, Set.empty)
       }

       //ClusterClientReceptionist(system).registerService(storedQueryAggregateRoot)

     case unknown =>
   }

  def receive = registration orElse processReceive
}
