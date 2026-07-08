package services

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[ArkServiceImpl])
trait ArkService {
  def generateSuffix(): String
}

case class ArkServiceImpl() extends ArkService {
  // Simple implementation that generates a random suffix
  // In a real application, this could be more complex
  override def generateSuffix(): String = {
    val alphabet = "abcdefghijklmnpqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ123456789"
    (1 to 8).map(_ => alphabet(scala.util.Random.nextInt(alphabet.length))).mkString
  }
}