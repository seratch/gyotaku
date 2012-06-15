package gyotaku

import java.io._
import scala.collection.JavaConverters._

import com.twitter.util.Eval

import org.apache.commons.io.FileUtils
import org.openqa.selenium._

import org.htmlcleaner.{ PrettyHtmlSerializer, CleanerProperties, HtmlCleaner }

case class GyotakuTarget(
  name: String,
  driver: WebDriver,
  charset: String,
  url: String,
  prettify: Boolean,
  replaceNoDomainOnly: Boolean,
  debug: Boolean)

object Executor {

  import Utils._

  def execute(config: Config, output: String): Unit = {
    val defaultDriverSourceCode = "new org.openqa.selenium.firefox.FirefoxDriver"
    val target = GyotakuTarget(
      name = config.name,
      driver = {
        val eval = new Eval(None)
        config.driver.map {
          d =>
            d.path.map {
              path => eval.apply[WebDriver](new File(path))
            }.getOrElse {
              eval.apply[WebDriver](d.source.getOrElse(defaultDriverSourceCode))
            }
        }.getOrElse {
          eval.apply[WebDriver](defaultDriverSourceCode)
        }
      },
      charset = config.charset.getOrElse("UTF-8"),
      url = config.url,
      prettify = config.prettify.getOrElse(false),
      replaceNoDomainOnly = config.replaceNoDomainOnly.getOrElse(true),
      debug = config.debug.getOrElse(false)
    )

    try {

      // HTTP request
      target.driver.get(target.url)

      val currentUrl = target.driver.getCurrentUrl

      val executor = Executor(
        target = target,
        output = output,
        currentUrl = currentUrl
      )

      println("Target: " + currentUrl)
      print("Now downloading")

      // download resources (from src/href attributes)

      target.driver.findElements(By.tagName("img")).asScala.par
        .filter(_ != null)
        .map(e => ignore(removeQueryString(e.getAttribute("src"))))
        .filter(src => isNotEmpty(src))
        .distinct
        .foreach(src => executor.download(src))

      target.driver.findElements(By.tagName("input")).asScala.par
        .filter(_ != null)
        .filter(e => ignore(e.getAttribute("type") == "image"))
        .map(e => ignore(removeQueryString(e.getAttribute("src"))))
        .filter(src => isNotEmpty(src))
        .distinct
        .foreach(src => executor.download(src))

      target.driver.findElements(By.tagName("link")).asScala.par
        .filter(_ != null)
        .filter(e => ignore(e.getAttribute("type") == "text/css"))
        .map(e => ignore(removeQueryString(e.getAttribute("href"))))
        .filter(src => isNotEmpty(src))
        .distinct
        .foreach(src => executor.downloadCss(src))

      target.driver.findElements(By.tagName("script")).asScala.par
        .filter(_ != null)
        .map(e => removeQueryString(e.getAttribute("src")))
        .filter(src => isNotEmpty(src))
        .distinct
        .foreach(src => executor.download(src))

      val outputBaseDir = output + "/" + target.name

      val originalHtml = target.driver.getPageSource
      writeFile(
        path = outputBaseDir + "/original.html",
        content = originalHtml.getBytes(target.charset)
      )

      executor.downloadImportedCss(domainOnly(currentUrl), dirname(currentUrl), originalHtml)
      executor.downloadUrlInCss(domainOnly(currentUrl), dirname(currentUrl), originalHtml)

      // copy all the resources on the same domain

      val sameDomainDir = outputBaseDir + "/__local__/" +
        currentUrl.replaceFirst("(https?)://", "$1__").split("/").head
      mkdir(sameDomainDir)
      FileUtils.copyDirectory(new File(sameDomainDir), new File(outputBaseDir + "/__local__"))

      FileUtils.listFiles(new File(outputBaseDir + "/__local__"), Array("css"), true).asScala.foreach { file =>
        // Need to replace the relative path. The reason is...
        // the original file is located at __local__/http__www.example.com/css/style.css
        // but this one will be located at __local__/css/style.css
        val updated = FileUtils.readFileToString(file)
          .replaceAll("""url\('\.\./""", "url('")
          .replaceAll("""url\("\.\./""", "url(\"")
          .replaceAll("""url\(\.\./""", "url(")
        FileUtils.write(file, updated)
      }

      val pathFromLocalRoot = "__local__/" + replaceHttp(dirname(currentUrl)).split("/").tail.mkString("/")

      val htmlToReplace = if (target.prettify) {
        try {
          val cleanerProps = new CleanerProperties
          new PrettyHtmlSerializer(cleanerProps).getAsString(
            new HtmlCleaner(cleanerProps).clean(originalHtml)
          )
        } catch {
          case e =>
            println("Failed to prettify because of " + e.getClass + "(" + e.getMessage + ")")
            originalHtml
        }
      } else {
        originalHtml
      }

      val indexHtml = replaceSrcAttributePath(
        src = replaceCssPath(
          src = replaceUrlsInCss(
            src = htmlToReplace,
            pathFromLocalRoot = "__local__",
            replaceNoDomainOnly = target.replaceNoDomainOnly),
          pathFromLocalRoot = pathFromLocalRoot,
          replaceNoDomainOnly = target.replaceNoDomainOnly),
        pathFromLocalRoot = pathFromLocalRoot,
        replaceNoDomainOnly = target.replaceNoDomainOnly)

      writeFile(
        path = outputBaseDir + "/index.html",
        content = indexHtml.getBytes(target.charset)
      )

    } catch {
      case e => e.printStackTrace
    } finally {
      target.driver.quit()
      println("Finished.")
    }
  }

}

case class Executor(target: GyotakuTarget, output: String, currentUrl: String) {

  import Utils._

  val protocol = currentUrl.split("/").head.replaceFirst(":", "")

  val outputBaseDir = output + "/" + target.name
  val outputOriginalBaseDir = outputBaseDir + "/original"

  mkdir(outputBaseDir)
  mkdir(outputOriginalBaseDir)

  def download(src: String): Unit = {
    print(".")
    val normalizedPath = normalizeUrl(src)
    try {
      val url = new java.net.URL(normalizedPath)
      val conn = url.openConnection()
      conn.setReadTimeout(10000)
      using(conn.getInputStream) {
        stream =>
          val path = outputBaseDir + "/" + replaceHttpToLocal(normalizedPath)
          prepareDir(path)
          writeFile(path, readAsByteArray(stream))
      }
    } catch {
      case e => {
        if (target.debug) {
          println("Failed to download! url: " + normalizedPath + " - " + e.getClass.getName + " - " + e.getMessage)
        }
      }
    }
  }

  def downloadCss(src: String): Unit = {
    print(".")
    val normalizedPath = normalizeUrl(src)
    try {
      val url = new java.net.URL(normalizedPath)
      val conn = url.openConnection()
      conn.setReadTimeout(10000)
      using(conn.getInputStream) {
        stream =>
          val bytes = readAsByteArray(stream)

          val originalOutputPath = outputOriginalBaseDir + "/" + replaceHttp(normalizedPath)
          prepareDir(originalOutputPath)
          writeFile(path = originalOutputPath, content = bytes)

          val pathToLocalRoot = (1 until replaceHttp(normalizedPath).split("/").size).map(_ => "..").mkString("/")
          val pathToLocalRootWithDomain = pathToLocalRoot + {
            if (domainOnly(normalizedPath) != domainOnly(currentUrl)) "/" + replaceHttp(normalizedPath).split("/").head
            else ""
          }

          val updatedCss = replaceUrlsInCss(new String(bytes, target.charset), pathToLocalRootWithDomain, target.replaceNoDomainOnly)
          val updatedBytes = updatedCss.getBytes(target.charset)
          val outputPath = outputBaseDir + "/" + replaceHttpToLocal(normalizedPath)
          prepareDir(outputPath)
          writeFile(path = outputPath, content = updatedBytes)
          downloadImportedCss(domainOnly(normalizedPath), dirname(normalizedPath), new String(bytes, target.charset))
          downloadUrlInCss(domainOnly(normalizedPath), dirname(normalizedPath), new String(bytes, target.charset))
      }
    } catch {
      case e =>
        if (target.debug) {
          println("Failed to download! url: " + normalizedPath + " - " + e.getClass.getName + " - " + e.getMessage)
        }
    }
  }

  def downloadImportedCss(domain: String, currentDirname: String, css: String): Unit = {
    findAllImportedCssPath(css).par.foreach {
      case null =>
      case full if full.startsWith("http://") || full.startsWith("https://") => downloadCss(full)
      case absolute if absolute.startsWith("/") => downloadCss(protocol + "://" + domain + absolute)
      case relative => downloadCss(currentDirname + "/" + relative)
    }
  }

  def downloadUrlInCss(domain: String, currentDirname: String, css: String): Unit = {
    findAllUrlsInCss(css).par.foreach {
      case full if full.startsWith("http://") || full.startsWith("https://") => download(full)
      case absolute if absolute.startsWith("/") => download(protocol + "://" + domain + absolute)
      case relative => download(currentDirname + "/" + relative)
    }
  }

}
