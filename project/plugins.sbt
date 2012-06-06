libraryDependencies += "com.github.siasia" %% "xsbt-proguard-plugin" % "0.11.2-0.1.1"

//addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.0")
libraryDependencies += Defaults.sbtPluginExtra("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.0", "0.11.2", "2.9.1")

//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")
libraryDependencies += Defaults.sbtPluginExtra("com.github.mpeltonen" % "sbt-idea" % "1.0.0", "0.11.2", "2.9.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.6.0")


