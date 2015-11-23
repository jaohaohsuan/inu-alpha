package frontend

import org.json4s.{Formats, DefaultFormats}
import spray.routing.HttpServiceActor
import spray.routing.authentication.BasicAuth
import spray.util.LoggingContext

class ServiceActor(implicit val client: org.elasticsearch.client.Client) extends HttpServiceActor
  with CorsSupport
  with storedQuery.StoredQueryRoute
  with storedFilter.StoredFilterRoute
  with mapping.MappingRoute
  with analysis.AnalysisRoute
  with external.river.ImportRoute
  with logs.LogsRoute {

  implicit val executionContext = actorRefFactory.dispatcher
  implicit def json4sFormats = DefaultFormats
  val log = LoggingContext.fromActorRefFactory(actorRefFactory)

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
