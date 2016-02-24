package lila.db
package api

import play.api.libs.iteratee._
import play.api.libs.json._
import reactivemongo.play.json._
import reactivemongo.api.Cursor
import reactivemongo.bson._
import scalaz.Monoid

import lila.db.Implicits._

object $enumerate {
  import reactivemongo.play.iteratees.cursorProducer

  def apply[A: BSONDocumentReader](query: QueryBuilder, limit: Int = Int.MaxValue)(op: A => Any): Funit =
    query.cursor[A]().enumerator(limit) run {
      Iteratee.foreach((obj: A) => op(obj))
    }

  def over[A: TubeInColl](query: QueryBuilder, limit: Int = Int.MaxValue)(op: A => Funit): Funit =
    query.cursor[Option[A]]().enumerator(limit, Cursor.ContOnError()) run {
      Iteratee.foldM(()) {
        case (_, Some(obj)) => op(obj)
        case _              => funit
      }
    }

  def bulk[A: BSONDocumentReader](query: QueryBuilder, size: Int, limit: Int = Int.MaxValue)(op: List[A] => Funit): Funit =
    query.batch(size).cursor[A]().bulkEnumerator(limit) run {
      Iteratee.foldM(()) {
        case (_, objs) => op(objs.toList)
      }
    }

  def fold[A: BSONDocumentReader, B](query: QueryBuilder)(zero: B)(f: (B, A) => B): Fu[B] =
    query.cursor[A]().enumerator() |>>> Iteratee.fold(zero)(f)

  def foldMonoid[A: BSONDocumentReader, B: Monoid](query: QueryBuilder)(f: A => B): Fu[B] =
    fold[A, B](query)(Monoid[B].zero) { case (b, a) => f(a) |+| b }
}
