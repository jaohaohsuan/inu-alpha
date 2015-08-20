package elastics

/**
 * Created by henry on 20/08/2015.
 */
object Cluster {

  lazy val node = org.elasticsearch.node.NodeBuilder.nodeBuilder().node()

  lazy val client = node.client()

  lazy val `4s client` = com.sksamuel.elastic4s.ElasticClient.fromNode(elastics.Cluster.node)
}
