package read.storedQuery

import org.elasticsearch.action.get.{GetRequestBuilder, GetRequest, GetResponse}
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress

import scala.concurrent.{Promise, ExecutionContextExecutor}
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.client.Client

object StoredQueryIndex {

  val index = "stored-query"
  val apiVersion = "v1"

  /* val node = org.elasticsearch.node.NodeBuilder.nodeBuilder().settings(settings).node()
 node.client()*/

  val settings = ImmutableSettings.settingsBuilder()
    //.put("node.client", true)
    .put("cluster.name", "inu-dc")

  implicit lazy val client: Client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("192.168.99.100", 32780))

  def prepareGet(id: String): GetRequestBuilder = {
    client.prepareGet(index, apiVersion, id)
  }

  def get(request: GetRequest)(implicit client: Client, ctx: ExecutionContextExecutor) = {
    val p = Promise[GetResponse]()
    val listener = new ActionListener[GetResponse] {
      def onFailure(e: Throwable): Unit = p.tryFailure(e)
      def onResponse(resp: GetResponse): Unit = p.trySuccess(resp)
    }
    client.get(request, listener)
    p.future
  }

  def saveOrUpdate(value: (String, String))(implicit client: Client, ctx: ExecutionContextExecutor) = {
    val (storedQueryId, json) = value

    val indexRequest = client.prepareIndex(index, apiVersion, storedQueryId)
      .setSource(json).request()

    val updateRequest = client.prepareUpdate(index, apiVersion, storedQueryId)
      .setDoc(json).setUpsert(indexRequest).request()

    val p = Promise[UpdateResponse]()
    val listener = new ActionListener[UpdateResponse] {
      def onFailure(e: Throwable): Unit = p.tryFailure(e)
      def onResponse(resp: UpdateResponse): Unit = p.trySuccess(resp)
    }
    client.update(updateRequest, listener)
    p.future.map(_.toString)
  }
}
