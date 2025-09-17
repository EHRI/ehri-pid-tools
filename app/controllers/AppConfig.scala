package controllers

import models.{DoiProfile, RelatedIdentifierType}
import play.api.Configuration

import javax.inject.Inject

case class AppConfig @Inject()(config: Configuration) {
  /**
   * Resolve a DOI.
   *
   * @param doi  the DOI.
   * @param prod whether to use the configurable resolver,
   *             or the global one
   * @return a DOI URL
   */
  def doiUrl(doi: String, prod: Boolean = true)(implicit profile: DoiProfile): String =
    if (prod) s"https://doi.org/$doi" else s"${profile.resolver}/$doi"

  def resolvePid(pid: String, pidType: RelatedIdentifierType.Value, staging: Boolean = true)(implicit profile: DoiProfile): Option[String] = pidType match {
    case RelatedIdentifierType.DOI => Some(doiUrl(pid, staging))
    case RelatedIdentifierType.ARK => Some(s"https://n2t.net/$pid")
    case _ => None
  }
}
