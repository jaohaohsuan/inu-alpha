package frontend

import org.json4s.JsonAST.{JValue, JArray, JObject}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import shapeless.{HNil, ::}
import spray.routing._
import spray.util.LoggingContext
import scala.concurrent.ExecutionContextExecutor

import scala.language.implicitConversions
import org.json4s.JsonDSL._

trait ImplicitHttpServiceLogging {
  this: HttpService =>

  implicit def executionContext: ExecutionContextExecutor
  implicit def json4sFormats: Formats

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
