package routing.request

import akka.actor._
import spray.http.StatusCodes._
import spray.routing._

import scala.concurrent.duration._

object PerRequest {
  case object Ok
  case object NotOk


}

trait PerRequest extends Actor with ActorLogging with Directives {

  import context._
  import PerRequest._

  implicit class stringUri(s: String) {
    def uri: java.net.URI = java.net.URI.create(s)
  }

  val URI = extract(ctx => s"${ctx.request.uri}".uri)


  //setReceiveTimeout(10.seconds)

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
        complete(InternalServerError, s"unexpected message: $res")
      }
  }

  def response(finalStep: Route): Unit = {
    finalStep(ctx)
    stop(self)
  }

  def responseWithoutStopActor(finalStep: Route): Unit = {
    finalStep(ctx)
  }

  override def unhandled(message: Any): Unit = message match {
    case _ =>
      log.info(s"unhandled message: $message")
  }
}
