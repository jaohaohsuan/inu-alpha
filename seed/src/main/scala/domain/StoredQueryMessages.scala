package domain

import akka.actor.ActorRef
import protocol.storedQuery._
import scala.language.implicitConversions


/**
  * Created by henry on 4/30/16.
  */

// commands
case object Initial2
case class CreateNewStoredQuery(title: String, referredId: Option[String], tags: Set[String]) extends Command
case class AddClause(storedQueryId: String, clause: BoolClause) extends Command
case class RemoveClauses(storedQueryId: String, specified: List[Int]) extends Command
case class ResetOccurrence(storedQueryId: String, occur: String) extends Command {
  require(occur.matches(protocol.elastics.boolQuery.OccurrenceRegex.toString()))
}

// events
case class ItemCreated2(id: String, refId: Option[String], title: String, tags: List[String]) extends Event {
  require(title.nonEmpty && id.nonEmpty)
}

case class ClauseAdded(storedQueryId: String, boolClause: (Int,BoolClause)) extends Event
case class ClauseRemoved(storedQueryId: String, boolClauses: Map[Int,BoolClause]) extends Event

// others
case class PersistedAck(receiver: ActorRef, message: Option[Any] = None) {
  def send(evt: Event) = receiver ! message.getOrElse(evt)
}