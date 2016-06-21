package com.inu.frontend.logs

import com.inu.frontend.WebvttSupport
import com.inu.frontend.directive.{LogsDirectives, StoredQueryDirectives, VttDirectives}
import spray.routing.{HttpService, Route}
import spray.http.StatusCodes._
import com.inu.frontend.elasticsearch.ImplicitConversions._
import scala.concurrent.ExecutionContext

trait LogsRoute extends WebvttSupport with HttpService with LogsDirectives with VttDirectives with StoredQueryDirectives {

  implicit def executionContext: ExecutionContext

  lazy val `logs-*`: Route = {
    get {
      prepareGetVtt { q =>
        onSuccess(q.execute().future) { gr =>
          format(gr.getField("vtt")) { vttMap =>
            percolate(gr) { p =>
              onSuccess(p.execute().future) { pr =>
                extractFragments(pr.getMatches) { segments =>
                  respondWithMediaType(`text/vtt`) {
                    complete(OK, vttMap.highlightWith(segments))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
