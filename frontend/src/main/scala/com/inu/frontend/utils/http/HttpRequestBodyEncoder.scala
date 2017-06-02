package com.inu.frontend.utils.http

import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpMethod, HttpRequest, MediaType}

/**
  * Created by henry on 6/1/17.
  */
object HttpRequestBodyEncoder {

  implicit val defaultMedia: MediaType.WithFixedCharset = `application/json`

  implicit class httpMethodIfx(val verb: HttpMethod) {

    def /(path: String, body: String = "")(implicit mime:MediaType.WithFixedCharset) : HttpRequest = {
      //val uri: String Refined Uri = Refined.unsafeApply(s"/$path")
      HttpRequest(method = verb, uri = s"/$path", entity = HttpEntity(mime, body))
    }
  }
}
