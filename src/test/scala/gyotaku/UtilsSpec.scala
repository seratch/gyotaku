package gyotaku

import org.scalatest._
import org.scalatest.matchers._

class UtilsSpec extends FlatSpec with ShouldMatchers {

  behavior of "CommandLine$ functions"

  it should "have normalizeUrl" in {
    {
      val url = Utils.normalizeUrl("http://example.com/bluebar/f390460/cache/public/../../images/shared/noise-1.png")
      url should equal("http://example.com/bluebar/f390460/images/shared/noise-1.png")
    }
    {
      val url = Utils.normalizeUrl("http://example.com/include/css/../../../images/xxx/bg.gif")
      url should equal("http://example.com/images/xxx/bg.gif")
    }
    {
      val url = Utils.normalizeUrl("http://example.com/xxx/../../images/yyy/map05.jpg")
      url should equal("http://example.com/images/yyy/map05.jpg")
    }
  }

  it should "have replaceUrlsInCss" in {
    val src = "@import url(\"/css/12/reset.1.1.css\");"
    val result = Utils.replaceUrlsInCss(src, "__local__/xxx", true)
    result should equal("@import url(\"__local__/xxx/css/12/reset.1.1.css\");")
  }

  it should "replace correctly" in {
    {
      val favicon = "<link rel=\"example.com icon\" href=\"favicon.ico\" />"
      import Utils._
      val result = replaceSrcAttributePath(
        src = replaceCssPath(
          src = replaceUrlsInCss(
            src = favicon,
            pathFromLocalRoot = "__local__",
            replaceNoDomainOnly = true),
          pathFromLocalRoot = "__local__",
          replaceNoDomainOnly = true),
        pathFromLocalRoot = "__local__",
        replaceNoDomainOnly = true)
      result should equal("<link rel=\"example.com icon\" href=\"__local__/favicon.ico\" />")
    }
    {
      val favicon = "<link href=\"favicon.ico\" rel=\"example.com icon\" />"
      import Utils._
      val result = replaceSrcAttributePath(
        src = replaceCssPath(
          src = replaceUrlsInCss(
            src = favicon,
            pathFromLocalRoot = "__local__",
            replaceNoDomainOnly = true),
          pathFromLocalRoot = "__local__",
          replaceNoDomainOnly = true),
        pathFromLocalRoot = "__local__",
        replaceNoDomainOnly = true)
      result should equal("<link href=\"__local__/favicon.ico\" rel=\"example.com icon\" />")
    }
  }

}

// vim: set ts=4 sw=4 et:
