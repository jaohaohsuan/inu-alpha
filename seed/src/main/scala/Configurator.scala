package seed

import akka.actor.{Cancellable, PoisonPill, Props, Actor}
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxySettings, ClusterSingletonProxy, ClusterSingletonManagerSettings, ClusterSingletonManager}
import akka.io.IO
import akka.persistence.query.{EventEnvelope, EventsByPersistenceId, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.pattern._
import akka.stream.scaladsl.{Sink, Flow}
import akka.util.Timeout
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri, BulkCompatibleDefinition, IndexDefinition}
import domain.storedQuery.StoredQueryAggregateRoot.{ItemsChanged, ItemCreated}
import net.hamnaberg.json.collection.data.{JavaReflectionData, DataApply}
import net.hamnaberg.json.collection.{JsonCollection, Property, Template}
import org.elasticsearch.common.settings.ImmutableSettings
import org.json4s
import org.json4s.JsonAST.{JInt, JString, JValue}
import org.json4s.{JObject, DefaultFormats, Formats}
import org.json4s.native.JsonMethods._
import protocol.storedQuery.{MatchBoolClause, StoredQuery, AggregateRoot}
import domain.storedQuery.StoredQueryAggregateRoot
import spray.can.Http
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.language.implicitConversions

object Configurator {
  val Name = "conf"
}

class Configurator extends Actor with SharedLeveldbStoreUsage {

  import context.system

  def processReceive: Receive = {
    case LeveldbStoreRegistration(m) if m.hasRole("compute") =>
      system.actorOf(ClusterSingletonManager.props(
        singletonProps = Props(classOf[StoredQueryAggregateRoot]),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)),
        name = AggregateRoot.Name)

    case LeveldbStoreRegistration(m) if m.hasRole("web") => {

      startupMaterializeView

      system.actorOf(ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${AggregateRoot.Name}",
        settings = ClusterSingletonProxySettings(system)
      ), name = "aggregateRootProxy")

      import frontend.ServiceActor
      import scala.concurrent.duration._

      val service = system.actorOf(Props(classOf[ServiceActor]), "service")

      implicit val timeout = Timeout(5.seconds)
      IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = frontend.Config.port)
    }
    //ClusterClientReceptionist(system).registerService(storedQueryAggregateRoot)

    case unknown =>
  }

  def receive = registration orElse processReceive

  def startupMaterializeView = {

    val settings = ImmutableSettings.settingsBuilder()
      .put("node.client", true)
      .put("cluster.name", "inu-dc")

    val uri = ElasticsearchClientUri("elasticsearch://192.168.99.100:32780")
    implicit val client = ElasticClient.remote(settings.build(), uri)

    import context.system

    implicit val mat = ActorMaterializer()(context)

    import akka.persistence.query.PersistenceQuery
    import akka.persistence.query.journal.leveldb.LeveldbReadJournal

    val readJournal = PersistenceQuery(system).readJournalFor(LeveldbReadJournal.Identifier)

    readJournal
      .query(EventsByPersistenceId(AggregateRoot.Name))
      .mapConcat(flatten)
      .map(convertToReadSideType)
      .mapAsync(1)(writeToElasticsearch)
      .runForeach(println)
      //.runWith(Sink.ignore)
  }

  def flatten(envelope: EventEnvelope) = {
    envelope.event match {
      case ItemCreated(entity, _) => (entity.id, entity.copy(clauses = Map((1, MatchBoolClause("big","dialogs","AND","must"))))) :: Nil
      case e: ItemsChanged => e.items.toList
      case _ => Nil
    }
  }

  def convertToReadSideType(value: (String, StoredQuery)) = {

    import ReadSideImplicits._
    val (id, e) = value
    val clauses = e.clauses.groupBy { case (_, c) => c.occurrence }.map {
     case (occur, map) =>
       occur -> map.map { case(id, c) =>
         compact(render(JObject(
           ("href", JInt(id)),
           ("data", c: JValue)
         )))
       }.toSeq
    }.toSeq

    (id , Seq(
      "href" -> s"/_query/template/$id",
      "data" -> compact(render(e: DbStoredQuery))
    ) ++ clauses)
  }

  def writeToElasticsearch(value: (String, Seq[(String, Any)] ))(implicit client: com.sksamuel.elastic4s.ElasticClient): Future[String] = {
    import com.sksamuel.elastic4s.ElasticDsl._
    import context.dispatcher
    val (storedQueryId, fields) = value
    client.execute {
      update id storedQueryId in "stored-query/v1" docAsUpsert fields
    }.map { _.toString }
  }
}

object ReadSideImplicits {

  case class DbStoredQuery(title: String, tags: Option[String])

  import scala.language.implicitConversions
  import org.json4s.JsonAST._

  implicit def json4sFormats: Formats = DefaultFormats

  implicit def asData[T<: AnyRef: Manifest](value: T): JValue = {
    val list = dataApply[T].apply(value).map(_.underlying)
    if (list.isEmpty) JNothing else JArray(list)
  }

  implicit def dataApply[T <: AnyRef: Manifest](implicit formats: org.json4s.Formats): DataApply[T] =
    new JavaReflectionData[T]()(formats, manifest[T])

  implicit def setToOptionString(value : Set[String]): Option[String] = Option(value.mkString(" ")).filter(_.trim.nonEmpty)

  implicit def toDb(value: StoredQuery): DbStoredQuery = DbStoredQuery(value.title, value.tags: Option[String])

}

