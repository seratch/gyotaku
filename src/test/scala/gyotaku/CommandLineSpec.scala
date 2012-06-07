package gyotaku

import org.scalatest._
import org.scalatest.matchers._

class CommandLineSpec extends FlatSpec with ShouldMatchers {

  behavior of "CommandLine"

  it should "work" in {
    CommandLine.main(Array("input", "output"))
  }

}

// vim: set ts=4 sw=4 et:
