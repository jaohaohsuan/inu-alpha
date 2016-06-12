package com.inu.frontend.elasticsearch

import org.elasticsearch.action.{ActionListener, ListenableActionFuture}

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
}
