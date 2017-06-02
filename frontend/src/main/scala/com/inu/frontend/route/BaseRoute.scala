package com.inu.frontend.route

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.inu.frontend.utils.encoding.NativeJson4sSupport

/**
  * Created by henry on 6/1/17.
  */
trait BaseRoute extends NativeJson4sSupport {


  protected def doRoute(implicit mat: Materializer): Route

  def route: Route = encodeResponse {
    extractMaterializer { implicit mat =>
      doRoute(mat)
    }
  }
}
