package com.inu.frontend.directive

import org.elasticsearch.action.get.GetResponse
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.routing._
import shapeless._

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import com.inu.frontend.elasticsearch.ImplicitConversions._
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchResponse}
import org.elasticsearch.index.query.{BoolQueryBuilder, MatchQueryBuilder, QueryBuilders}
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.search.SearchHits
import org.json4s.JsonAST.{JField, JString}
import spray.routing.directives.ParamDefMagnet

/**
  * Created by henry on 6/19/16.
  */
trait StoredQueryDirectives extends Directives {

  implicit def executionContext: ExecutionContext
  implicit def client: org.elasticsearch.client.Client

  implicit def toSeq(p: Option[String]): Seq[String] = ("""\w+""".r findAllIn p.getOrElse("")).toSeq

  implicit class BoolQueryOps(b: BoolQueryBuilder) {

    def excludeTemporary: BoolQueryBuilder = b.mustNot(QueryBuilders.idsQuery(".percolator").addIds("temporary"))

    def search_all(value: Option[String]): BoolQueryBuilder = {
      value match {
        case Some(s) if s.nonEmpty => b.must(QueryBuilders.queryStringQuery(s).field("_all"))
        case _ => b
      }
    }

    def matchQuery(field: String, value: Option[String]): BoolQueryBuilder = {
      value match {
        case Some(s) if s.nonEmpty => b.must(QueryBuilders.matchQuery(field, s).operator(MatchQueryBuilder.Operator.OR))
        case _ => b
      }
    }
  }

  def item (id: String): Directive1[JValue] = {
    val f: Future[GetResponse] = client.prepareGet("stored-query", ".percolator", id).setFetchSource(Array("item", "occurs", "query"), null).execute().future
    onComplete(f).flatMap {
      case scala.util.Success(res) => provide(parse(res.getSourceAsString()))
      case _ => reject
    }
  }

  def percolate(gr: GetResponse) = {
    parameters("_id".?).flatMap {
      case storedQueryId => {
        provide(client.preparePercolate()
          .setIndices("stored-query")
          .setDocumentType(gr.getType)
          .setSource(s"""{
                         |    "filter" : { "ids" : { "type" : ".percolator", "values" : [ "${storedQueryId.getOrElse("")}" ] } },
                         |    "doc" : ${gr.getSourceAsString},
                         |    "size" : 10,
                         |    "highlight" : {
                         |        "pre_tags" : ["<c>"],
                         |        "post_tags" : ["</c>"],
                         |        "require_field_match" : true,
                         |        "fields" : {
                         |            "agent*" :    { "number_of_fragments" : 0},
                         |            "customer*" : { "number_of_fragments" : 0},
                         |            "dialogs" :   { "number_of_fragments" : 0}
                         |        }
                         |    }
                         |}""".stripMargin))
      }
    }
  }

  def prepareSearchPercolator3: Directive1[SearchRequestBuilder] = {
    import QueryBuilders._
    parameters('conditionSet.?, 'include.?).hflatMap {
      case conditionSet :: include :: HNil =>
        val query = boolQuery().excludeTemporary
        provide(client.prepareSearch("stored-query").setTypes(".percolator")
          .setQuery(conditionSet ++ (include : Seq[String]) match {
            case Nil => query
            case ids => query.must(QueryBuilders.idsQuery(".percolator").addIds(ids))
          })
          .setFetchSource(Array("query", "title"), null))
    }
  }

  def prepareSearchPercolator: Directive1[SearchRequestBuilder] = {
    parameters('q.?, 'tags.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ).hflatMap {
      case q :: tags :: size :: from :: HNil =>
        provide(
          client.prepareSearch("stored-query").setTypes(".percolator")
            .setQuery(boolQuery()
              .excludeTemporary
              .search_all(q)
              .matchQuery("tags", tags))
            .setFetchSource(Array("item"), null)
            .setSize(size).setFrom(from)
        )
    }

  }

//  def format(hits: SearchHits): Directive1[Map[String, JValue]] = {
//    provide(hits.map { hit => hit.id() -> parse(hit.getSourceAsString()) } toMap)
//  }

}
