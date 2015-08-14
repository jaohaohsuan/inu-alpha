package util

/**
 * Created by henry on 5/17/15.
 */
import akka.actor.{ Actor, ActorLogging }
import scala.language.implicitConversions

object ImplicitPrint {
  implicit def toLogging[T](a: T) = WrappedLog[T](a)

  case class WrappedLog[T](a: T) {
    def println(f: T ⇒ String = _.toString): T = {
      Console.println(f(a))
      a
    }
    def print(f: T ⇒ String = _.toString): T = {
      Console.print(f(a))
      a
    }
  }
}

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