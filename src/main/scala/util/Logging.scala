package util

/**
 * Created by henry on 5/17/15.
 */
import akka.actor.{ Actor, ActorLogging }
import scala.language.implicitConversions

trait ImplicitActorLogging extends ActorLogging {
  this: Actor ⇒

  implicit def toLogging[T](a: T) = WrappedLog[T](a)

  case class WrappedLog[T](a: T) {
    def logInfo(f: T ⇒ String = _.toString): T = {
      log.info(f(a))
      a
    }
    def logError(f: T ⇒ String = _.toString): T = {
      log.error(f(a))
      a
    }
  }
}