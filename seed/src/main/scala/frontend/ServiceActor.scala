package frontend

import spray.routing.HttpServiceActor

class ServiceActor(implicit val client: org.elasticsearch.client.Client) extends HttpServiceActor
  with CorsSupport
  with storedQuery.StoredQueryRoute
  with mapping.MappingRoute
  with analysis.AnalysisRoute
  with external.river.ImportRoute {

  def receive = runRoute(
    cors {
      `_query/template/` ~
      `_mapping/` ~
      `_analysis` ~
      `_import`
    }
  )
}
