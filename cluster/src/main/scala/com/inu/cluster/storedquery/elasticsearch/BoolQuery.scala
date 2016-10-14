package com.inu.cluster.storedquery.elasticsearch


import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{write}
import com.inu.protocol.storedquery.messages._

import scala.language.implicitConversions

object BoolQuery {

  def unapply(arg: Iterable[BoolClause]): Option[JValue] = {

    val empty: JValue = parse("""{ "bool": { "minimum_should_match": 1 } }""")
    def build(clauses: Iterable[BoolClause]): JValue = {
      clauses.foldLeft(empty) { (acc, clause) =>
       val query: JValue = clause match {
          case MultiMatchQuery(json) => json
          case MultiSpanNearQuery(json) => json
          case NamedClause(_, _, occur, innerClauses) =>
            innerClauses.getOrElse(Map.empty).values.toList match {
              case Nil => JNothing
              case xs => "bool" -> ((occur -> Set(build(xs))) ~~ ("minimum_should_match" -> 1)): JObject
            }
          case _ => JNothing
        }
        acc merge query
      }
    }

    Some(build(arg))
  }
}

object Occurs {

  implicit val formats = DefaultFormats +
    FieldSerializer[NamedClause](FieldSerializer.ignore("clauses")) +
    FieldSerializer[NamedClause](FieldSerializer.ignore("shortName"))

  def unapply(arg: Map[Int, BoolClause]): Option[JValue] = {
        val result = arg.map({ case (id, el) =>

          val JObject(xs) = parse(write(el))
          val data = JArray(xs.map { case JField(f,v) => ("name" -> f) ~~ ("value" -> v) })

          el.occurrence -> Set(
            ("data" -> data) ~~
            ("href" -> s"#{uri}/${el.shortName}/$id")
          ): JObject
        }).foldLeft(JObject(Nil)){ (acc, j) => acc.merge(j)}
        Some(result)
    }
}

