package com.inu.frontend

import spray.http.Uri

/**
  * Created by henry on 6/11/16.
  */
object UriImplicitConversions {

  implicit class Uri0(uri: Uri) {
    def appendToValueOfKey(key: String)(value: String): Uri = {
      s"${uri.query.get(key).getOrElse("")} $value".trim match {
        case "" => uri
        case appended =>
          uri.withQuery(uri.query.toMap + (key -> appended))
      }
    }

    def withExistQuery(kvp: (String, String)*): Uri = {
      uri.withQuery(uri.query.toMap ++ kvp)
    }

    def /(segment: String): Uri = {
      uri.withPath(uri.path / segment)
    }

    def drop(keys: String*) = {
      uri.withQuery(keys.foldLeft(uri.query.toMap)(_ - _))
    }
  }
}
