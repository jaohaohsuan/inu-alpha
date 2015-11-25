package read.storedFilter

import akka.actor.Props
import akka.persistence.query.EventEnvelope
import org.elasticsearch.client.Client
import org.json4s.JsonAST.JObject
import read.MaterializeView
import elastic.ImplicitConversions._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

object StoredFilterAggregateRootView {
  def props(implicit client: Client) = Props(classOf[StoredFilterAggregateRootView], client)
}

class StoredFilterAggregateRootView(private implicit val client: Client) extends MaterializeView {

  import akka.stream.scaladsl.Sink
  import context.dispatcher

  import domain.storedFilter.StoredFilterAggregateRoot._
  import protocol.storedFilter.NameOfAggregate

  def flatten(envelope: EventEnvelope) = {
    envelope.event match {
      case ItemCreated(id, typ, title) =>
        ("_id" -> id) ~
        ("type" -> typ) ~
        ("source" ->
          ("title" -> title): JObject) :: Nil
      case _ => Nil
    }
  }

  val source = readJournal
    .eventsByPersistenceId(NameOfAggregate.root.name)
    //.mapConcat(flatten)

  source.mapAsync(1) { envelope => envelope.event match {
    case ItemCreated(id, typ, title) =>
      val json = compact(render(("title" -> title): JObject))
      es.indices.storedFilter.save(id, typ, json)
  } }.runWith(Sink.ignore)
  //.runForeach(r => println(r.isCreated))

  //source.runForeach(f => pretty(render(f)).logInfo())

  def receive: Receive = {
    case _ =>
  }
}
