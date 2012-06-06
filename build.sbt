seq(ProguardPlugin.proguardSettings :_*)

proguardOptions ++= Seq (
    "-dontshrink -dontoptimize -dontobfuscate -dontpreverify -dontnote " +
    "-ignorewarnings",
    keepAllScala
)

initialCommands := """|import gyotaku._
                      |import gyotaku.Main._
                      |import org.htmlcleaner._
                      |import org.openqa.selenium._
                      |""".stripMargin

seq(scalariformSettings: _*)

seq(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)


