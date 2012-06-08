package gyotaku

import swing._
import swing.event._
import swing.GridBagPanel.Fill
import org.yaml.snakeyaml.Yaml
import java.awt.{ Color, Insets }
import com.sun.java.swing.plaf.windows.WindowsIconFactory

object SwingApplication extends SimpleSwingApplication {

  import Utils._

  val windowName = "Gyotaku - 魚拓"

  lazy val ui = new GridBagPanel {

    opaque = true
    background = Color.WHITE

    val c = new Constraints

    def initialize(): Unit = {
      val shouldFill = true
      if (shouldFill) c.fill = Fill.Horizontal
      c.insets = new Insets(5, 5, 5, 5)
    }

    def movePosition(weightx: Double, weighty: Double, gridx: Int, gridy: Int): Unit = {
      c.weightx = weightx
      c.weighty = weighty
      c.gridx = gridx
      c.gridy = gridy
    }

    def moveToLabelPosition(gridy: Int) = movePosition(0, 0, 0, gridy)

    def moveToInputPosition(gridy: Int) = movePosition(1.0, 0, 1, gridy)

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
              prettifyHtmlInput.selected = c.prettify.getOrElse(false)
              replaceNoDomainOnlyInput.selected = c.replaceNoDomainOnly.getOrElse(false)
          }
        case _ =>
      }
    }

    val savedFilesComboBox = new ComboBox[String]("" :: configs.map(_.filepath).toList) {
      InputsSelectionListener.listenTo(selection)
    }

    moveToLabelPosition(0)
    layout(new Label("Load file")) = c
    movePosition(0, 0, 1, 0)
    layout(savedFilesComboBox) = c

    val nameInput = new TextField
    moveToLabelPosition(1)
    layout(new Label("Name")) = c
    moveToInputPosition(1)
    layout(nameInput) = c

    val urlInput = new TextField
    moveToLabelPosition(2)
    layout(new Label("URL")) = c
    moveToInputPosition(2)
    layout(urlInput) = c

    val charsetInput = new TextField
    moveToLabelPosition(3)
    layout(new Label("Charset")) = c
    moveToInputPosition(3)
    layout(charsetInput) = c

    val driverPathInput = new TextField
    moveToLabelPosition(4)
    layout(new Label("Driver")) = c
    moveToInputPosition(4)
    layout(driverPathInput) = c

    val prettifyHtmlInput = new CheckBox("Prettify HTML")
    moveToLabelPosition(5)
    layout(new Label("Formatter")) = c
    moveToInputPosition(5)
    layout(prettifyHtmlInput) = c

    val replaceNoDomainOnlyInput = new CheckBox("Replace absolute/relative path only")
    moveToLabelPosition(6)
    layout(new Label("Replacement")) = c
    moveToInputPosition(6)
    layout(replaceNoDomainOnlyInput) = c

    val outputDirInput = new TextField("output")
    moveToLabelPosition(7)
    layout(new Label("Output")) = c
    moveToInputPosition(7)
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
            d.put("prettify", prettifyHtmlInput.selected)
            d.put("replaceNoDomainOnly", replaceNoDomainOnlyInput.selected)
            val filename = "input/" + d.get("name") + ".yml"
            writeFile(filename, new Yaml().dump(d).getBytes)
          } catch {
            case e =>
              Dialog.showMessage(
                title = windowName,
                message = "Failed to save (" + e.getMessage + ")",
                icon = WindowsIconFactory.createFrameIconifyIcon())
          }
      }
    }
    moveToLabelPosition(8)
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
              prettify = Option(prettifyHtmlInput.selected),
              replaceNoDomainOnly = Option(replaceNoDomainOnlyInput.selected),
              debug = Option(false)
            )
            val answer = Dialog.showConfirmation(
              title = windowName,
              message = "Are you ready to execute?",
              icon = WindowsIconFactory.createFrameIconifyIcon())
            if (answer == Dialog.Result.Ok) {
              Executor.execute(config, outputDirInput.text)
              Dialog.showMessage(
                title = windowName,
                message = "Done. Check " + outputDirInput.text + "/" + nameInput.text + ".",
                icon = WindowsIconFactory.createFrameIconifyIcon())
            }
          } catch {
            case e =>
              Dialog.showMessage(
                title = windowName,
                message = "Failed to execute (" + e.getMessage + ")",
                icon = WindowsIconFactory.createFrameIconifyIcon())
          }
      }
    }
    moveToInputPosition(8)
    layout(executeButton) = c

  }

  def top = new MainFrame {
    title = windowName
    contents = ui
    size = new Dimension(400, 400)
  }

}
