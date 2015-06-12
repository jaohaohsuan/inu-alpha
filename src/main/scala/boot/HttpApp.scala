package boot

import akka.actor.ActorRef
import routing._
import spray.routing.HttpServiceActor

class ServiceActor(clusterClientRef: ActorRef) extends HttpServiceActor
  with SearchTemplateRoute {

  def receive = runRoute(queryTemplateRoute)

  override def clusterClient: ActorRef = clusterClientRef
}
