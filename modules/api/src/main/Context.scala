package lila.api

import play.api.libs.json.{ JsObject, JsArray }
import play.api.mvc.{ Request, RequestHeader }

import lila.pref.Pref
import lila.user.{ UserContext, HeaderUserContext, BodyUserContext }

case class PageData(
  friends: List[lila.common.LightUser],
  teamNbRequests: Int,
  nbMessages: Int,
  pref: Pref,
  blindMode: Boolean)

object PageData {

  val default = PageData(Nil, 0, 0, Pref.default, false)

  def anon(blindMode: Boolean) = default.copy(blindMode = blindMode)
}

sealed trait Context extends lila.user.UserContextWrapper {

  val userContext: UserContext
  val pageData: PageData

  def friends = pageData.friends
  def teamNbRequests = pageData.teamNbRequests
  def nbMessages = pageData.nbMessages
  def pref = pageData.pref
  def blindMode = pageData.blindMode

  def currentTheme = ctxPref("theme").fold(Pref.default.realTheme)(lila.pref.Theme.apply)

  def currentPieceSet = ctxPref("pieceSet").fold(Pref.default.realPieceSet)(lila.pref.PieceSet.apply)

  def currentBg = ctxPref("bg") | "light"

  private def ctxPref(name: String): Option[String] =
    userContext.req.session get name orElse { pref get name }
}

sealed abstract class BaseContext(
  val userContext: lila.user.UserContext,
  val pageData: PageData) extends Context

final class BodyContext(
    val bodyContext: BodyUserContext,
    data: PageData) extends BaseContext(bodyContext, data) {

  def body = bodyContext.body
}

final class HeaderContext(
  headerContext: HeaderUserContext,
  data: PageData) extends BaseContext(headerContext, data)

object Context {

  def apply(req: RequestHeader): HeaderContext =
    new HeaderContext(UserContext(req, none), PageData.default)

  def apply(userContext: HeaderUserContext, pageData: PageData): HeaderContext =
    new HeaderContext(userContext, pageData)

  def apply(userContext: BodyUserContext, pageData: PageData): BodyContext =
    new BodyContext(userContext, pageData)
}
