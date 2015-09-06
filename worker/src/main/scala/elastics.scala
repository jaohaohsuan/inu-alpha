package worker

object elastics {

  lazy val node = org.elasticsearch.node.NodeBuilder.nodeBuilder().node()

  lazy val client = com.sksamuel.elastic4s.ElasticClient.fromNode(node)
}
