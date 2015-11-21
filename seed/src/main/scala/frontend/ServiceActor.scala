package frontend

import spray.routing.HttpServiceActor
import spray.routing.authentication.BasicAuth

class ServiceActor(implicit val client: org.elasticsearch.client.Client) extends HttpServiceActor with ImplicitHttpServiceLogging
  with CorsSupport
  with storedQuery.StoredQueryRoute
  with storedFilter.StoredFilterRoute
  with mapping.MappingRoute
  with analysis.AnalysisRoute
  with external.river.ImportRoute
  with logs.LogsRoute {

  def receive = runRoute(
    cors {
      `_query/template/` ~
        `_filter/` ~
        `_mapping/` ~
        `_analysis` ~
        `logs-*` ~
        authenticate(BasicAuth("river")) { username =>
          `_import`
        }
    }
  )
}
