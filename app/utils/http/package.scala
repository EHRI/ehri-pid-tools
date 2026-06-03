package utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

package object http {

  def joinPath(path: String, qs: Map[String, Seq[String]]): String =
    List(path.takeWhile(_ != '?'), joinQueryString(qs)).filter(_.trim.nonEmpty).mkString("?")

  /**
   * Turn a seq of parameters into an URL parameter string, not including
   * the initial '?'.
   * @param qs a seq of key -> value pairs
   * @return an encoded URL parameter string
   */
  def joinQueryString(qs: Seq[(String, String)]): String =
    qs.map { case (key, value) =>
      URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
    }.mkString("&")

  /**
   * Turn a map of parameters into an URL parameter string, not including
   * the initial '?'.
   * @param qs a map of key -> value sequences
   * @return an encoded URL parameter string
   */
  def joinQueryString(qs: Map[String, Seq[String]]): String =
    joinQueryString(qs.toSeq.flatMap { case (key, values) => values.map(value => key -> value)})
}
