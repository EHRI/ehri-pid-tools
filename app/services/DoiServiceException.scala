package services

import play.api.libs.json.JsValue

case class DoiServiceException(message: String, status: Int, underlying: JsValue) extends Exception(message)
