package com.inu.frontend.storedquery.directive

import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.routing._

trait LogsDirectives extends Directives {

  implicit def client: org.elasticsearch.client.Client

  import QueryBuilders._
  val noReturnQuery = boolQuery().mustNot(matchAllQuery())

  def prepareSearch(query: JValue): Directive1[SearchRequestBuilder] = {
    import shapeless._

    parameter('size.as[Int] ? 10, 'from.as[Int] ? 0 ).hflatMap {
      case size :: from :: HNil => {
        provide(
          client.prepareSearch("logs-*")
                .setQuery(
                  query \ "bool" match {
                    case JObject(Nil) => boolQuery().should(noReturnQuery)
                    case _ => boolQuery().should(noReturnQuery).should(wrapperQuery(compact(render(query))))
                  }
                )
                .setSize(size).setFrom(from)
                .addField("vtt")
                  .setHighlighterRequireFieldMatch(true)
                  .setHighlighterNumOfFragments(0)
                  .setHighlighterPreTags("<em>")
                  .setHighlighterPostTags("</em>")
                  .addHighlightedField("agent*")
                  .addHighlightedField("customer*")
                  .addHighlightedField("dialogs")
        )
      }
    }
  }
}
