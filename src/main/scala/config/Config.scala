package config

import scala.collection.JavaConversions._

import com.typesafe.config.{ConfigFactory, ConfigObject}

/**
 * Loads in configuration settings from resources (application.conf) using typesafe ConfigFactory
 */
object Config {

  // Check if mediaman.environment is set, and if it is, use the right config file
  // Otherwise just stick with application.conf
  val environment = Option(System.getProperty("inu.environment"))
  private val config = if (environment.isDefined) {
    ConfigFactory.load(environment.get)
  } else {
    ConfigFactory.load("httpApp")
  }

  val corsAllowCredentials = config.getBoolean("service.cors.allow_credentials")

  // Grab origins from a string like "*" or
  // "http://localhost:5000 | http://mydomain.com | http://192.168.3.14:1234"
  val corsAllowOrigins: Array[String] = {
    config.getString("service.cors.allow_origin") match {
      case "*" => Array("*")
      case multiple: String => multiple.split('|') map { _.trim }
    }
  }

  val corsAllowHeaders = config.getStringList("service.cors.allow_headers").toList

}