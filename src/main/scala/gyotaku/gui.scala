package gyotaku

import swing._
import swing.event._
import java.awt.Insets
import swing.GridBagPanel.Fill
import org.yaml.snakeyaml.Yaml

object SwingApplication extends SimpleSwingApplication {

  import Utils._

  val windowName = "gyotaku - 魚拓"

  lazy val ui = new GridBagPanel {

    val c = new Constraints

    def initialize(): Unit = {
      val shouldFill = true
      if (shouldFill) c.fill = Fill.Horizontal
      c.insets = new Insets(2, 2, 2, 2)
    }

    def movePosition(weightx: Double, weighty: Double, gridx: Int, gridy: Int): Unit = {
      c.weightx = weightx
      c.weighty = weighty
      c.gridx = gridx
      c.gridy = gridy
    }

    initialize()

    val configs = Config.findAll("input")

    object InputsSelectionListener extends Publisher {
      reactions += {
        case SelectionChanged(source) =>
          val combo = source.asInstanceOf[ComboBox[String]]
          val filename = combo.selection.item
          configs.find(c => c.filepath == filename) map {
            c =>
              nameInput.text = c.name
              urlInput.text = c.url
              charsetInput.text = c.charset.getOrElse("UTF-8")
              driverPathInput.text = c.driver.map(d => d.path.getOrElse(null)).getOrElse(null)
          }
        case _ =>
      }
    }

    val savedFilesComboBox = new ComboBox[String]("" :: configs.map(_.filepath).toList) {
      InputsSelectionListener.listenTo(selection)
    }

    movePosition(0, 0, 0, 0)
    layout(new Label("Saved files")) = c
    movePosition(0, 0, 1, 0)
    layout(savedFilesComboBox) = c

    val nameInput = new TextField
    movePosition(0, 0, 0, 1)
    layout(new Label("Name")) = c
    movePosition(1.0, 0, 1, 1)
    layout(nameInput) = c

    val urlInput = new TextField
    movePosition(0, 0, 0, 2)
    layout(new Label("URL")) = c
    movePosition(1.0, 0, 1, 2)
    layout(urlInput) = c

    val charsetInput = new TextField
    movePosition(0, 0, 0, 3)
    layout(new Label("Charset")) = c
    movePosition(1.0, 0, 1, 3)
    layout(charsetInput) = c

    val driverPathInput = new TextField
    movePosition(0, 0, 0, 4)
    layout(new Label("Web Driver")) = c
    movePosition(1.0, 0, 1, 4)
    layout(driverPathInput) = c

    val outputDirInput = new TextField("output")
    movePosition(0, 0, 0, 5)
    layout(new Label("Output directory")) = c
    movePosition(1.0, 0, 1, 5)
    layout(outputDirInput) = c

    val saveButton = new Button("Save") {
      reactions += {
        case ButtonClicked(source) =>
          try {
            val d = new java.util.HashMap[String, Any]
            d.put("name", nameInput.text)
            d.put("url", urlInput.text)
            d.put("charset", charsetInput.text)
            Option({
              val value = driverPathInput.text
              if (value == null || value.trim.length == 0) null
              else value
            }).foreach {
              path =>
                val driver = new java.util.HashMap[String, String]
                driver.put("path", path)
                d.put("driver", driver)
            }
            val filename = "input/" + d.get("name") + ".yml"
            writeFile(filename, new Yaml().dump(d).getBytes)
            Dialog.showMessage(title = windowName, message = "Done.")
          } catch {
            case e =>
              Dialog.showMessage(title = windowName, message = "Failed to execute! (" + e.getMessage + ")")
          }
      }
    }
    movePosition(0, 0, 0, 6)
    layout(saveButton) = c

    val executeButton = new Button("Execute") {
      reactions += {
        case ButtonClicked(source) =>
          try {
            val config = Config(
              name = nameInput.text,
              url = urlInput.text,
              charset = Option(charsetInput.text),
              driver = Option(WebDriverInConfig(
                path = Option({
                  val value = driverPathInput.text
                  if (value == null || value.trim.length == 0) null
                  else value
                }),
                source = None
              )),
              prettify = Option(false),
              replaceNoDomainOnly = Option(false),
              debug = Option(false)
            )
            val answer = Dialog.showConfirmation(title = windowName, message = "Are you right to execute?")
            if (answer == Dialog.Result.Ok) {
              Executor.execute(config, outputDirInput.text)
              Dialog.showMessage(title = windowName, message = "Done.")
            }
          } catch {
            case e =>
              Dialog.showMessage(title = windowName, message = "Failed to execute! (" + e.getMessage + ")")
          }
      }
    }
    movePosition(1.0, 0, 1, 6)
    layout(executeButton) = c

  }

  def top = new MainFrame {
    title = windowName
    contents = ui
    size = new Dimension(400, 400)
  }

}
