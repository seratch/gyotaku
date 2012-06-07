package gyotaku

import util.control.Exception._
import java.io.{ FileOutputStream, File, InputStream }

object Utils {

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