package com.inu.protocol.storedquery.messages

trait Ack extends Serializable

case class StoredQueryCreatedAck(id: String) extends  Ack
case class ClauseAddedAck(clauseId: String) extends  Ack
case object UpdatedAck extends Ack
case object ClausesRemovedAck extends Ack

case class RejectAck(reason: String) extends  Ack