package frontend

import spray.routing.HttpService
import spray.util.LoggingContext
import scala.language.implicitConversions

trait ImplicitHttpServiceLogging extends {
  this: HttpService =>

  implicit val executionContext = actorRefFactory.dispatcher

  private val log: AnyRef with LoggingContext = LoggingContext.fromActorRefFactory(actorRefFactory)

  implicit def toLogging[T](a: T): WrappedLog[T] = WrappedLog[T](a)(log)

  case class WrappedLog[T](a: T)(logger: LoggingContext) {
    def logInfo(f: T ⇒ String = _.toString): T = {
      logger.info(f(a))
      a
    }
    def logError(ex: Exception)(f: T ⇒ String = _.toString): T = {
      logger.error(ex, f(a))
      a
    }
    def logDebug(f: T ⇒ String = _.toString): T = {
      logger.debug(f(a))
      a
    }
  }

}
