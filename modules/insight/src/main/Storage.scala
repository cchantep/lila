package lila.insight

import org.joda.time.DateTime
import play.api.libs.iteratee._

import reactivemongo.api.Cursor
import reactivemongo.bson._

import scala.concurrent.duration._
import scalaz.NonEmptyList

import lila.db.dsl._
import lila.user.UserRepo
import lila.rating.PerfType

private final class Storage(private[insight] val coll: Coll) {

  import Storage._
  import BSONHandlers._
  import Entry.{ BSONFields => F }

  /*
  def aggregate(operators: NonEmptyList[PipelineOperator]): Fu[AggregationResult] =
    coll.aggregate(operators.head, operators.tail, allowDiskUse = true)
   */

  def fetchFirst(userId: String): Fu[Option[Entry]] =
    coll.find(selectUserId(userId)).sort(sortChronological).uno[Entry]

  def fetchLast(userId: String): Fu[Option[Entry]] =
    coll.find(selectUserId(userId)).sort(sortAntiChronological).uno[Entry]

  def count(userId: String): Fu[Int] =
    coll.count(selectUserId(userId).some)

  def insert(p: Entry) = coll.insert(p).void

  def bulkInsert(ps: Seq[Entry]) =
    coll.insert[Entry](ordered = false).many(ps)

  def update(p: Entry) = coll.update(selectId(p.id), p, upsert = true).void

  def remove(p: Entry) = coll.remove(selectId(p.id)).void

  def removeAll(userId: String) = coll.remove(selectUserId(userId)).void

  def find(id: String) = coll.find(selectId(id)).uno[Entry]

  def ecos(userId: String): Fu[Set[String]] =
    coll.distinct[String, Set](F.eco, selectUserId(userId).some)

  def nbByPerf(userId: String): Fu[Map[PerfType, Int]] =
    coll.aggregateWith[BSONDocument]() { agg =>
      import agg.{ GroupField, Match, SumField, SumValue }

      Match(BSONDocument(F.userId -> userId)) -> List(
        GroupField(F.perf)("nb" -> SumValue(1)))

    }.collect[List](-1, Cursor.FailOnError[List[BSONDocument]]()).map { ls =>
      val m: Map[PerfType, Int] = ls.flatMap({ doc =>
        for {
          perfType <- doc.getAs[PerfType]("_id")
          nb <- doc.getAs[Int]("nb")
        } yield perfType -> nb
      })(scala.collection.breakOut)

      m
    }
}

private object Storage {

  import Entry.{ BSONFields => F }

  def selectId(id: String) = BSONDocument(F.id -> id)
  def selectUserId(id: String) = BSONDocument(F.userId -> id)
  val sortChronological = BSONDocument(F.date -> 1)
  val sortAntiChronological = BSONDocument(F.date -> -1)

  def combineDocs(docs: List[BSONDocument]) = docs.foldLeft(BSONDocument()) {
    case (acc, doc) => acc ++ doc
  }
}
