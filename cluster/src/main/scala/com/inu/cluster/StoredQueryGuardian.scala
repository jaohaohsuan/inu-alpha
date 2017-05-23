package com.inu.cluster

import akka.actor.{Actor, ActorInitializationException, ActorLogging, OneForOneStrategy, Props}

import scala.concurrent.duration._

/**
  * Created by henry on 5/22/17.
  */
class StoredQueryGuardian extends Actor with ActorLogging {

  import akka.actor.SupervisorStrategy._

  override def supervisorStrategy = OneForOneStrategy(withinTimeRange = 5 minute,loggingEnabled = true) {
    case _: ActorInitializationException =>
      Restart
    case _: Exception =>
      Restart
  }

  override def receive = {
    case (p: Props, name: String) => sender() ! context.actorOf(p,name)
  }
}
