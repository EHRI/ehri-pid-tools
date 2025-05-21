import helpers.resourceAsJson
import mockws.MockWS
import mockws.MockWSHelpers.Action
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.{DELETE, GET, POST, PUT}

package object mocks {

  // This is a mock for the DOI service that simulates the behavior of the actual service.
  // What's the point of this? Not sure, but we can't use the real service
  // so this will have to do...
  def doiServiceMockWS(apiBaseUrl: String) = {
    val quoted = java.util.regex.Pattern.quote(apiBaseUrl)
    val regex = s"^$quoted/(.+)".r

    MockWS {

      case (GET, `apiBaseUrl`) => Action {
        Results.Ok(resourceAsJson("example-list.json"))
      }
      case (GET, regex(doi)) if doi == "NOT/FOUND" => Action {
        Results.NotFound(Json.parse(
          """{"errors":[{"status":"404","title":"The resource you are looking for doesn't exist."}]}"""))
      }
      case (GET, regex(_)) => Action {
        Results.Ok(resourceAsJson("example.json"))
      }
      case (POST, `apiBaseUrl`) => Action {
        Results.Created(resourceAsJson("example.json"))
      }
      case (PUT, regex(_)) => Action {
        Results.Ok(resourceAsJson("example.json"))
      }
      case (DELETE, regex(_)) => Action {
        Results.NoContent
      }
    }
  }
}
