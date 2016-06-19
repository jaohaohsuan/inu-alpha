package com.inu.frontend.storedquery.directive

import org.elasticsearch.action.get.GetResponse
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.routing._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by henry on 6/19/16.
  */
trait StoredQueryDirectives extends Directives {

  implicit def executionContext: ExecutionContext
  implicit def client: org.elasticsearch.client.Client

  def item (id: String): Directive1[JValue] = {
    import com.inu.frontend.elasticsearch.ImplicitConversions._
    val f: Future[GetResponse] = client.prepareGet("stored-query", ".percolator", id).setFetchSource(Array("item", "occurs", "query"), null).execute().future
    onComplete(f).flatMap {
      case scala.util.Success(res) => provide(parse(res.getSourceAsString()))
      case _ => reject
    }
  }
}
