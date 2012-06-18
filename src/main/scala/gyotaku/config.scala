package gyotaku

import org.apache.commons.io.FileUtils
import java.io.{ FileInputStream, File }

case class WebDriverInConfig(
  path: Option[String],
  source: Option[String])

case class Config(
  filepath: String = null,
  name: String,
  url: String,
  charset: Option[String],
  driver: Option[WebDriverInConfig],
  prettify: Option[Boolean],
  replaceNoDomainOnly: Option[Boolean],
  debug: Option[Boolean])

object Config {

  import Utils._

  def findAll(input: String): Seq[Config] = {
    import scala.collection.JavaConverters._
    val pathToFindInput = new File(input)
    val configs: Seq[Config] = {
      if (!pathToFindInput.isDirectory && (input.endsWith("yml") || input.endsWith("yaml"))) Seq(pathToFindInput)
      else FileUtils.listFiles(new File(input), Array("yml", "yaml"), true).asScala.toSeq
    }.map {
      yamlFile =>
        val yaml: String = using(new FileInputStream(yamlFile)) {
          stream => new String(readAsByteArray(stream), "UTF-8")
        }
        import org.yaml.snakeyaml.Yaml
        type juMap[K, V] = java.util.Map[K, V]
        val c = new Yaml().load(yaml).asInstanceOf[juMap[String, Any]]
        val driver = Option(c.get("driver").asInstanceOf[juMap[String, String]])
          .getOrElse(new java.util.HashMap[String, String])
        def opt[A](v: A): Option[A] = Option(v)
        def str(v: Any): String = v match {
          case null => null
          case v => v.toString
        }
        def bool(v: Any, default: Boolean): Boolean = v match {
          case null => default
          case _ => java.lang.Boolean.parseBoolean(v.toString)
        }
        Config(
          filepath = yamlFile.getPath,
          name = c.get("name").toString,
          url = c.get("url").toString,
          charset = opt(str(c.get("charset"))),
          driver = opt(WebDriverInConfig(
            path = opt(driver.get("path")),
            source = opt(driver.get("source"))
          )),
          prettify = opt(bool(c.get("prettify"), false)),
          replaceNoDomainOnly = opt(bool(c.get("replaceNoDomainOnly"), true)),
          debug = opt(bool(c.get("debug"), false))
        )
    }
    configs
  }

}