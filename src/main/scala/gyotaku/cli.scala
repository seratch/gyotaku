package gyotaku

object CommandLine {

  def gyotaku(input: String, output: String) = main(Array(input, output))

  def main(args: Array[String]): Unit = {
    if (args.size != 2) {
      println("""usage: gytaku [input_dir/input_file] [output_dir]""")
      System.exit(0)
    }
    val input = args(0)
    val output = args(1)
    Config.findAll(input).foreach {
      config => Executor.execute(config, output)
    }
  }

}

