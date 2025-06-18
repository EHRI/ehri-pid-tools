package services

case class PidExistsException(message: String) extends Exception(message)