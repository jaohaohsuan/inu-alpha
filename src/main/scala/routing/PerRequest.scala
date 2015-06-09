package routing

import akka.actor._
import spray.http.StatusCodes._
import spray.routing._
import scala.concurrent.duration._

object PerRequest {
  case object Ok
  case object NotOk
}

trait PerRequest extends Actor with Directives {

  import context._

  setReceiveTimeout(10.seconds)

  def ctx: RequestContext

  def processResult: Receive

  def receive = processResult orElse defaultReceive

  private def defaultReceive: Receive = {
    case ReceiveTimeout =>
      response {
        complete(RequestTimeout)
      }
    case res =>
      response {
        complete(InternalServerError, res.toString)
      }
  }

  def response(finalStep: Route): Unit = {
    finalStep(ctx)
    stop(self)
  }
}
