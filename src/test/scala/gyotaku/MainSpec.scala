package gyotaku

import org.scalatest._
import org.scalatest.matchers._

class MainSpec extends FlatSpec with ShouldMatchers {

  behavior of "Main"

  it should "work" in {
    Main.main(Array("input", "output"))
  }

}

// vim: set ts=4 sw=4 et:
