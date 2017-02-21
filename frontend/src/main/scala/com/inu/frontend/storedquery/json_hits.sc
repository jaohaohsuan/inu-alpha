import org.json4s.JValue
import org.json4s.native.JsonMethods._

def show(value: JValue) = pretty(render(value))

val origon =
  """{"took":2,"timed_out":false,"_shards":{"total":5,"successful":5,"failed":0},"hits":{"total":2,"max_score":1.0,"hits":[{"_index":"stored-query","_type":"queries","_id":"154932256","_score":1.0,"_source":{"keywords":[],"title":"k8s","tags":["paas","docker"],"query":{"bool":{}},"item":{"id":"154932256","data":[{"name":"title","value":"k8s"},{"name":"tags","value":"paas docker"}]}}},{"_index":"stored-query","_type":"queries","_id":"1346923062","_score":1.0,"_source":{"keywords":[],"title":"mesos","tags":["paas"],"query":{"bool":{}},"item":{"id":"1346923062","data":[{"name":"title","value":"mesos"},{"name":"tags","value":"paas"}]}}}]}}""".stripMargin

val json = parse(origon)


val list = json \ "hits" \ "hits" \ "_source" \ "item"
show(list)

val single = """{
  "took" : 4,
  "timed_out" : false,
  "_shards" : {
    "total" : 5,
    "successful" : 5,
    "failed" : 0
  },
  "hits" : {
    "total" : 2,
    "max_score" : 1.0,
    "hits" : [ {
      "_index" : "stored-query",
      "_type" : "queries",
      "_id" : "154932256",
      "_score" : 1.0,
      "_source" : {
        "item" : {
          "data" : [ {
            "name" : "title",
            "value" : "k8s"
          }, {
            "name" : "tags",
            "value" : "paas docker"
          } ],
          "href" : "154932256"
        }
      }
    } ]
  }
}"""

val singleItem = parse(single) \ "hits" \ "hits" \ "_source" \ "item"

show(singleItem)

