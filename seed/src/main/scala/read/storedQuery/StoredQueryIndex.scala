package read.storedQuery

import elastic.ImplicitConversions._
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder
import org.elasticsearch.action.get.GetRequestBuilder
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.query.QueryBuilders

import scala.concurrent.{ExecutionContextExecutor, Future}

object StoredQueryIndex {

  val index = "stored-query"

  val settings = ImmutableSettings.settingsBuilder()
    //.put("discovery.zen.ping.multicast.enabled", false)
    //.putArray("discovery.zen.ping.unicast.hosts", "192.168.99.100:32772")
    //.put("node.client", true)
    .put("cluster.name", "inu-dc")

  /*lazy val node = org.elasticsearch.node.NodeBuilder.nodeBuilder().settings(settings).node()
  implicit lazy val client = node.client()*/


  implicit lazy val client: Client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("192.168.99.100",32774))

  val temporaryIdsQuery =  QueryBuilders.idsQuery(".percolator").ids("temporary")

  def prepareGet(id: String): GetRequestBuilder = {
    client.prepareGet(index, ".percolator", id)
  }

  def save(storedQueryId: String, json: String)(implicit client: Client, ctx: ExecutionContextExecutor): Future[IndexResponse] = {
    client.prepareIndex(index, ".percolator", storedQueryId).setSource(json).execute().asFuture
  }


  def putSourceMapping(name: String)(implicit client: Client): PutMappingRequestBuilder = {

    import org.json4s.JsonAST.{JField, JObject}
    import org.json4s.JsonDSL._
    import org.json4s.native.JsonMethods._

    import scala.language.implicitConversions

    implicit def field(name: String): JField = name -> (("type" -> "string") ~ ("analyzer" -> "whitespace"))

    val `dialogs, customer*, agent*`: JObject =
      "properties" -> (0 to 5).foldLeft(JObject("dialogs")) { (acc, n) => acc ~ s"customer$n" ~ s"agent$n" }

    client.admin().indices().preparePutMapping(index)
      .setType(name)
      .setSource(compact(render(`dialogs, customer*, agent*`)))
  }

  def preparePutMapping(implicit client: Client): PutMappingRequestBuilder = {

    val mappingSource = """{
            |  "properties": {
            |    "referredClauses" : { "type" : "string" },
            |    "title" :           { "type" : "string" },
            |    "tags" :            { "type" : "string", "include_in_all" : false },
            |    "keywords" :        { "type" : "string" },
            |    "view" :            { "type" : "object", "enabled" : false },
            |    "occurs" :          { "type" : "object", "enabled" : false }
            |  }
            |}
          """.stripMargin

    client.admin().indices().preparePutMapping(index)
      .setType(".percolator")
      .setSource(mappingSource)
  }

  def prepareSearch(implicit client: Client): SearchRequestBuilder = {
    //import org.elasticsearch.index.query.QueryBuilders
    client.prepareSearch(index).setTypes(".percolator")
  }
}
