package com.inu.cluster.storedquery.messages

import akka.actor.ActorRef
import com.inu.protocol.storedquery.messages.BoolClause

import scala.language.implicitConversions


/**
  * Created by henry on 4/30/16.
  */

trait Event extends Serializable
trait State extends Serializable


// events
case class ItemCreated(id: String, title: String, refId: Option[String] = None, tags: Set[String] = Set.empty) extends Event {
  require(title.nonEmpty && id.nonEmpty)
}

case class ItemUpdated(id: String, title: String, tags: Set[String]) extends Event

case class ClauseAdded(storedQueryId: String, boolClause: (Int,BoolClause)) extends Event
case class ClauseRemoved(storedQueryId: String, boolClauses: Map[Int,BoolClause]) extends Event

// others
case class PersistedAck(receiver: ActorRef, message: Option[Any] = None) {
  def send(evt: Event) = receiver ! message.getOrElse(evt)
}