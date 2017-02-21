package com.inu.frontend.elasticsearch

import org.elasticsearch.action.{ActionListener, ListenableActionFuture}
import org.elasticsearch.common.xcontent.{ToXContent, XContentFactory, XContentType}
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import org.elasticsearch.index.query.QueryBuilders._

import scala.concurrent.{Future, Promise}

object ImplicitConversions {

  implicit class ActionListenableFutureConverter[T](x: ListenableActionFuture[T]) {
    def future: Future[T] = {
      val p = Promise[T]()
      x.addListener(new ActionListener[T] {
        def onFailure(e: Exception): Unit = p.failure(e)
        def onResponse(response: T): Unit = p.success(response)
      })
      p.future
    }
  }

  implicit class OptionIsFuture[A](val option: Option[A]) {
    def future(ex: Exception = new Exception): Future[A] = option.map(Future.successful).getOrElse(Future.failed(ex))
  }

  implicit class String0(value: Option[String]) {
    def asTypeQuery: BoolQueryBuilder = {
      value match {
        case None => boolQuery()
        case Some(s) =>
          boolQuery().filter(s.split("""(\s+|,)""").foldLeft(boolQuery()){ (acc, t) => acc.should(QueryBuilders.typeQuery(t))})
      }
    }
  }
   implicit class XContentExtractor(response: ToXContent) {

     private lazy val builder =  XContentFactory.contentBuilder(XContentType.JSON)

     def json: String = {
       builder.startObject()
       response.toXContent(builder, ToXContent.EMPTY_PARAMS)
       builder.endObject()
       builder.string()
     }
   }
}
