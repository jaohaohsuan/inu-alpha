package frontend

import org.json4s._
import shapeless.{HNil, ::}
import spray.routing._
import spray.util.LoggingContext
import scala.concurrent.ExecutionContextExecutor
import scala.language.implicitConversions
import org.json4s.JsonDSL._

trait ImplicitHttpServiceLogging extends {
  this: HttpService =>

  implicit def executionContext: ExecutionContextExecutor

  def log: AnyRef with LoggingContext


  val `collection+json`: Directive1[JObject] = requestUri.flatMap {
    case uri => provide("collection" ->
      ("version" -> "1.0") ~~
        ("href" -> s"$uri") ~~
        ("items" -> JArray(List.empty)) ~~
        ("template" -> JNothing))
  }

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
