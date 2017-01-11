package com.inu.frontend

import akka.actor.{Actor, ActorLogging, DeadLetter}

/**
  * Created by henry on 1/11/17.
  */
class ClusterDoctor extends  Actor with ActorLogging {

  def receive: Receive = {
    case DeadLetter(msg, from, to) if s"$msg" == "InitJoin" =>
      log.error(s"$msg")
      sys.exit(1)
    case DeadLetter(msg, from, to) =>
      //log.warning(s"$msg")
  }
}
