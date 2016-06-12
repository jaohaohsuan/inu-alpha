package com.inu.frontend

import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http._
import spray.routing._
import scala.collection.JavaConversions._
import com.typesafe.config.ConfigFactory

/**
 * Loads in configuration settings from resources (application.conf) using typesafe ConfigFactory
 */
object Config {
  // Check if inu.environment is set, and if it is, use the right config file
  // Otherwise just stick with application.conf
  val environment = Option(System.getProperty("inu.environment.frontend"))
  private val config = if (environment.isDefined) {
    ConfigFactory.load(environment.get)
  } else {
    ConfigFactory.load()
  }

  val port = config.getInt("service.port")

  val corsAllowCredentials = config.getBoolean("service.cors.allow_credentials")

  // Grab origins from a string like "*" or
  // "http://localhost:5000 | http://domain.com | http://192.168.3.14:1234"
  val corsAllowOrigins: Array[String] = {
    config.getString("service.cors.allow_origin") match {
      case "*" => Array("*")
      case multiple: String => multiple.split('|') map { _.trim }
    }
  }

  val corsAllowHeaders = config.getStringList("service.cors.allow_headers").toList
  val corsExposeHeaders = config.getStringList("service.cors.expose_headers").toList

}

/**
 * A mixin to provide support for providing CORS headers as appropriate
 */
trait CorsSupport {
  this: HttpService =>

  private val optionsCorsCustomHeaders = List(
    `Access-Control-Expose-Headers`(Config.corsExposeHeaders.mkString(", "))
  )

  private val optionsCorsHeaders = List(
    `Access-Control-Allow-Headers`(Config.corsAllowHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20),  // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(Config.corsAllowCredentials)
  )

  /**
   * Based on the provided RequestContext, return an Access-Control-Allow-Origin header with the
   * user-provided Origin if that Origin is acceptable, or a None if it's not.
   */
  private def getAllowedOrigins(context: RequestContext): Option[`Access-Control-Allow-Origin`] = {
    context.request.header[Origin].collect {
      case origin if Config.corsAllowOrigins.contains(origin.value) ||
        Config.corsAllowOrigins.contains("*") => `Access-Control-Allow-Origin`(SomeOrigins(origin.originList))
    }
  }

  def cors[T]: Directive0 = mapRequestContext {
    context => context.withRouteResponseHandling {
      // If an OPTIONS request was rejected as 405, complete the request by responding with the
      // defined CORS details and the allowed options grabbed from the rejection
      case Rejected(reasons) if context.request.method == HttpMethods.OPTIONS && reasons.exists(_.isInstanceOf[MethodRejection]) => {
        val allowedMethods = reasons.collect { case r: MethodRejection => r.supported }

        context.complete(HttpResponse().withHeaders(
          `Access-Control-Allow-Methods`(OPTIONS, allowedMethods :_*) ::
            getAllowedOrigins(context) ++:
              optionsCorsHeaders
        ))
      }
    } withHttpResponseHeadersMapped {
      headers => getAllowedOrigins(context).toList ++ headers ++ optionsCorsCustomHeaders
    }
  }
}
