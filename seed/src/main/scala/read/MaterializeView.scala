package read

import akka.actor.{Actor, ActorSystem}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import common.ImplicitActorLogging

trait MaterializeView extends Actor with ImplicitActorLogging {

  implicit val system: ActorSystem = context.system

  implicit val mat = ActorMaterializer()(context)

  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  implicit val executor = system.dispatcher

}
