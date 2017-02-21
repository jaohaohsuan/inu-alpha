package com.inu.frontend.directive

import com.inu.frontend.elasticsearch.ImplicitConversions._
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentFactory}
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.index.query._
import org.elasticsearch.percolator.PercolateQueryBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.json4s._
import org.json4s.native.JsonMethods._
import shapeless._
import spray.routing._

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

trait StoredQueryDirectives extends Directives {

  implicit def executionContext: ExecutionContext
  implicit def client: org.elasticsearch.client.Client

  implicit def toSeq(p: Option[String]): Seq[String] =  p.getOrElse("").split("""[\s,]+""").toSeq

  implicit class BoolQueryOps(b: BoolQueryBuilder) {

    def excludeTemporary: BoolQueryBuilder = b.mustNot(QueryBuilders.idsQuery("queries").addIds("temporary"))
                                              .mustNot(QueryBuilders.existsQuery("temporary"))

    def search_all(value: Option[String]): BoolQueryBuilder = {
      value match {
        case Some(s) if s.nonEmpty => b.must(QueryBuilders.queryStringQuery(s).field("_all"))
        case _ => b
      }
    }

    def matchQuery(field: String, value: Option[String]): BoolQueryBuilder = {
      value match {
        case Some(s) if s.nonEmpty => b.must(QueryBuilders.matchQuery(field, s).operator(org.elasticsearch.index.query.Operator.OR))
        case _ => b
      }
    }
  }

  def replaceTemporaryId(id: String): Directive1[String] = {
    headerValueByName("uid").flatMap { uid =>
      id match {
        case "temporary" => provide(uid)
        case _ => provide(id)
      }
    }
  }

  def item (id: String): Directive1[JValue] = {
    replaceTemporaryId(id).flatMap { _id =>
      val f = client.prepareGet("stored-query", "queries", _id).setFetchSource(Array("item", "occurs", "query"), null).execute().future
      onComplete(f).flatMap {
        case scala.util.Success(res) if res.isExists =>
          provide(parse(res.getSourceAsString()))
        case _ =>
          throw new Exception(s"StoredQuery resource $id is not exist.")
      }
    }
  }

  def percolate(docType: String, doc: String): Directive1[SearchRequestBuilder] = {
    headerValueByName("uid").flatMap { uid =>
      parameters("_id".?).flatMap { _id =>
          val ids: Seq[String] = _id.getOrElse("").replace("temporary", uid).split("""[\s,]+""").map { id => s""""$id"""" }.toSet.toList

          val docBuilder = XContentFactory.jsonBuilder().startObject()
          docBuilder.value(doc)
          docBuilder.endObject()

          val percolateQuery = new PercolateQueryBuilder("query", docType, docBuilder.bytes())
          val idsQuery = QueryBuilders.idsQuery("queries").addIds(ids: _*)
          val boolQuery = QueryBuilders.boolQuery()
            .must(percolateQuery)
            .filter(idsQuery)

          val hi = new HighlightBuilder()
            .requireFieldMatch(true)
            .numOfFragments(0)
            .preTags("<c>")
            .postTags("</c>")
            .field("agent*")
            .field("customer*")
            .field("dialogs")

          provide(client.prepareSearch("stored-query").setQuery(boolQuery).highlighter(hi))
      }
    }
  }

  def `conditionSet+include+must_not`: Directive1[SearchRequestBuilder] = {
    import QueryBuilders._
    parameters('conditionSet.?, 'include.?, 'must_not.?).hflatMap {
      case conditionSet :: include :: must_not :: HNil =>
        implicit def percolatorIdsQuery(ids: Seq[String]): IdsQueryBuilder = QueryBuilders.idsQuery("queries").addIds(ids:_*)

        val noReturnQuery = boolQuery().mustNot(matchAllQuery())
        val query = boolQuery().excludeTemporary
        val idsQuery = conditionSet ++ (include : Seq[String]) ++ (must_not : Seq[String]) match {
          case Nil => query.should(noReturnQuery)
          case ids => query.must(ids.distinct)
        }
        provide(client.prepareSearch("stored-query").setTypes("queries")
          .setQuery(idsQuery)
          .setFetchSource(Array("query", "title"), null))
    }
  }

  def tags: Directive1[String]  = {
    headerValueByName("uid").flatMap { uid =>

      def fetchTags(query: QueryBuilder = QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery("temporary"))) = client.prepareSearch("stored-query").setTypes("queries")
        .setQuery(query)
        .setSize(0)
        .addAggregation(AggregationBuilders.terms("tags").field("tags"))
        .execute()
        .future.map { res => res.getAggregations.get[StringTerms]("tags").getBuckets.map { bucket => bucket.getKeyAsString }.toList }

      val fc = for {
        fullTags <- fetchTags()
        mine <- fetchTags(QueryBuilders.idsQuery("queries").addIds(uid))
      } yield (fullTags,mine)

      onComplete(fc).flatMap {
        case scala.util.Success((fullTags,mine)) => provide((mine ++ fullTags).mkString(" "))
        case scala.util.Failure(ex) => provide("")
      }
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
          client.prepareSearch("stored-query").setTypes("queries")
            .setQuery(boolQuery()
              .excludeTemporary
              .search_all(q)
              .matchQuery("tags", tags)
              .mustNot(idsQuery().addIds(excludedIds:_*)))
            .setFetchSource(Array("item"), null)
            .setSize(size).setFrom(from)
        )
    }
  }
}
