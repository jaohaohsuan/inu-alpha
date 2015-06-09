package worker

import akka.actor.Actor

/**
 * Created by henry on 6/6/15.
 */
class WorkExecutor extends Actor {
  def receive = {
    case x =>
      sender() ! Worker.WorkComplete(x)
  }
}
