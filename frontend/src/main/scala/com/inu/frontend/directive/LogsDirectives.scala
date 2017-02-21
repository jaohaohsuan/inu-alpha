package com.inu.frontend.directive

import org.elasticsearch.action.get.GetRequestBuilder
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.json4s._
import org.json4s.native.JsonMethods._
import shapeless._
import spray.routing._

trait LogsDirectives extends Directives {

  implicit def client: org.elasticsearch.client.Client

  import QueryBuilders._

  def prepareGetVtt: Directive1[GetRequestBuilder] = {
    path("""^logs-\d{4}\.\d{2}\.\d{2}$""".r / Segment / Segment).hflatMap {
      case index :: typ :: id :: HNil =>
        provide(client.prepareGet(index,typ,id)
                      .setStoredFields("vtt")
                      .setFetchSource(Array("dialogs", "agent*", "customer*"), null))
    }
  }

  def userFields = {
    provide(Seq(
      "startTime",
      "endTime",
      "length",
      "endStatus",
      "projectName",
      "agentPhoneNo",
      "agentId",
      "agentName",
      "callDirection",
      "customerPhoneNo",
      "customerGender"))
  }

  def prepareSearchLogs(query: JValue): Directive1[SearchRequestBuilder] = {
    userFields.flatMap { fields =>
      parameter('size.as[Int] ? 10, 'from.as[Int] ? 0).hflatMap {
        case size :: from :: HNil => {
          //val noReturnQuery = boolQuery().mustNot(matchAllQuery())
          //val JArray(xs) = query \ "indices" \ "indices"
          //val indices = xs.map { case JString(s) => s}

          provide(
            fields.foldLeft(client.prepareSearch()
                              .setQuery(constantScoreQuery(wrapperQuery(compact(render(query)))))
                              .setSize(size).setFrom(from)
                              .addStoredField("vtt")
                              .highlighter(new HighlightBuilder()
                                .requireFieldMatch(true)
                                .numOfFragments(0)
                                .preTags("<em>")
                                .postTags("</em>")
                                .field("agent*")
                                .field("customer*")
                                .field("dialogs"))){ (acc, f) => acc.addStoredField(f) }
          )
        }
        case _ => reject
      }
    }
  }

}
