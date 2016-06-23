package com.inu.frontend.elasticsearch

import org.elasticsearch.action.{ActionListener, ListenableActionFuture}
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.QueryBuilders._

import scala.concurrent.{Future, Promise}

object ImplicitConversions {

  implicit class ActionListenableFutureConverter[T](x: ListenableActionFuture[T]) {
    def future: Future[T] = {
      val p = Promise[T]()
      x.addListener(new ActionListener[T] {
        def onFailure(e: Throwable) = p.failure(e)
        def onResponse(response: T) = p.success(response)
      })
      p.future
    }
  }

  implicit class OptionIsFuture[A](val option: Option[A]) {
    def future(ex: Exception = new Exception) = option.map(Future.successful).getOrElse(Future.failed(ex))
  }

  implicit class String0(value: Option[String]) {
    def asTypeQuery = {
      value match {
        case None => boolQuery()
        case Some(value) =>
          boolQuery().filter(value.split("""(\s+|,)""").foldLeft(boolQuery()){ (acc, t) => acc.should(QueryBuilders.typeQuery(t))})
      }
    }
  }
}
