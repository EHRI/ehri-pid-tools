package models

import play.api.Configuration

/**
 * Information that encapsulates access and publication
 * settings for a DOI repository.
 */
case class DoiProfile(
  prefix: String,
  resolver: String,
  apiBaseUrl: String,
  repositoryId: String,
  repositorySecret: String,
  showHidden: Boolean,
)

object DoiProfile {
  def fromConfig(id: String, config: Configuration): DoiProfile = {
    val configOpt = config.getOptional[Configuration](s"doi.$id")
    val doiConfig = configOpt.getOrElse(config.get[Configuration]("doi.default"))
    DoiProfile(
      doiConfig.get[String]("prefix"),
      doiConfig.get[String]("resolverUrl"),
      doiConfig.get[String]("api.baseUrl"),
      doiConfig.get[String]("api.repositoryId"),
      doiConfig.get[String]("api.repositorySecret"),
      doiConfig.get[Boolean]("showHidden")
    )
  }
}
