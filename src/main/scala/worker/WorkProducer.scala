package worker

import akka.actor.{ReceiveTimeout, Actor, ActorLogging, ActorRef}
import java.util.UUID


import scala.concurrent.duration._
import scala.concurrent.forkjoin.ThreadLocalRandom

class WorkProducer(frontend: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher

  def scheduler = context.system.scheduler
  def rnd = ThreadLocalRandom.current
  def nextWorkId(): String = UUID.randomUUID().toString

  def receive = {
    case _ =>
      val work = Work(nextWorkId(), rnd.nextInt(1,10))
      frontend ! work
      context.become(waitAccepted(work), discardOld = false)
  }

    def waitAccepted(work: Work): Receive = {
      case Frontend.Ok =>
        context.unbecome()
      case Frontend.NotOk =>
        log.info("Work not accepted, retry after a while")
        scheduler.scheduleOnce(3.seconds, frontend, work)
    }

}



