package com.inu.frontend

import akka.actor.{Actor, ActorLogging, ActorRef, DeadLetter, PossiblyHarmful}
import akka.cluster.ClusterMessage

import scala.concurrent.duration._


/**
  * Created by henry on 1/11/17.
  */
object ClusterDoctor {
  implicit class behaves(actor: ActorRef) {
    def joinSeedNodeProcess: Boolean = {
      actor.path.toString.matches("""akka://storedq/system/cluster/core/daemon/joinSeedNodeProcess.*""")
    }
  }
  case class RetryTimes(times: Int)
  case object Probe

}
class ClusterDoctor extends  Actor with ActorLogging {

  import ClusterDoctor._

  implicit val ec = context.system.dispatcher

  context.system.scheduler.schedule(0.seconds, 1.minutes, self, Probe)

  private var retry = RetryTimes(1)

  def receive: Receive = {
    case Probe =>
      retry = RetryTimes(0)

    case RetryTimes(c) if 5 <= c =>
      sys.exit(1)

    case state: RetryTimes =>
      retry = state
      log.info(s"dead letter keep sending ${retry.times} times")

    case DeadLetter(x: PossiblyHarmful, _, _) =>
      self ! retry.copy(times = retry.times + 1)

    case DeadLetter(cm :ClusterMessage, sender, to) if sender.joinSeedNodeProcess =>
      self ! retry.copy(times = retry.times + 1)

    case _ =>
  }

//  def receive: Receive = {
//    case DeadLetter(msg, from, to) if s"$msg" == "InitJoin" =>
//      log.error(s"$msg")
//      sys.exit(1)
//    case DeadLetter(msg, from, to) =>
//      //log.warning(s"$msg")
//  }
}
