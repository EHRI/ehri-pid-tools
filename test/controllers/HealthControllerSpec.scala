package controllers

import helpers.{AppSpec, DatabaseSupport}
import models.PidType
import play.api.test.Helpers._
import play.api.test._
import services.TargetCheckService

class HealthControllerSpec extends AppSpec with DatabaseSupport {

  private def controller = inject[HealthController]
  private def checkService = inject[TargetCheckService]

  "HealthController GET" should {
    "render an empty state when no checks have been run" in {
      val request = FakeRequest(GET, routes.HealthController.index().url)
      val result = controller.index().apply(request)

      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include("No health checks have been run yet.")
    }

    "render a DOI summary card with the ok count big and failures/tombstones as sublabels" in {
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0523", "https://example.com/pid-test-1", ok = false, Some(404), None))
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0524", "https://example.com/pid-test-2", ok = true, Some(200), None))

      val request = FakeRequest(GET, routes.HealthController.index().url)
      val result = controller.index().apply(request)

      status(result) mustBe OK
      val body = contentAsString(result)
      body must include(routes.DoiController.health().url)
      body must include("doi_logo.svg")
      // one failure, one ok, recorded above: the big number is the ok count
      body must include(">1<")
      body must include("health-card-sublabel-failing")
      body must include("1 failing to resolve")
      body must include("1 removed")
    }

    "shows zero failing when no DOIs are failing" in {
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0523", "https://example.com/pid-test-1", ok = true, Some(200), None))

      val request = FakeRequest(GET, routes.HealthController.index().url)
      val result = controller.index().apply(request)

      status(result) mustBe OK
      val body = contentAsString(result)
      body must include(routes.DoiController.health().url)
      body must include(">1<") // ok count
      body must include("0 failing to resolve")
      body must include("1 removed")
    }
  }
}
