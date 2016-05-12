package read.storedQuery

import akka.NotUsed
import akka.http.scaladsl.model.{HttpEntity, HttpRequest}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.stream.ClosedShape
import akka.stream.javadsl.RunnableGraph
import org.json4s.native.JsonMethods._
import akka.stream.scaladsl.{Flow, GraphDSL}
import org.json4s._
import protocol.storedQuery.StoredQuery

import scala.language.postfixOps
import org.json4s.JsonDSL._

/**
  * Created by henry on 5/11/16.
  */
trait PercolatorWriter {

  val query: Flow[StoredQuery, JValue, NotUsed] = Flow[StoredQuery].map {
    case Percolator(id, body) =>
      body
  }

  val keywords: Flow[StoredQuery, JValue, NotUsed] = Flow[StoredQuery].map {
    case QueryTerms(id,terms) => "keywords" -> terms
  }

}
