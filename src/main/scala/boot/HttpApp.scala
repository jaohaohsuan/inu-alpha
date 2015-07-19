package boot

import com.typesafe.config.ConfigFactory
import routing._
import spray.routing.HttpServiceActor


class ServiceActor extends HttpServiceActor with StoredQueryRoute with SearchPreviewRoute {

  lazy val clusterClient = {
    ClusterBoot.client(ConfigFactory.load("rest"))(context.system)
  }

  def receive = runRoute(queryTemplateRoute ~ `_search/preview`)

}
