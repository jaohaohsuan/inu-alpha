package com.inu.frontend.storedquery.directive

import org.elasticsearch.action.search.SearchRequestBuilder
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.routing._
/**
  * Created by henry on 6/19/16.
  */
trait LogsDirectives extends Directives {

  implicit def client: org.elasticsearch.client.Client

  def prepareSearch(query: JValue): Directive1[SearchRequestBuilder] = {
    import shapeless._
    val queryString = compact(render(query))
    parameter('size.as[Int] ? 10, 'from.as[Int] ? 0 ).hflatMap {
      case size :: from :: HNil => {
        provide(
          client.prepareSearch("logs-*")
                .setQuery(queryString)
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
