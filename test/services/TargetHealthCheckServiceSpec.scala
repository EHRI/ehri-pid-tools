package services

import helpers.AppSpec
import models.{Pid, PidType}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{Answers, Mockito}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TargetHealthCheckServiceSpec extends AppSpec with MockitoSugar {

  private def config = inject[Configuration]
  private def actorSystem = inject[ActorSystem]
  protected implicit def mat: Materializer = inject[Materializer]

  // RETURNS_SELF means any method returning WSRequest (e.g. withRequestTimeout)
  // just returns this same mock, without needing to stub each chained call.
  private def mockFluentRequest(): WSRequest =
    Mockito.mock(classOf[WSRequest], Answers.RETURNS_SELF)

  private def mockRequest(status: Int): WSRequest = {
    val request = mockFluentRequest()
    val response = mock[WSResponse]
    when(response.status).thenReturn(status)
    when(request.head()).thenReturn(Future.successful(response))
    request
  }

  private def failingRequest(error: Exception): WSRequest = {
    val request = mockFluentRequest()
    when(request.head()).thenReturn(Future.failed(error))
    request
  }

  "TargetHealthCheckService" should {
    "record an ok result for a 200 response" in {
      val ws = mock[WSClient]
      val pidService = mock[PidService]
      val checkService = mock[TargetCheckService]
      val okRequest = mockRequest(200)
      when(ws.url("https://example.com/ok")).thenReturn(okRequest)
      when(pidService.findAll(eqTo(PidType.DOI), any(), any()))
        .thenReturn(Future.successful(Seq(Pid(PidType.DOI, "10.1/ok",
          "https://example.com/ok"))), Future.successful(Seq.empty))
      when(checkService.recordResult(any(), any(), any(), any(), any(), any())).thenReturn(Future.successful(()))

      val service = new TargetHealthCheckService(ws, pidService, checkService, config, actorSystem)
      await(service.runCheck(PidType.DOI))

      verify(checkService).recordResult(PidType.DOI, "10.1/ok",
        "https://example.com/ok", ok = true, Some(200), None)
    }

    "record a failure for a non-2xx/3xx response" in {
      val ws = mock[WSClient]
      val pidService = mock[PidService]
      val checkService = mock[TargetCheckService]
      val brokenRequest = mockRequest(404)
      when(ws.url("https://example.com/broken")).thenReturn(brokenRequest)
      when(pidService.findAll(eqTo(PidType.DOI), any(), any()))
        .thenReturn(Future.successful(Seq(Pid(PidType.DOI, "10.1/broken",
          "https://example.com/broken"))), Future.successful(Seq.empty))
      when(checkService.recordResult(any(), any(), any(), any(), any(), any())).thenReturn(Future.successful(()))

      val service = new TargetHealthCheckService(ws, pidService, checkService, config, actorSystem)
      await(service.runCheck(PidType.DOI))

      verify(checkService).recordResult(PidType.DOI, "10.1/broken",
        "https://example.com/broken", ok = false, Some(404), None)
    }

    "record a failure with no status code when the request itself fails" in {
      val ws = mock[WSClient]
      val pidService = mock[PidService]
      val checkService = mock[TargetCheckService]
      val error = new java.net.ConnectException("Connection refused")
      val unreachableRequest = failingRequest(error)
      when(ws.url("https://example.com/unreachable")).thenReturn(unreachableRequest)
      when(pidService.findAll(eqTo(PidType.DOI), any(), any()))
        .thenReturn(Future.successful(Seq(Pid(PidType.DOI, "10.1/unreachable",
          "https://example.com/unreachable"))), Future.successful(Seq.empty))
      when(checkService.recordResult(any(), any(), any(), any(), any(), any())).thenReturn(Future.successful(()))

      val service = new TargetHealthCheckService(ws, pidService, checkService, config, actorSystem)
      await(service.runCheck(PidType.DOI))

      verify(checkService).recordResult(PidType.DOI, "10.1/unreachable",
        "https://example.com/unreachable", ok = false, None, Some("Connection refused"))
    }

    "recover from a transient failure on retry" in {
      val ws = mock[WSClient]
      val pidService = mock[PidService]
      val checkService = mock[TargetCheckService]
      val badResponse = mock[WSResponse]
      when(badResponse.status).thenReturn(503)
      val goodResponse = mock[WSResponse]
      when(goodResponse.status).thenReturn(200)
      val flakyRequest = mockFluentRequest()
      // first call fails transiently, second call (the retry) succeeds
      when(flakyRequest.head()).thenReturn(Future.successful(badResponse), Future.successful(goodResponse))
      when(ws.url("https://example.com/flaky")).thenReturn(flakyRequest)
      when(pidService.findAll(eqTo(PidType.DOI), any(), any()))
        .thenReturn(Future.successful(Seq(Pid(PidType.DOI, "10.1/flaky",
          "https://example.com/flaky"))), Future.successful(Seq.empty))
      when(checkService.recordResult(any(), any(), any(), any(), any(), any())).thenReturn(Future.successful(()))

      val service = new TargetHealthCheckService(ws, pidService, checkService, config, actorSystem)
      await(service.runCheck(PidType.DOI))

      // only the final, successful outcome is recorded - the transient 503 is never persisted
      verify(checkService).recordResult(PidType.DOI, "10.1/flaky", "https://example.com/flaky", ok = true, Some(200), None)
      verify(checkService, times(1)).recordResult(any(), any(), any(), any(), any(), any())
    }

    "continue checking other targets if recording one result fails" in {
      val ws = mock[WSClient]
      val pidService = mock[PidService]
      val checkService = mock[TargetCheckService]
      val requestA = mockRequest(200)
      val requestB = mockRequest(200)
      when(ws.url("https://example.com/a")).thenReturn(requestA)
      when(ws.url("https://example.com/b")).thenReturn(requestB)
      when(pidService.findAll(eqTo(PidType.DOI), any(), any()))
        .thenReturn(Future.successful(Seq(
          Pid(PidType.DOI, "10.1/a", "https://example.com/a"),
          Pid(PidType.DOI, "10.1/b", "https://example.com/b"),
        )), Future.successful(Seq.empty))
      // recording the first target's result fails outright (e.g. a transient DB error)
      when(checkService.recordResult(eqTo(PidType.DOI), eqTo("10.1/a"), any(), any(), any(), any()))
        .thenReturn(Future.failed(new RuntimeException("DB write failed")))
      when(checkService.recordResult(eqTo(PidType.DOI), eqTo("10.1/b"), any(), any(), any(), any()))
        .thenReturn(Future.successful(()))

      val service = new TargetHealthCheckService(ws, pidService, checkService, config, actorSystem)
      // the run itself must complete despite the one failed write - if it doesn't,
      // await() below throws and fails this test
      await(service.runCheck(PidType.DOI)) mustBe org.apache.pekko.Done

      // the other target was still checked and recorded, proving the stream wasn't aborted
      verify(checkService).recordResult(PidType.DOI, "10.1/b", "https://example.com/b", ok = true, Some(200), None)
    }

    "skip tombstoned pids" in {
      val ws = mock[WSClient]
      val pidService = mock[PidService]
      val checkService = mock[TargetCheckService]
      val tombstoned = Pid(PidType.DOI, "10.1/gone", "https://example.com/gone",
        Some(models.Tombstone(java.time.Instant.now(), "system", "gone")))
      when(pidService.findAll(eqTo(PidType.DOI), any(), any()))
        .thenReturn(Future.successful(Seq(tombstoned)), Future.successful(Seq.empty))

      val service = new TargetHealthCheckService(ws, pidService, checkService, config, actorSystem)
      await(service.runCheck(PidType.DOI))

      verify(ws, times(0)).url(any())
      verify(checkService, times(0)).recordResult(any(), any(), any(), any(), any(), any())
    }
  }
}
