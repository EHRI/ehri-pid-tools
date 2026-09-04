package services

import helpers.AppSpec

class ConfigAuthServiceSpec extends AppSpec {

  private def authService: ConfigAuthService = inject[ConfigAuthService]

  private def encode(s: String): String =
    java.util.Base64.getEncoder.encodeToString(s.getBytes("UTF-8"))

  "ConfigAuthService" should {
    "authenticate a valid client id/secret pair" in {
      await(authService.authenticate(encode("system:changeme"))) mustBe Some("system")
    }

    "reject an incorrect secret" in {
      await(authService.authenticate(encode("system:wrong"))) mustBe None
    }

    "reject an unknown client id" in {
      await(authService.authenticate(encode("nobody:changeme"))) mustBe None
    }

    "reject a token with no colon separator" in {
      await(authService.authenticate(encode("nocolon"))) mustBe None
    }

    "reject a malformed, non-base64 token without throwing" in {
      await(authService.authenticate("not-valid-base64!!!")) mustBe None
    }
  }
}
