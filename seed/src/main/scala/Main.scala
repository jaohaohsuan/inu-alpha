package seed

import akka.actor._
import common._

object Main extends App {

  val nodeConfig = NodeConfig parse args

  nodeConfig map { c =>
    implicit val system = ActorSystem(c.clusterName, c.config)

    system.actorOf(Props[Configurator], Configurator.Name)

    if(c.isEventsStore)
      system.actorOf(Props[LeveldbJournalListener])


    system.log info s"ActorSystem ${system.name} started successfully"
  }
}
