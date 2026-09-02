package services

import models.PidType
import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
 * Schedules the periodic target health check for the PID types configured
 * at targetHealthCheck.pidTypes. Bound as an eager singleton (see Module)
 * so it starts as soon as the application boots.
 */
@Singleton
class TargetHealthChecker @Inject()(
  actorSystem: ActorSystem,
  service: TargetHealthCheckService,
  config: Configuration,
  lifecycle: ApplicationLifecycle,
)(implicit ec: ExecutionContext) {

  private val logger = Logger(classOf[TargetHealthChecker])

  /**
   * Runs the check for each configured PID type in turn (not concurrently),
   * so the per-run concurrency/throttle settings aren't multiplied by the
   * number of PID types being checked.
   */
  private def runAll(pidTypes: Seq[PidType.Value]): Future[Unit] =
    pidTypes.foldLeft(Future.successful(())) { (acc, pidType) =>
      acc.flatMap { _ =>
        service.runCheck(pidType).map(_ => ()).recover {
          case e =>
            logger.error(s"$pidType target health check run failed", e)
        }
      }
    }

  if (config.get[Boolean]("targetHealthCheck.enabled")) {
    val pidTypes = config.get[Seq[String]]("targetHealthCheck.pidTypes").map(PidType.withName)
    val initialDelay = config.get[FiniteDuration]("targetHealthCheck.initialDelay")
    val interval = config.get[FiniteDuration]("targetHealthCheck.interval")

    val cancellable = actorSystem.scheduler.scheduleWithFixedDelay(initialDelay, interval) { () =>
      runAll(pidTypes)
    }

    lifecycle.addStopHook(() => Future.successful(cancellable.cancel()))
  } else {
    logger.info("Target health check is disabled (targetHealthCheck.enabled = false)")
  }
}
