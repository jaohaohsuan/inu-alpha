package read

import akka.actor.Actor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import common.ImplicitActorLogging

trait MaterializeView extends Actor with ImplicitActorLogging {

  import context.system

  implicit val mat = ActorMaterializer()(context)

  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

}
