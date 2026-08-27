package utils

import play.api.i18n.Lang

import java.util.Locale

package object data {
  def languageCodeToName(code: String)(implicit lang: Lang): String = {
    Locale.forLanguageTag(code).getDisplayLanguage(lang.toLocale)
  }
}
