package boot

import com.typesafe.config.ConfigFactory
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import routing._
import spray.routing.HttpServiceActor


class ServiceActor(val node: Option[org.elasticsearch.node.Node]) extends HttpServiceActor
  with StoredQueryRoute
  with SearchPreviewRoute
  with ChineseAnalyzerRoute {

  val client = node.map { _.client }.getOrElse(new TransportClient().addTransportAddress(new InetSocketTransportAddress("127.0.0.1", 9300)))

  lazy val clusterClient = {
    ClusterBoot.client(ConfigFactory.load("rest"))(context.system)
  }

  def receive = runRoute(queryTemplateRoute ~ `_search/preview`)

}
