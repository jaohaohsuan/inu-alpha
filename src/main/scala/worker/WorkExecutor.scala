package worker

import akka.actor.Actor

class WorkExecutor extends Actor {
  def receive = {
    case x =>
      sender() ! Worker.WorkComplete(x)
  }
}
