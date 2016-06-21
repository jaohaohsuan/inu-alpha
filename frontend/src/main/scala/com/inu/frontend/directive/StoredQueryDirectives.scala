package com.inu.frontend.directive

import org.elasticsearch.action.get.GetResponse
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.routing._
import shapeless._
import scala.concurrent.{ExecutionContext, Future}
import com.inu.frontend.elasticsearch.ImplicitConversions._

/**
  * Created by henry on 6/19/16.
  */
trait StoredQueryDirectives extends Directives {

  implicit def executionContext: ExecutionContext
  implicit def client: org.elasticsearch.client.Client

  def item (id: String): Directive1[JValue] = {

    val f: Future[GetResponse] = client.prepareGet("stored-query", ".percolator", id).setFetchSource(Array("item", "occurs", "query"), null).execute().future
    onComplete(f).flatMap {
      case scala.util.Success(res) => provide(parse(res.getSourceAsString()))
      case _ => reject
    }
  }

  def percolate(gr: GetResponse) = {
    parameters("_id").flatMap {
      case storedQueryId => {
        provide(client.preparePercolate()
          .setIndices("stored-query")
          .setDocumentType(gr.getType)
          .setSource(s"""{
                         |    "filter" : { "ids" : { "type" : ".percolator", "values" : [ "$storedQueryId" ] } },
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
}
