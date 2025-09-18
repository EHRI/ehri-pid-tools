import helpers.{resourceAsJson, resourceAsString}
import mockws.MockWS
import mockws.MockWSHelpers.Action
import models.JsonApiError
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.{DELETE, GET, HEAD, POST, PUT}

package object mocks {

  // This is a mock for the DOI service that simulates the behavior of the actual service.
  // What's the point of this? Not sure, but we can't use the real service
  // so this will have to do...
  def mockWS(baseUrl: String) = {
    val quoted = java.util.regex.Pattern.quote(baseUrl)
    val regex = s"^$quoted/(.+)".r

    MockWS {

      // DOI service mocks
      case (GET, `baseUrl`) => Action {
        Results.Ok(resourceAsJson("example-list.json"))
      }
      case (GET, regex(doi)) if doi == "NOT/FOUND" => Action {
        Results.NotFound(
          JsonApiError(
            "The resource you are looking for doesn't exist.",
            status = Some("404")
          )
        )
      }
      case (GET, regex(doi)) if doi == "NOT/FINDABLE" => Action {
        // Test fetching a DOI that does not have a 'findable' state:
        val jsonText = resourceAsString("example.json")
        val munged = jsonText.replace("\"state\": \"findable\"", "\"state\": \"registered\"")
        Results.Ok(Json.parse(munged))
      }
      case (GET, regex(_)) => Action {
        Results.Ok(resourceAsJson("example.json"))
      }
      case (POST, `baseUrl`) => Action {
        Results.Created(resourceAsJson("example.json"))
      }
      case (PUT, regex(_)) => Action {
        Results.Ok(resourceAsJson("example.json"))
      }
      case (DELETE, regex(_)) => Action {
        Results.NoContent
      }

      // Preview service mocks
      case (GET, "https://example.com/preview-test") => Action {
        Results.Ok(resourceAsString("preview-test.html"))
      }
      case (HEAD, "https://example.com/preview-test-image.svg") => Action {
        Results.Ok.withHeaders("Content-Type" -> "image/svg+xml")
      }
      case (GET, "https://example.com/preview-test-image.svg") => Action {
        Results.Ok("<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100'><rect width='100' height='100' fill='blue'/></svg>")
          .withHeaders("Content-Type" -> "image/svg+xml")
      }
    }
  }
}
