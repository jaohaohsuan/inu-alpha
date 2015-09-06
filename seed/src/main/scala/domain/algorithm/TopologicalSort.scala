package seed.domain.algorithm

import scala.annotation.tailrec

object TopologicalSort {

  def sort[A](edges: Map[A, Set[A]]): Iterable[A] = {
    @tailrec
    def sort(predecessor: Map[A, Set[A]], done: Iterable[A]): Iterable[A] = {
      val (noPreds, hasPreds) = predecessor.partition {
        _._2.isEmpty
      }
      if (noPreds.isEmpty) {
        if (hasPreds.isEmpty) done else sys.error(hasPreds.toString)
      } else {
        val found = noPreds.map {
          _._1
        }
        sort(hasPreds.mapValues {
          _ -- found
        }, done ++ found)
      }
    }

    sort(edges, Seq())
  }

  def toPredecessor[A](edges: Traversable[(A, A)]): Map[A, Set[A]] = {
    edges.foldLeft(Map[A, Set[A]]()) { (acc, e) =>
      append(acc, e)
    }
  }

  def append[A](acc: Map[A, Set[A]], e: (A, A)) = {
    acc + (e._1 -> acc.getOrElse(e._1, Set())) + (e._2 -> (acc.getOrElse(e._2, Set()) + e._1))
  }

  def collectPaths[A](currentDot: A,
                      result: Set[Set[(A,A)]] = Set.empty[Set[(A,A)]],
                      currentPath: Set[(A,A)] = Set.empty[(A,A)])
                     (implicit preds: Map[A, Set[A]])
                     : Set[Set[(A,A)]] = {
    preds get currentDot match {
      case None => result + currentPath
      case Some(set) if set.isEmpty => result + currentPath
      case Some(set) =>
        set.foldLeft(result) { (acc, e) => {
            collectPaths(e, acc, currentPath + Tuple2(currentDot,e))
          }
        }
    }
  }

  def collectPaths2[A](currentDot: A,
                      result: List[A] = List.empty )
                     (implicit preds: Map[A, Set[A]])
  : List[A] = {
    preds get currentDot match {
      case None => currentDot :: result
      case Some(list) if list.isEmpty => currentDot :: result
      case Some(list) =>
        currentDot :: list.foldLeft(result) { (acc, e) =>
          collectPaths2(e, acc )
        }
    }
  }

}
