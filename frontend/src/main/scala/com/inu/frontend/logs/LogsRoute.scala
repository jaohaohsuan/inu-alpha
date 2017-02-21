package com.inu.frontend.logs

import com.inu.frontend.WebvttSupport
import com.inu.frontend.directive.{LogsDirectives, StoredQueryDirectives, VttDirectives}
import spray.routing.{HttpService, Route}
import spray.http.StatusCodes._
import com.inu.frontend.elasticsearch.ImplicitConversions._
import org.elasticsearch.common.xcontent.{ToXContent, XContentFactory, XContentType}

import scala.concurrent.ExecutionContext

trait LogsRoute extends WebvttSupport with HttpService with LogsDirectives with VttDirectives with StoredQueryDirectives {

  implicit def executionContext: ExecutionContext

  /*
  url sample:

  http://192.168.1.100:31115/sapi/logs-2016.07.01/amiast/wqidhfuehuhqw?_id=1775285775

  _id is 模型的id
   */

  lazy val `logs-*`: Route = {
    get {
      prepareGetVtt { q =>
        onSuccess(q.execute().future) { gr =>
          format(gr.getField("vtt")) { vttMap =>
            percolate(gr.getType(), gr.getSourceAsString()) { p =>
              onSuccess(p.execute().future) { pr =>
                pr.json
                extractFragments(List.empty[String]) { segments =>
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
