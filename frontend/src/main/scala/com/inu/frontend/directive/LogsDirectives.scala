package com.inu.frontend.directive

import akka.http.scaladsl.server._
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.json4s._
import org.json4s.native.JsonMethods._

trait LogsDirectives extends Directives {

  implicit def client: org.elasticsearch.client.Client

  import QueryBuilders._

  def prepareGetVtt = {
    path("""^logs-\d{4}\.\d{2}\.\d{2}$""".r / Segment / Segment).tflatMap {
      case (index, typ, id) =>
        provide(client.prepareGet(index,typ,id)
                      .setFields("vtt")
                      .setFetchSource(Array("dialogs", "agent*", "customer*"), null))
    }
  }

  def userFields = {
    Seq(
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
      "customerGender")
  }

  def prepareSearchLogs(query: JValue): Directive1[SearchRequestBuilder] = {

      parameter('size.as[Int] ? 10, 'from.as[Int] ? 0).tflatMap {
        case (size, from) => {
          //val noReturnQuery = boolQuery().mustNot(matchAllQuery())
          //val JArray(xs) = query \ "indices" \ "indices"
          //val indices = xs.map { case JString(s) => s}

          provide(
            userFields.foldLeft(client.prepareSearch()
              .setQuery(constantScoreQuery(wrapperQuery(compact(render(query)))))
              .setSize(size).setFrom(from)
              .addField("vtt")){ (acc, f) => acc.addField(f) }
              .setHighlighterRequireFieldMatch(true)
              .setHighlighterNumOfFragments(0)
              .setHighlighterPreTags("<em>")
              .setHighlighterPostTags("</em>")
              .addHighlightedField("agent*")
              .addHighlightedField("customer*")
              .addHighlightedField("dialogs")
          )
        }
        case _ => reject
      }

  }

}
