package com.inu.frontend

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query

/**
  * Created by henry on 6/11/16.
  */
object UriImplicitConversions {

  implicit class Uri0(uri: Uri) {
    def appendToValueOfKey(key: String)(value: String): Uri = {
      s"${uri.query().get(key).getOrElse("")} $value".trim match {
        case "" => uri
        case appended =>
          uri.withQuery(Query(uri.query().toMap + (key -> appended)))
      }
    }

    def withExistQuery(kvp: (String, String)*): Uri = {
      uri.withQuery(Query(uri.query().toMap ++ kvp))
    }

    def /(segment: String): Uri = {
      uri.withPath(uri.path / segment)
    }

    def drop(keys: String*) = {
      uri.withQuery(Query(keys.foldLeft(uri.query().toMap)(_ - _)))
    }
  }
}
