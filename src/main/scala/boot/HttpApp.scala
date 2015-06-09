package boot

import routing._
import spray.routing.HttpServiceActor

class ServiceActor extends HttpServiceActor
  with QueryTemplateRoute {

  def receive = runRoute(queryTemplateRoute)
}
