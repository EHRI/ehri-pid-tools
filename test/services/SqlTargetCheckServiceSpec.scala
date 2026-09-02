package services

import helpers.{AppSpec, DatabaseSupport}
import models.PidType

class SqlTargetCheckServiceSpec extends AppSpec with DatabaseSupport {

  private def checkService = inject[SqlTargetCheckService]

  "SqlTargetCheckService" should {
    "record and report a failing check" in {
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0523",
        "https://example.com/pid-test-1", ok = false, Some(404), None))

      val failures = await(checkService.latestFailures(Some(PidType.DOI)))
      failures.map(_.value) must contain("10.14454/fxws-0523")

      val failure = failures.find(_.value == "10.14454/fxws-0523").get
      failure.target mustBe "https://example.com/pid-test-1"
      failure.statusCode mustBe Some(404)
      failure.ok mustBe false
    }

    "not report a passing check" in {
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0523",
        "https://example.com/pid-test-1", ok = true, Some(200), None))

      val failures = await(checkService.latestFailures(Some(PidType.DOI)))
      failures.map(_.value) must not contain "10.14454/fxws-0523"
    }

    "upsert on repeated checks, keeping only the latest result" in {
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0523",
        "https://example.com/pid-test-1", ok = false, Some(500), None))
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0523",
        "https://example.com/pid-test-1", ok = false, Some(404), Some("boom")))

      val failures = await(checkService.latestFailures(Some(PidType.DOI)))
      failures.count(_.value == "10.14454/fxws-0523") mustBe 1
      failures.find(_.value == "10.14454/fxws-0523").get.statusCode mustBe Some(404)
      failures.find(_.value == "10.14454/fxws-0523").get.error mustBe Some("boom")
    }

    "record a failure with no status code (e.g. connection error)" in {
      await(checkService.recordResult(PidType.DOI, "10.14454/1234/1234/1234/1234",
        "https://example.com/pid-test-3", ok = false, None, Some("Connection refused")))

      val failure = await(checkService.latestFailures(Some(PidType.DOI)))
        .find(_.value == "10.14454/1234/1234/1234/1234")
      failure mustBe defined
      failure.get.statusCode mustBe None
      failure.get.error mustBe Some("Connection refused")
    }

    "report failures across all PID types" in {
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0523",
        "https://example.com/pid-test-1", ok = false, Some(404), None))
      await(checkService.recordResult(PidType.ARK, "12345/12345678",
        "https://example.com/pid-test-4", ok = false, Some(500), None))

      val failures = await(checkService.latestFailures())
      failures.map(f => f.ptype -> f.value) must contain allOf(
        (PidType.DOI, "10.14454/fxws-0523"),
        (PidType.ARK, "12345/12345678"),
      )
    }

    "summarize checked/failing counts per PID type" in {
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0523",
        "https://example.com/pid-test-1", ok = false, Some(404), None))
      await(checkService.recordResult(PidType.DOI, "10.14454/fxws-0524",
        "https://example.com/pid-test-2", ok = true, Some(200), None))
      await(checkService.recordResult(PidType.ARK, "12345/12345678",
        "https://example.com/pid-test-4", ok = true, Some(200), None))

      val summary = await(checkService.summary())
      val doiSummary = summary.find(_.ptype == PidType.DOI).get
      doiSummary.checked mustBe 2
      doiSummary.failing mustBe 1
      doiSummary.lastCheckedAt mustBe defined

      val arkSummary = summary.find(_.ptype == PidType.ARK).get
      arkSummary.checked mustBe 1
      arkSummary.failing mustBe 0
    }
  }
}
