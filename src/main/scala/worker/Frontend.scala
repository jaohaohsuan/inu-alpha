package worker

import akka.actor.Actor
import akka.contrib.pattern.ClusterSingletonProxy
import akka.util.Timeout
import akka.pattern._
import scala.concurrent.duration._

object Frontend {
  case object Ok
  case object NotOk
}

class Frontend extends Actor {
  import Frontend._
  import context.dispatcher

  val masterProxy = context.actorOf(ClusterSingletonProxy.props(
    singletonPath = "/user/master/active",
    role = Some("backend")
  ), name = "masterProxy")

  def receive = {
    case work =>
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? work).map {
        case Master.Ack(_) => Ok
      }.recover { case _ => NotOk } pipeTo sender()
  }
}
