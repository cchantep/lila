package lila.plan

import org.joda.time.DateTime
import reactivemongo.bson.{ BSONDocument, BSONNull }

import lila.db.dsl._

private final class MonthlyGoalApi(goal: Cents, chargeColl: Coll) {

  def get: Fu[MonthlyGoal] = monthAmount map { amount =>
    MonthlyGoal(current = amount, goal = goal)
  }

  def monthAmount: Fu[Cents] = chargeColl.aggregateWith[BSONDocument]() { agg =>
    import agg._ // aggregation stages

    Match($doc(
      "date" $gt DateTime.now.withDayOfMonth(1).withTimeAtStartOfDay)) -> List(
      Group(BSONNull)("cents" -> SumField("cents")))
  }.head.map {
    _.getAs[Int]("cents").map(Cents(_)).get
  }
}

case class MonthlyGoal(current: Cents, goal: Cents) {

  def percent = 100 * current.value / goal.value
}
