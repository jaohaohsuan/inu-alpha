package seed

import akka.actor.{PoisonPill, Props, Actor}
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxySettings, ClusterSingletonProxy, ClusterSingletonManagerSettings, ClusterSingletonManager}
import akka.io.IO
import akka.stream.ActorMaterializer
import akka.pattern._
import akka.util.Timeout
import protocol.storedQuery.AggregateRoot
import seed.domain.storedQuery.StoredQueryAggregateRoot
import spray.can.Http

object Configurator {
  val Name = "conf"
}

class Configurator extends Actor with SharedLeveldbStoreUsage {
  import context.system

   def processReceive: Receive = {
     case LeveldbStoreRegistration(m) if m.hasRole("compute") =>
       system.actorOf(ClusterSingletonManager.props(
         singletonProps = Props(classOf[StoredQueryAggregateRoot]),
         terminationMessage = PoisonPill,
         settings = ClusterSingletonManagerSettings(system)),
         name = AggregateRoot.Name)

     case LeveldbStoreRegistration(m) if m.hasRole("web") => {
       system.log.info("web sending")
       val r =  system.actorOf(ClusterSingletonProxy.props(
         singletonManagerPath = s"/user/${AggregateRoot.Name}",
         settings = ClusterSingletonProxySettings(system)
       ), name = "aggregateRootProxy")
       log.info(s"${r.path}")

       import frontend.ServiceActor
       import scala.concurrent.duration._

       val service = system.actorOf(Props(classOf[ServiceActor]), "service")

       implicit val timeout = Timeout(5.seconds)
       IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = frontend.Config.port)

       // persistence query
       import akka.persistence.query.PersistenceQuery
       import akka.persistence.query.journal.leveldb.LeveldbReadJournal

       val readJournal = PersistenceQuery(system).readJournalFor(LeveldbReadJournal.Identifier)

       import akka.persistence.query.EventsByPersistenceId
       import akka.stream.scaladsl.Source
       import akka.persistence.query.EventEnvelope

       val source: Source[EventEnvelope, Unit] =
         readJournal.query(EventsByPersistenceId(AggregateRoot.Name))

       implicit val mat = ActorMaterializer()(system)
       source
         //.mapAsync(2) { envelope => Future { envelope } }
         .runForeach { envelope => println(s"Event: ${envelope.event}")}
     }
       //ClusterClientReceptionist(system).registerService(storedQueryAggregateRoot)

     case unknown =>
   }

  def receive = registration orElse processReceive
}
