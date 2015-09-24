package frontend

import spray.routing.HttpServiceActor

class ServiceActor(implicit val client: org.elasticsearch.client.Client) extends HttpServiceActor with CorsSupport with storedQuery.StoredQueryRoute with mapping.MappingRoute {

  def receive = runRoute(
    cors {
      `_query/template/` ~
      `_mapping/`
    }
  )
}
