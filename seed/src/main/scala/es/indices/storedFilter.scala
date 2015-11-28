package es.indices

import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder
import org.elasticsearch.action.get.GetRequestBuilder
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.client.Client
import elastic.ImplicitConversions._
import org.elasticsearch.index.query.{MatchQueryBuilder, BoolQueryBuilder}
import org.elasticsearch.index.query.QueryBuilders._
import scala.concurrent.{Future, ExecutionContextExecutor}


object storedFilter {
  val index = "stored-filter"

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

  def buildQueryDefinition(queryString: Option[String] = None): BoolQueryBuilder = Seq(
    queryString.map { queryStringQuery(_).field("_all") }
  ).flatten.foldLeft(boolQuery())(_ must _)
  //.mustNot(temporaryIdsQuery).mustNot(temporaryIdsQuery)

  def prepareGet(typ: String, id: String)(implicit client: Client): GetRequestBuilder = {
    client.prepareGet(index, typ, id)
  }

  def prepareSearch(typ: String)(implicit client: Client): SearchRequestBuilder = {
    //import org.elasticsearch.index.query.QueryBuilders
    client.prepareSearch(index).setTypes(typ)
  }

  def index(id: String, typ: String, json: String)(implicit client: Client, ctx: ExecutionContextExecutor): Future[IndexResponse] = {
    client.prepareIndex(index, typ, id).setSource(json).execute().future
  }

  def update(id: String, typ: String, json: String)(implicit client: Client, ctx: ExecutionContextExecutor): Future[UpdateResponse] =
    client.prepareUpdate(index, typ, id).setDoc(json).execute().future

}
