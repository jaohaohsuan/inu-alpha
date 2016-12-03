package com.inu.cluster

/**
  * Created by henry on 12/3/16.
  */
import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.config.Config

import scala.util.Try

class ElasticsearchExtensionImpl(config: Config) extends Extension {

  private val (address, port) = (config.getString("elasticsearch.client-address"), config.getInt("elasticsearch.client-http"))

  def httpConnectionPool(implicit system: ActorSystem, fm: Materializer): Flow[(HttpRequest, String), (Try[HttpResponse], String), HostConnectionPool] =
    Http().cachedHostConnectionPool[String](address, port)

}

object ElasticsearchExtension
  extends ExtensionId[ElasticsearchExtensionImpl]
  with ExtensionIdProvider {

  override def lookup = ElasticsearchExtension

  override def createExtension(system: ExtendedActorSystem) = new ElasticsearchExtensionImpl(system.settings.config)
}
