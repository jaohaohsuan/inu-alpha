package com.inu.protocol.storedquery.messages

case class StoredQueryCreatedAck(id: String)
case class ClauseAddedAck(clauseId: String)
case object UpdatedAck
case object ClausesRemovedAck

case class RejectAck(reason: String)