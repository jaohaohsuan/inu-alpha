package frontend

import spray.util.LoggingContext
import scala.concurrent.ExecutionContextExecutor
import scala.language.implicitConversions

trait ImplicitHttpServiceLogging extends {

  implicit def executionContext: ExecutionContextExecutor

  def log: AnyRef with LoggingContext

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
