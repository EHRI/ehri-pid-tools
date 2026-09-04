package models

import helpers.resourceAsJson
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsObject

class DoiMetadataSpec extends PlaySpec {

  "DoiMetadata" should {
    "correctly parse the id, prefix, and suffix" in {
      val json = resourceAsJson("example.json")
      val doiMetadata = json.as[JsonApiData].data.as[DoiMetadata]
      doiMetadata.id mustBe Some("10.14454/fxws-0523")
      doiMetadata.prefix mustBe "10.14454"
      doiMetadata.suffix mustBe "fxws-0523"
    }

    "not truncate a suffix containing extra slashes" in {
      val doiMetadata = DoiMetadata(Some("10.14454/fxws-0523/v1"), None, JsObject.empty)
      doiMetadata.prefix mustBe "10.14454"
      doiMetadata.suffix mustBe "fxws-0523/v1"
    }
  }

  "correctly parse doi metadata" in {
    val json = resourceAsJson("example.json")
    val doiMetadata = json.as[JsonApiData].data.as[DoiMetadata]
    doiMetadata.attributes.asOpt[DataCiteMetadata].isDefined mustBe true
  }
}
