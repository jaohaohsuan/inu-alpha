package com.inu.cluster.storedquery.elasticsearch

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest}
import akka.http.scaladsl.model.MediaTypes._
import akka.stream.scaladsl.Flow
import com.inu.protocol.storedquery.messages._
import com.typesafe.config.{Config, ConfigFactory}
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s._

import scala.language.postfixOps

/**
  * Created by henry on 5/11/16.
  */
trait PercolatorWriter  {

  val put = Flow[JValue].map { json =>
    val JString(id) = json \ "_id"
    val doc = json \ "doc"
    HttpRequest(method = HttpMethods.PUT, uri = s"/stored-query/.percolator/$id", entity = HttpEntity(`application/json`, compact(render(doc)))) -> id }

  val query: Flow[StoredQuery, org.json4s.JValue, NotUsed] = Flow[StoredQuery].map {
    case Percolator(id, body) => ("_id", id) ~~ ("doc", body)
    case unmatched => JObject()
  }

  val keywords: Flow[StoredQuery, org.json4s.JValue, NotUsed] = Flow[StoredQuery].map {
    case QueryTerms(id,terms) => ("_id", id) ~~ ("doc", "keywords" -> terms)
    case unmatched => JObject()
  }

}
