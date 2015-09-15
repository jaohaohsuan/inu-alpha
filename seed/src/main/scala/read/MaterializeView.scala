package read

import akka.actor.{Actor, ActorLogging}
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.journal.leveldb.LeveldbReadJournal
import akka.stream.ActorMaterializer

trait MaterializeView extends Actor with ActorLogging {

  import context.system

  implicit val mat = ActorMaterializer()(context)

  val readJournal = PersistenceQuery(system).readJournalFor(LeveldbReadJournal.Identifier)

}
