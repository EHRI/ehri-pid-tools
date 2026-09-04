package services

case class PidNotFoundException(message: String) extends Exception(message)
