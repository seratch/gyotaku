package gyotaku

import java.io._
import scala.collection.JavaConverters._
import scala.util.control.Exception._

import net.liftweb.json._
import com.twitter.util.Eval

import org.apache.commons.io.FileUtils
import org.openqa.selenium._
import org.htmlcleaner.{ PrettyHtmlSerializer, CleanerProperties, HtmlCleaner }

object Main {

  def gyotaku(input: String, output: String) = main(Array(input, output))

  def main(args: Array[String]): Unit = {

    if (args.size != 2) {
      println("""usage: gytaku [input_dir/input_file] [output_dir]""")
      System.exit(0)
    }
    val input = args(0)
    val output = args(1)

    // scan directory
    val pathToFindInput = new File(input)
    val jsonFiles: Seq[File] = {
      if (!pathToFindInput.isDirectory && input.endsWith("json")) Seq(pathToFindInput)
      else FileUtils.listFiles(new File(input), Array("json"), true).asScala.toSeq
    }
    jsonFiles.foreach {
      jsonFile =>

        // load json with lift-json
        val json: String = using(new FileInputStream(jsonFile)) {
          stream => new String(readAsByteArray(stream), "UTF-8")
        }
        implicit val formats = DefaultFormats
        val config = parse(json).extract[JSONConfig]
        val defaultDriverSourceCode = "new org.openqa.selenium.firefox.FirefoxDriver"
        val target = GyotakuTarget(
          name = config.name,
          driver = {
            val e = new Eval(None)
            config.driver map {
              d =>
                d.path.map { path =>
                  e.apply[WebDriver](new File(path))
                }.getOrElse {
                  e.apply[WebDriver](d.source.getOrElse(defaultDriverSourceCode))
                }
            } getOrElse { e.apply[WebDriver](defaultDriverSourceCode) }
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

          val downloader = Downloader(
            target = target,
            output = output,
            currentUrl = currentUrl
          )

          println("Target: " + currentUrl)
          print("Now downloading")

          // download resources (from src/href attributes)

          target.driver.findElements(By.tagName("img")).asScala.par
            .map(e => ignore(removeQueryString(e.getAttribute("src"))))
            .filter(src => isNotEmpty(src))
            .distinct
            .foreach(src => downloader.download(src))

          target.driver.findElements(By.tagName("input")).asScala.par
            .filter(_.getAttribute("type") == "image")
            .map(e => ignore(removeQueryString(e.getAttribute("src"))))
            .filter(src => isNotEmpty(src))
            .distinct
            .foreach(src => downloader.download(src))

          target.driver.findElements(By.tagName("link")).asScala.par
            .filter(_ != null)
            .filter(_.getAttribute("type") == "text/css")
            .map(e => ignore(removeQueryString(e.getAttribute("href"))))
            .filter(src => isNotEmpty(src))
            .distinct
            .foreach(src => downloader.downloadCss(src))

          target.driver.findElements(By.tagName("script")).asScala.par
            .filter(_ != null)
            .map(e => removeQueryString(e.getAttribute("src")))
            .filter(src => isNotEmpty(src))
            .distinct
            .foreach(src => downloader.download(src))

          val outputBaseDir = output + "/" + target.name

          val originalHtml = target.driver.getPageSource
          writeFile(
            path = outputBaseDir + "/original.html",
            content = originalHtml.getBytes(target.charset)
          )

          downloader.downloadImportedCss(domainOnly(currentUrl), dirname(currentUrl), originalHtml)
          downloader.downloadUrlInCss(domainOnly(currentUrl), dirname(currentUrl), originalHtml)

          // copy all the resources on the same domain

          val sameDomainDir = outputBaseDir + "/__local__/" +
            currentUrl.replaceFirst("(https?)://", "$1__").split("/").head
          mkdir(sameDomainDir)
          FileUtils.copyDirectory(new File(sameDomainDir), new File(outputBaseDir + "/__local__"))

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
          } else { originalHtml }

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

  def using[R <: { def close() }, A](resource: R)(f: R => A): A = ultimately {
    ignoring(classOf[Throwable]) apply resource.close()
  } apply f(resource)

  def ignore[A](op: => A): A = catching(classOf[Throwable]) withApply (_ => null.asInstanceOf[A]) apply (op)

  def isNotEmpty(s: String) = s != null && s.trim.length > 0

  def readAsByteArray(stream: InputStream): Array[Byte] = {
    Stream.continually(stream.read).takeWhile(-1 !=).map(_.toByte).toArray
  }

  def removeQueryString(s: String) = s.replaceFirst("\\?.+$", "")

  def replaceHttp(s: String) = s.replaceFirst("(https?)://", "$1__")

  def replaceHttpToLocal(s: String) = s.replaceFirst("(https?)://", "__local__/$1__")

  def domainOnly(s: String) = s.replaceFirst("https?://", "").split("/").head

  def dirname(s: String) = s.split("/").init.mkString("/")

  def mkdir(dir: String) = new File(dir).mkdirs()

  def prepareDir(path: String) = new File(dirname(path)).mkdirs()

  def normalizeUrl(url: String) = {
    var c = 0
    val elements = url.split("/").reverse
    elements.zipWithIndex.flatMap {
      case (e, idx) =>
        if (idx == elements.size - 3 && c > 0) {
          c = 0
          Seq(e)
        } else if (e == "..") {
          c += 1
          None
        } else if (c > 0) {
          c -= 1
          None
        } else {
          Some(e)
        }
    }.reverse.mkString("/")
  }

  def findAllUrlsInCss(css: String): Seq[String] = {
    """[\s:]url\(['"]([^'"]+)['"]\)""".r
      .findAllIn(css).matchData.map(_.group(1))
      .toSeq
      .union(
        """[\s:]url\(([^'"\))]+)\)""".r
          .findAllIn(css).matchData.map(_.group(1))
          .toSeq
      ).filter(url => isNotEmpty(url))
  }

  def findAllImportedCssPath(css: String): Seq[String] = {
    """@import\s+['"]([^'"]+?\.css)['"]""".r
      .findAllIn(css).matchData.map(_.group(1))
      .toSeq
      .union(
        """@import\s+url\(([^'"]+?\.css)\)""".r
          .findAllIn(css).matchData.map(_.group(1))
          .toSeq
      )
      .union(
        """@import\s+url\(['"]([^'"]+?\.css)['"]\)""".r
          .findAllIn(css).matchData.map(_.group(1))
          .toSeq
      )
      .filter(path => isNotEmpty(path))
      .distinct
  }

  def replaceSrcAttributePath(src: String, pathFromLocalRoot: String, replaceNoDomainOnly: Boolean): String = {
    val modified = src
      // img, js
      .replaceAll("""src=(['"])/""", """src=$1__local__/""")
      .replaceAll("""src=(['"])([^(http)(__local__)])""", """src=$1""" + pathFromLocalRoot + "/$2")
    if (replaceNoDomainOnly) {
      modified
    } else {
      modified.replaceAll("""src="(https?)://""", """src="__local__/$1__""")
    }
  }

  def replaceCssPath(src: String, pathFromLocalRoot: String, replaceNoDomainOnly: Boolean): String = {
    val modified = src
      // css
      .replaceAll("""<link([^>]*?\s+)href=(['"])/""", """<link$1href=$2__local__/""")
      .replaceAll("""<link([^>]*?\s+)href=(['"])([^(http)(__local__)])""", """<link$1href=$2""" + pathFromLocalRoot + "/$3")
      // import absolute path css on the same domain
      .replaceAll("""@import\s+(['"])/([^'"]+?\.css)(['"])""", "@import $1__local__/$2$3")
      .replaceAll("""@import\s+url\(/([^'"]+?\.css)\)""", "@import url(__local__/$1)")
      .replaceAll("""@import\s+url\((['"])/([^'"]+?\.css)(['"])\)""", "@import url($1__local__/$2$3)")
    if (replaceNoDomainOnly) {
      modified
    } else {
      modified.replaceAll("""<link[^>]*?\s+href=(['"])(https?)://""", """<link href=$1__local__/$2__""")
    }
  }

  def replaceUrlsInCss(src: String, pathFromLocalRoot: String, replaceNoDomainOnly: Boolean): String = {
    val replacedNoDomainOnly = src
      // e.g. url(img/foo.gif)
      .replaceAll("""([\s:])url\(([^'"/(\.\./)(http)(__local__)][^\)]+)\)""", """$1url(__local__/$2)""")
      // e.g. url(/img/foo.gif)
      .replaceAll("""([\s:])url\(/([^'"\)]+)\)""", "$1url(" + pathFromLocalRoot + "/$2)")
      // e.g. url("img/foo.gif")
      .replaceAll("""([\s:])url\((["'])([^/(\.\./)(http)(__local__)][^'"]+)(["'])\)""", """$1url($2__local__/$3$4)""")
      // e.g. url("/img/foo.gif")
      .replaceAll("""([\s:])url\((["'])/([^'"]+)(["'])\)""", "$1url($2" + pathFromLocalRoot + "/$3$4)")
    if (replaceNoDomainOnly) {
      replacedNoDomainOnly
    } else {
      replacedNoDomainOnly
        // e.g. url(http://example.com/img/foo.gif)
        .replaceAll("""([\s:])url\((https?)://([^\)]+)\)""", "$1url(" + pathFromLocalRoot + "/$2__$3)")
        // e.g. url("http://example.com/img/foo.gif")
        .replaceAll("""([\s:])url\((["'])(https?)://([^'"]+)(["'])\)""", "$1url($2" + pathFromLocalRoot + "/$3__$4$5)")
    }
  }

  def writeFile(path: String, content: Array[Byte]): Unit = {
    using(new FileOutputStream(path)) {
      _.write(content, 0, content.size)
    }
  }

}

case class Downloader(target: GyotakuTarget, output: String, currentUrl: String) {

  import Main._

  val protocol = currentUrl.split("/").head.replaceFirst(":", "")

  val outputBaseDir = output + "/" + target.name
  val outputOriginalBaseDir = outputBaseDir + "/original"

  mkdir(outputBaseDir)
  mkdir(outputOriginalBaseDir)

  def download(src: String): Unit = {
    print(".")
    val normalizedPath = normalizeUrl(src)
    try {
      using(new java.net.URL(normalizedPath).openStream()) {
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
      using(new java.net.URL(normalizedPath).openStream()) {
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

case class WebDriverInJSONConfig(
  path: Option[String],
  source: Option[String])

case class JSONConfig(
  name: String,
  url: String,
  charset: Option[String],
  driver: Option[WebDriverInJSONConfig],
  prettify: Option[Boolean],
  replaceNoDomainOnly: Option[Boolean],
  debug: Option[Boolean])

case class GyotakuTarget(
  name: String,
  driver: WebDriver,
  charset: String,
  url: String,
  prettify: Boolean,
  replaceNoDomainOnly: Boolean,
  debug: Boolean)

