package helpers

import mockws.MockWSHelpers
import org.scalatest.TestData
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.ws.WSClient
import play.api.{Application, Configuration, Environment}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, Injecting}

import scala.language.implicitConversions

abstract class AppSpec extends PlaySpec with GuiceOneAppPerTest with DefaultAwaitTimeout with FutureAwaits with Injecting with MockWSHelpers {
  // This is a base class for all tests in the application.
  // It provides common functionality and configuration for all tests.
  // You can add any common setup or utility methods here.

  private val config = Configuration.load(Environment.simple())
  private val apiBaseUrl = config.get[String]("doi.api.baseUrl")

  protected def newAppBuilder(overrides: GuiceableModule*): GuiceApplicationBuilder = {
    import play.api.inject.bind
    new GuiceApplicationBuilder()
      .overrides(overrides: _*)
      .overrides(bind[WSClient].toInstance(mocks.mockWS(apiBaseUrl)))
  }

  implicit override def newAppForTest(testData: TestData): Application = {
      newAppBuilder().build()
  }
}
