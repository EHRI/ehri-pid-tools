import com.google.inject.{AbstractModule, Provides}
import org.apache.pekko.stream.Materializer
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfigFactory}
import play.api.{Configuration, Environment}
import services.TargetHealthChecker

import javax.inject.{Named, Singleton}
import scala.concurrent.Future

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[TargetHealthChecker]).asEagerSingleton()
  }

  @Provides
  @Singleton
  @Named("healthCheck")
  def healthCheckWsClient(
    configuration: Configuration,
    environment: Environment,
    materializer: Materializer,
    lifecycle: ApplicationLifecycle,
  ): WSClient = {
    val baseConfig = AhcWSClientConfigFactory.forConfig(configuration.underlying, environment.classLoader)
    // maxConnectionsPerHost is limited on this Health Check WS so we don't overload target servers.
    val maxConnectionsPerHost = configuration.get[Int]("targetHealthCheck.maxConnectionsPerHost")
    val client = AhcWSClient(baseConfig.copy(maxConnectionsPerHost = maxConnectionsPerHost), None)(materializer)
    lifecycle.addStopHook(() => Future.successful(client.close()))
    client
  }
}
