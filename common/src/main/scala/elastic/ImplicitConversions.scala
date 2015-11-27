package elastic

import org.elasticsearch.action.{ActionResponse, ActionRequestBuilder, ActionListener, ListenableActionFuture}

import scala.concurrent.{Future, Promise}

object ImplicitConversions {
  implicit class ActionListenableFutureConverter[T](x: ListenableActionFuture[T]) {
    def asFuture: Future[T] = {
      val p = Promise[T]()
      x.addListener(new ActionListener[T] {
        def onFailure(e: Throwable) = p.failure(e)
        def onResponse(response: T) = p.success(response)
      })
      p.future
    }
  }
}
