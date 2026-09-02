package services

import models.{Pid, PidType}
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.after
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Named}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Periodically checks that DOI target pages still resolve, caching the
 * result so it doesn't need to be re-checked on every landing page view.
 */
class TargetHealthCheckService @Inject()(
  @Named("healthCheck") ws: WSClient,
  pidService: PidService,
  checkService: TargetCheckService,
  config: Configuration,
  actorSystem: ActorSystem,
)(implicit ec: ExecutionContext, mat: Materializer) {

  private val logger = Logger(classOf[TargetHealthCheckService])

  private def requestTimeout: FiniteDuration = config.get[FiniteDuration]("targetHealthCheck.requestTimeout")
  private def parallelism: Int = config.get[Int]("targetHealthCheck.parallelism")
  private def throttleElements: Int = config.get[Int]("targetHealthCheck.throttle.elements")
  private def throttlePer: FiniteDuration = config.get[FiniteDuration]("targetHealthCheck.throttle.per")
  private def retries: Int = config.get[Int]("targetHealthCheck.retries")
  private def retryDelay: FiniteDuration = config.get[FiniteDuration]("targetHealthCheck.retryDelay")

  private def isOk(response: WSResponse): Boolean = response.status >= 200 && response.status < 400

  /**
   * Attempts a HEAD request, retrying (after a delay) on a bad status or a
   * failed request, up to `attemptsLeft` more times. Returns the last
   * response received, or the last error message if every attempt failed
   * outright - either way, transient errors that clear within a retry or
   * two never get recorded as a failure.
   */
  private def attempt(target: String, attemptsLeft: Int): Future[Either[String, WSResponse]] = {
    ws.url(target).withRequestTimeout(requestTimeout).head().transformWith {
      case Success(response) if isOk(response) || attemptsLeft <= 0 =>
        Future.successful(Right(response))
      case Success(_) =>
        after(retryDelay, actorSystem.scheduler)(attempt(target, attemptsLeft - 1))
      case Failure(_) if attemptsLeft > 0 =>
        after(retryDelay, actorSystem.scheduler)(attempt(target, attemptsLeft - 1))
      case Failure(e) =>
        Future.successful(Left(e.getMessage))
    }
  }

  /**
   * Checks a single target, records the result, and returns whether it was ok
   * (so callers can aggregate a checked/failed summary without shared mutable state).
   */
  private def checkOne(pidType: PidType.Value, value: String, target: String): Future[Boolean] = {
    attempt(target, retries).flatMap {
      case Right(response) =>
        val ok = isOk(response)
        checkService.recordResult(pidType, value, target, ok, Some(response.status), None).map(_ => ok)
      case Left(errorMessage) =>
        logger.warn(s"Target check failed for $pidType $value ($target): $errorMessage")
        checkService.recordResult(pidType, value, target, ok = false, statusCode = None, error = Some(errorMessage)).map(_ => false)
    }
  }

  /**
   * Enumerate all non-tombstoned pids of the given type, paging through PidService.findAll.
   */
  private def allTargets(pidType: PidType.Value): Future[Seq[Pid]] = {
    val pageSize = 100
    def loop(offset: Int, acc: Seq[Pid]): Future[Seq[Pid]] =
      pidService.findAll(pidType, offset, pageSize).flatMap { page =>
        if (page.isEmpty) Future.successful(acc)
        else loop(offset + pageSize, acc ++ page)
      }
    loop(0, Seq.empty)
  }

  def runCheck(pidType: PidType.Value): Future[Done] = {
    allTargets(pidType).flatMap { pids =>
      val targets = pids.filter(_.tombstone.isEmpty)
      Source(targets)
        .throttle(throttleElements, throttlePer)
        .mapAsyncUnordered(parallelism) { pid =>
          // mapAsyncUnordered fails (and aborts) the whole stream if any one
          // element's Future fails, so an unexpected error here (e.g. a
          // transient DB write failure in checkOne) must not propagate -
          // otherwise it would silently skip every remaining PID in this run.
          checkOne(pidType, pid.value, pid.target).recover {
            case e =>
              logger.error(s"Unexpected error checking $pidType ${pid.value} (${pid.target})", e)
              false
          }
        }
        // Sink.fold processes elements one at a time regardless of the
        // concurrency of the mapAsyncUnordered stage above, so this is safe
        // without any external synchronization.
        .runWith(Sink.fold((0, 0)) { case ((checkedCount, failedCount), ok) =>
          (checkedCount + 1, if (ok) failedCount else failedCount + 1)
        })
        .map { case (checkedCount, failedCount) =>
          logger.info(s"$pidType target health check complete: checked=$checkedCount failed=$failedCount")
          Done
        }
    }
  }
}
