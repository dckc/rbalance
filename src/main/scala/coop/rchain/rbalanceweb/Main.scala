package coop.rchain.rbalanceweb

import cats.effect._
import cats.implicits._
import coop.rchain.rbalance.txns.RHOCTxnGraphClosure.{BarcelonaClique, PithiaClique}

object Main extends IOApp {
  def run(args: List[String]) = {
    PithiaClique.reportAdjustments()
    BarcelonaClique.reportAdjustments()
    IO{ ExitCode.Success }
  }
}
