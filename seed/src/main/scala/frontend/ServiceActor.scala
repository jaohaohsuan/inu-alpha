package frontend

import spray.routing.HttpServiceActor

class ServiceActor extends HttpServiceActor with storedQuery.StoredQueryRoute {

  def receive = runRoute(`_query/template/`)
}
