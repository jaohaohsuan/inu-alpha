package com.inu.cluster.storedquery.messages

import akka.actor.ActorRef
import com.inu.protocol.storedquery.messages.Event

import scala.language.implicitConversions


/**
  * Created by henry on 4/30/16.
  */


trait State extends Serializable


// others
case class PersistedAck(receiver: ActorRef, message: Option[Any] = None) {
  def send(evt: Event) = receiver ! message.getOrElse(evt)
}