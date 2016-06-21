package com.inu.frontend.logs

import com.inu.frontend.WebvttSupport
import com.inu.frontend.directive.{LogsDirectives, StoredQueryDirectives, VttDirectives}
import spray.routing.{HttpService, Route}
import spray.http.StatusCodes._
import com.inu.frontend.elasticsearch.ImplicitConversions._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


trait LogsRoute extends WebvttSupport with HttpService with LogsDirectives with VttDirectives with StoredQueryDirectives {

  implicit def executionContext: ExecutionContext

  lazy val `logs-*`: Route = {
    get {
      prepareGetVtt { q =>
        onSuccess(q.execute().future) { gr =>
          getVtt(gr) { kv =>
            percolate(gr) { p =>
              onSuccess(p.execute().future) { pr =>

                respondWithMediaType(`text/vtt`) {
                  complete(OK, kv)
                }
              }
            }
          }
        }
      }
    }
  }
}
