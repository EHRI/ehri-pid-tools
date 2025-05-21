package controllers

import models.RelatedIdentifierType
import play.api.Configuration

import javax.inject.Inject

case class AppConfig @Inject()(config: Configuration) {

  def showHidden: Boolean = config.get[Boolean]("doi.showHidden")

  def doiPrefix: String =
    config.get[String]("doi.prefix")

  /**
   * Resolve a DOI.
   *
   * @param doi  the DOI.
   * @param prod whether to use the configurable resolver,
   *             or the global one
   * @return a DOI URL
   */
  def doiUrl(doi: String, prod: Boolean = true): String =
    if (prod) s"https://doi.org/$doi" else s"${config.get[String]("doi.resolverUrl")}/$doi"

  def resolvePid(pid: String, pidType: RelatedIdentifierType.Value, staging: Boolean = true): Option[String] = pidType match {
    case RelatedIdentifierType.DOI => Some(doiUrl(pid, staging))
    case RelatedIdentifierType.ARK => Some(s"https://n2t.net/$pid")
    case _ => None
  }
}
