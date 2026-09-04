package services

import helpers.{AppSpec, resourceAsJson}
import mockws.MockWSHelpers
import models.{DoiMetadata, DoiMetadataList, DoiProfile, JsonApiData}
import play.api.Configuration

import scala.language.implicitConversions

class WsDoiServiceSpec extends AppSpec with MockWSHelpers {
  private def configuration = inject[Configuration]
  private def wsDoiServiceHandle = inject[WsDoiServiceHandle]
  private def wsDoiService = wsDoiServiceHandle.forProfile(DoiProfile.fromConfig("default", configuration))

  "WsDoiService" should {
    "return a list of DOIs" in {
      val response = await(wsDoiService.listDoiMetadata("10.1234"))
      response mustBe resourceAsJson("example-list.json").as[DoiMetadataList]
    }

    "delete DOIs" in {
      val response = await(wsDoiService.deleteDoi("10.1234"))
      response mustBe true
    }

    "get DOIs" in {
      val response = await(wsDoiService.getDoiMetadata("10.1234"))
      response mustBe resourceAsJson("example.json").as[JsonApiData].data.as[DoiMetadata]
    }

    "error if DOIs not found" in {
      intercept[DoiNotFound] {
        await(wsDoiService.getDoiMetadata("NOT/FOUND")) // see mocks
      }
    }

    "error cleanly on a non-JSON error response" in {
      val exception = intercept[DoiServiceException] {
        await(wsDoiService.getDoiMetadata("SERVER/ERROR")) // see mocks
      }
      exception.status mustBe 500
    }

    "register a DOI" in {
      val response = await(wsDoiService.registerDoi(resourceAsJson("example.json").as[JsonApiData].data.as[DoiMetadata]))
      response mustBe resourceAsJson("example.json").as[JsonApiData].data.as[DoiMetadata]
    }

    "update a DOI" in {
      val response = await(wsDoiService.updateDoi("10.1234", resourceAsJson("example.json").as[JsonApiData].data.as[DoiMetadata]))
      response mustBe resourceAsJson("example.json").as[JsonApiData].data.as[DoiMetadata]
    }

    "generate new DOIs" in {
      val newSuffix = wsDoiService.generateSuffix()
      newSuffix must include ("-")
      newSuffix.length mustBe 9
      newSuffix mustNot include ("o")
    }
  }
}
