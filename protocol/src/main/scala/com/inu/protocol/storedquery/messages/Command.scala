package com.inu.protocol.storedquery.messages

import com.inu.protocol.storedquery.messages.BoolClause._

trait Command

// commands
case object Initial extends Command
case class CreateNewStoredQuery(title: String, referredId: Option[String], tags: Set[String]) extends Command
case class UpdateStoredQuery(storedQueryId: String, title: String, tags: Option[String]) extends Command
case class AddClause(storedQueryId: String, clause: BoolClause) extends Command
case class RemoveClauses(storedQueryId: String, specified: List[Int]) extends Command
case class ResetOccurrence(storedQueryId: String, occur: String) extends Command {
  require(occur.matches(OccurrenceRegex.toString()))
}