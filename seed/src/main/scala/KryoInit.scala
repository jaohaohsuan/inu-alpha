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
  }
}
