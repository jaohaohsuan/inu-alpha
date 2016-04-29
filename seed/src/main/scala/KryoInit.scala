package seed
import com.esotericsoftware.kryo.Kryo

/**
  * Created by henry on 4/26/16.
  */
class KryoInit {
  def customize(kryo: Kryo): Unit  = {
    kryo.register(classOf[domain.storedQuery.StoredQueryAggregateRoot.ItemCreated])
    kryo.register(classOf[domain.storedQuery.StoredQueryAggregateRoot.ItemsChanged])
    kryo.register(classOf[protocol.storedQuery.StoredQuery])
    kryo.register(classOf[protocol.storedQuery.BoolClause])
    kryo.register(classOf[protocol.storedQuery.NamedBoolClause])
    kryo.register(classOf[protocol.storedQuery.SpanNearBoolClause])
    kryo.register(classOf[protocol.storedQuery.MatchBoolClause])

    kryo.register(classOf[scala.collection.Map[_,_]])
    kryo.register(classOf[scala.collection.Set[_]])
    kryo.register(scala.collection.immutable.::.getClass)
    kryo.register(Set.empty.getClass)
    kryo.register(Map.empty.getClass)
    kryo.register(Tuple1.getClass)
    kryo.register(Tuple2.getClass)
  }
}
