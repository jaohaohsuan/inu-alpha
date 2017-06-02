package com.inu.frontend

import akka.actor.Actor.Receive
import com.inu.frontend.analysis.AnalysisRoute
import com.inu.frontend.logs.LogsRoute
//import com.inu.frontend.storedquery.StoredQueryRoute
import org.elasticsearch.cluster.health.ClusterHealthStatus

//class DigServiceActor(implicit val client: org.elasticsearch.client.Client) extends
//{
//  implicit val system = context.system
//  implicit val ec = system.dispatcher
//  implicit val json4sFormats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
//
//
//  val hello = get {
//    path("hello") {
//      client.admin().cluster().prepareHealth().get().getStatus match {
//        case ClusterHealthStatus.RED =>
//          complete(RequestTimeout,"elasticsearch down")
//        case _ => complete(OK, "hello storedq")
//      }
//    }
//  }
//  def receive = runRoute(hello)
//}

//class ServiceActor(implicit val client: org.elasticsearch.client.Client) extends
//  StoredQueryRoute
//  with LogsRoute
//  with AnalysisRoute {
//
//  implicit val system = context.system
//  implicit val executionContext = system.dispatchers.lookup("my-thread-pool-dispatcher")
//  implicit val json4sFormats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
//
//  val log = LoggingContext.fromActorRefFactory(actorRefFactory)
//
//  def receive: Receive = runRoute(
//     pathPrefix("sapi") {
//      cors {
//        `_query/template/` ~
//          `logs-*` ~
//          `_analysis`
//       }
//     }
//  )
//}
