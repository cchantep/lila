package lila.tournament

import org.joda.time.DateTime
import play.api.libs.iteratee._

import reactivemongo.bson._
import reactivemongo.api.Cursor

import scala.concurrent.duration._

import lila.common.Maths
import lila.common.paginator.Paginator
import lila.db.BSON._
import lila.db.dsl._
import lila.db.paginator.Adapter
import lila.rating.PerfType
import lila.user.User

final class LeaderboardApi(
    coll: Coll,
    maxPerPage: Int) {

  import LeaderboardApi.{ ChartData, Entry, TourEntry }
  import BSONHandlers._

  def recentByUser(user: User, page: Int) = paginator(user, page, $doc("d" -> -1))

  def bestByUser(user: User, page: Int) = paginator(user, page, $doc("w" -> 1))

  def chart(user: User): Fu[ChartData] = {
    coll.aggregateWith[ChartData.AggregationResult]() { agg =>
      import agg._

      Match($doc("u" -> user.id)) -> List(
        GroupField("v")(
          "nb" -> SumValue(1),
          "points" -> Push(BSONString(f"$$s")),
          "ratios" -> Push(BSONString(f"$$w"))))

    }.collect[List](
      -1, Cursor.FailOnError[List[ChartData.AggregationResult]]()).map { aggs =>
      ChartData(aggs.flatMap { agg =>
        PerfType.byId get agg._id map {
          _ -> ChartData.PerfResult(
            nb = agg.nb,
            points = ChartData.Ints(agg.points),
            rank = ChartData.Ints(agg.ratios))
        }
      }.sortLike(PerfType.leaderboardable, _._1))
    }
  }

  private def paginator(user: User, page: Int, sort: Bdoc): Fu[Paginator[TourEntry]] = Paginator(
    adapter = new Adapter[Entry](
      collection = coll,
      selector = $doc("u" -> user.id),
      projection = $empty,
      sort = sort
    ) mapFutureList withTournaments,
    currentPage = page,
    maxPerPage = maxPerPage)

  private def withTournaments(entries: Seq[Entry]): Fu[Seq[TourEntry]] =
    TournamentRepo byIds entries.map(_.tourId) map { tours =>
      entries.flatMap { entry =>
        tours.find(_.id == entry.tourId).map { TourEntry(_, entry) }
      }
    }
}

object LeaderboardApi {

  private val rankRatioMultiplier = 100 * 1000

  case class TourEntry(tour: Tournament, entry: Entry)

  case class Ratio(value: Double)

  case class Entry(
    id: String, // same as tournament player id
    userId: String,
    tourId: String,
    nbGames: Int,
    score: Int,
    rank: Int,
    rankRatio: Ratio, // ratio * rankRatioMultiplier. function of rank and tour.nbPlayers. less is better.
    freq: Option[Schedule.Freq],
    speed: Option[Schedule.Speed],
    perf: PerfType,
    date: DateTime)

  case class ChartData(perfResults: List[(PerfType, ChartData.PerfResult)]) {
    import ChartData._
    lazy val allPerfResults: PerfResult = perfResults.map(_._2) match {
      case head :: tail => tail.foldLeft(head) {
        case (acc, res) => PerfResult(
          nb = acc.nb + res.nb,
          points = res.points ::: acc.points,
          rank = res.rank ::: acc.rank)
      }
      case Nil => PerfResult(0, Ints(Nil), Ints(Nil))
    }
  }

  object ChartData {

    case class Ints(v: List[Int]) {
      def mean = v.toNel map Maths.mean[Int]
      def median = v.toNel map Maths.median[Int]
      def sum = v.sum
      def :::(i: Ints) = Ints(v ::: i.v)
    }

    case class PerfResult(nb: Int, points: Ints, rank: Ints) {
      private def rankPercent(n: Double) = (n * 100 / rankRatioMultiplier).toInt
      def rankPercentMean = rank.mean map rankPercent
      def rankPercentMedian = rank.median map rankPercent
    }

    case class AggregationResult(_id: Int, nb: Int, points: List[Int], ratios: List[Int])
  }
}
