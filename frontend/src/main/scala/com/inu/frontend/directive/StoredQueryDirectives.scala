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
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms
import org.elasticsearch.search.aggregations.{Aggregation, AggregationBuilders, Aggregations}
import org.elasticsearch.search.{SearchHit, SearchHits}
import org.json4s._

import scala.util.Failure

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
      case _id => {
        val ids = _id.map{ raw => raw.split("""[\+\s]""").map{ id => s""""$id"""" }.mkString(",") }.getOrElse("")
        provide(client.preparePercolate()
          .setIndices("stored-query")
          .setDocumentType(gr.getType)
          .setSource(s"""{
                         |    "filter" : { "ids" : { "type" : ".percolator", "values" : [ $ids ] } },
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

  def `conditionSet+include`: Directive1[SearchRequestBuilder] = {
    import QueryBuilders._
    parameters('conditionSet.?, 'include.?).hflatMap {
      case conditionSet :: include :: HNil =>
        val noReturnQuery = boolQuery().mustNot(matchAllQuery())
        val query = boolQuery().excludeTemporary
        provide(client.prepareSearch("stored-query").setTypes(".percolator")
          .setQuery(conditionSet ++ (include : Seq[String]) match {
            case Nil => query.should(noReturnQuery)
            case ids => query.must(QueryBuilders.idsQuery(".percolator").addIds(ids))
          })
          .setFetchSource(Array("query", "title"), null))
    }
  }

  def tags: Directive1[String]  = {
    val f = client.prepareSearch("stored-query").setTypes(".percolator")
      .setSize(0)
      .addAggregation(AggregationBuilders.terms("tags").field("tags"))
        .execute().future.map { res => res.getAggregations.get[StringTerms]("tags").getBuckets.map { bucket => bucket.getKeyAsString }.mkString(" ") }

    onComplete(f).flatMap {
      case scala.util.Success(value) => provide(value)
      case scala.util.Failure(ex) => provide("")
    }
  }


//  def count(conditions: Map[String, Condition]) = {
//    parameters('conditionSet.?, 'include.?).hflatMap {
//      case excludedIds :: includedIds :: HNil =>
//        val conditionSet =  ConditionSet(excludedIds)
//        //Future.traverse(excludedIds) { id => conditionSet.exclude(id).count() }
//        //Future.traverse(includedIds) { id => conditionSet.include(id).count() }
//
//
//        provide("")
//    }
//
//  }

//  val items = JField("items", JArray((ConditionSet(conditionSetIds).all :: storedQueries.values.toList).map {
//    case Condition(id, title, _, state, _, hits) =>
//      Template(Map("title" -> title, "state" -> state, "hits" -> hits)).template ~~ ("links" -> Nil)
//  } ))

  def prepareSearchPercolator: Directive1[SearchRequestBuilder] = {
    parameters('conditionSet.?, 'include.?, 'q.?, 'tags.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ).hflatMap {
      case conditionSet :: include :: q :: tags :: size :: from :: HNil =>
        val excludedIds = (conditionSet: Seq[String]) ++ include
        provide(
          client.prepareSearch("stored-query").setTypes(".percolator")
            .setQuery(boolQuery()
              .excludeTemporary
              .search_all(q)
              .matchQuery("tags", tags)
              .mustNot(idsQuery().ids(excludedIds)))
            .setFetchSource(Array("item"), null)
            .setSize(size).setFrom(from)
        )
    }
  }
}
