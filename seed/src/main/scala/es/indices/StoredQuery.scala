package es.indices

import elastic.ImplicitConversions._
import org.elasticsearch.action.ListenableActionFuture
import org.elasticsearch.action.admin.indices.exists.indices.{IndicesExistsRequestBuilder, IndicesExistsResponse}
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder
import org.elasticsearch.action.get.{GetRequest, GetRequestBuilder}
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilders

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object storedQuery {

  val index = "stored-query"

  def exists(implicit client: Client): IndicesExistsRequestBuilder = client.admin().indices().prepareExists(index)

  def create(implicit client: Client) =
    client.admin().indices()
      .prepareCreate(index).setSettings(
        """{
          | "index" : {
          |   "number_of_shards" : 1,
          |   "number_of_replicas" : 1
          | }
          |}""".stripMargin)
      .execute()


  //val settings = ImmutableSettings.settingsBuilder()
    //.put("discovery.zen.ping.multicast.enabled", false)
    //.putArray("discovery.zen.ping.unicast.hosts", "192.168.99.100:32772")
    //.put("node.client", true)
    //.put("cluster.name", "inu-dc")

  /*lazy val node = org.elasticsearch.node.NodeBuilder.nodeBuilder().settings(settings).node()
  implicit lazy val client = node.client()*/


 /* implicit lazy val client: Client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("192.168.99.100",32774))*/

  val temporaryIdsQuery =  QueryBuilders.idsQuery(".percolator").ids("temporary")

  def prepareGet(id: String)(implicit client: Client): GetRequestBuilder = {
    client.prepareGet(index, ".percolator", id)
  }

  def getSourceOf(id: String, field: String)(implicit client: Client, ctx: ExecutionContext)= {
    import org.json4s._
    import org.json4s.native.JsonMethods._

    prepareGet(id)
    .setFetchSource(Array(field), null)
      .setTransformSource(true)
      .execute().asFuture
      .map{ r => compact(render(parse(r.getSourceAsString) \ field)) }
  }

  def save(storedQueryId: String, json: String)(implicit client: Client, ctx: ExecutionContextExecutor) =
    client.prepareIndex(index, ".percolator", storedQueryId).setSource(json).execute().asFuture


  def putSourceMapping(`type`: String)(implicit client: Client) = {

    import org.json4s.JsonAST.{JField, JObject}
    import org.json4s.JsonDSL._
    import org.json4s.native.JsonMethods._

    import scala.language.implicitConversions

    implicit def field(name: String): JField = name -> (("type" -> "string") ~ ("analyzer" -> "whitespace_stt_analyzer"))

    val `dialogs, customer*, agent*`: JObject =
      "properties" -> (0 to 9).foldLeft(JObject("dialogs")) { (acc, n) => acc ~ s"customer$n" ~ s"agent$n" }

    client.admin().indices().preparePutMapping(index)
      .setType(`type`)
      .setSource(compact(render(`dialogs, customer*, agent*`)))
      .execute()
  }

  def mapping(implicit client: Client) = {

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
      .execute()
  }

  def mapping(`type`: String)(implicit client: Client) = {

    val fields = (0 to 9).flatMap(n => Seq(
      s""""agent$n" :    {  "type": "string", "analyzer": "whitespace_stt_analyzer" }""",
      s""""customer$n" : {  "type": "string", "analyzer": "whitespace_stt_analyzer" }"""
    )) + """"dialogs" :  {  "type": "string", "analyzer": "whitespace_stt_analyzer" }"""

    val mappingSource =
      s"""{
        | "properties" : { ${fields.mkString(",")} }
        |}""".stripMargin

    println(mappingSource)

    client.admin().indices().preparePutMapping(index)
      .setType(`type`)
      .setSource(mappingSource)
      .execute()
  }

  def prepareSearch(implicit client: Client): SearchRequestBuilder = {
    //import org.elasticsearch.index.query.QueryBuilders
    client.prepareSearch(index).setTypes(".percolator")
  }

  def preparePercolate(typ: String)(implicit client: Client) =
    client.preparePercolate()
      .setIndices(index)
      .setDocumentType(typ)

}
