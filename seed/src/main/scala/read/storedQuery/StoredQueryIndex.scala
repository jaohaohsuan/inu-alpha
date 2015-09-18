package read.storedQuery

import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.mapping.put.{PutMappingRequestBuilder, PutMappingResponse}
import org.elasticsearch.action.get.{GetRequest, GetRequestBuilder, GetResponse}
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.{SearchRequest, SearchRequestBuilder, SearchResponse}
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.query.QueryBuilders

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}

object StoredQueryIndex {

  val index = "stored-query"

  /* val node = org.elasticsearch.node.NodeBuilder.nodeBuilder().settings(settings).node()
 node.client()*/

  val settings = ImmutableSettings.settingsBuilder()
    //.put("node.client", true)
    .put("cluster.name", "inu-dc")

  implicit lazy val client: Client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("192.168.99.100", 32774))

  val temporaryIdsQuery =  QueryBuilders.idsQuery(".percolator").ids("temporary")

  def prepareGet(id: String): GetRequestBuilder = {
    client.prepareGet(index, ".percolator", id)
  }

  def search(request: SearchRequest)(implicit client: Client, ctx: ExecutionContextExecutor) = {
    val p = Promise[SearchResponse]()
    val listener = new ActionListener[SearchResponse] {
      def onFailure(e: Throwable): Unit = p.tryFailure(e)
      def onResponse(resp: SearchResponse): Unit = p.trySuccess(resp)
    }
    client.search(request, listener)
    p.future
  }

  def get(request: GetRequest)(implicit client: Client, ctx: ExecutionContextExecutor) = {
    val p = Promise[GetResponse]()
    val listener = new ActionListener[GetResponse] {
      def onFailure(e: Throwable): Unit = p.tryFailure(e)
      def onResponse(resp: GetResponse): Unit = p.trySuccess(resp)
    }
    client.get(request, listener)
    p.future
  }

  def save(value: (String, String))(implicit client: Client, ctx: ExecutionContextExecutor): Future[IndexResponse] = {

    val (storedQueryId, json) = value
    val indexRequest = client.prepareIndex(index, ".percolator", storedQueryId)
      .setSource(json).request()

    val p = Promise[IndexResponse]

    val listener = new ActionListener[IndexResponse] {
      def onFailure(e: Throwable): Unit = {
        //println(e.getMessage)
        p.tryFailure(e)
      }
      def onResponse(resp: IndexResponse): Unit = p.trySuccess(resp)
    }

    client.index(indexRequest, listener)

    p.future
  }

  def putMapping(builder: PutMappingRequestBuilder): Future[PutMappingResponse] = {

    val p = Promise[PutMappingResponse]()

    val listener = new ActionListener[PutMappingResponse] {
      def onFailure(e: Throwable): Unit = p.tryFailure(e)
      def onResponse(resp: PutMappingResponse): Unit = p.trySuccess(resp)
    }

    builder.execute(listener)

    p.future
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
