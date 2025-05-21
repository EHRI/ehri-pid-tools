package services

import play.api.libs.json.JsValue

case class DoiServiceException(message: String, status: Int, underlying: JsValue) extends Exception(message)

case class DoiNotFound(message: String, underlying: Option[JsValue] = None) extends Exception(message)