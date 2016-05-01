package domain

import akka.actor.ActorRef
import protocol.storedQuery._
import scala.language.implicitConversions

/**
  * Created by henry on 4/30/16.
  */
case object Initial2

case class ItemCreated2(id: String, refId: Option[String], title: String, tags: List[String]) extends Event {
  require(title.nonEmpty && id.nonEmpty)
}

case class ClauseAdded(storedQueryId: String, boolClause: (Int,BoolClause)) extends Event
case class ClauseRemoved(storedQueryId: String, boolClauses: Map[Int,BoolClause]) extends Event

case class PersistedAck(receiver: ActorRef, message: Option[Any] = None) {
  def send(evt: Event) = receiver ! message.getOrElse(evt)
}