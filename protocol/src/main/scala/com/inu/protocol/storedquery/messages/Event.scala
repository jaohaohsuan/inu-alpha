package com.inu.protocol.storedquery.messages

trait Event extends Serializable

// events
case class ItemCreated(id: String, title: String, refId: Option[String] = None, tags: Set[String] = Set.empty) extends Event {
  require(title.nonEmpty && id.nonEmpty)
}

case class ItemUpdated(id: String, title: String, tags: Set[String]) extends Event

case class ClauseAdded(storedQueryId: String, boolClause: (Int,BoolClause)) extends Event
case class ClauseRemoved(storedQueryId: String, boolClauses: Map[Int,BoolClause]) extends Event