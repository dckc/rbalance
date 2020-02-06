package coop.rchain.rbalanceweb
import coop.rchain.rbalance.txns._

import cats.effect._
import cats.implicits._
import coop.rchain.rbalance.txns.RHOCTxnGraphClosure.{BarcelonaClique, PithiaClique}

object Main extends IOApp {
  def run(args: List[String]) = {
    
    RHOCTxnGraphClosure.BarcelonaClique.reportAdjustments()
    RHOCTxnGraphClosure.PithiaClique.reportAdjustments()

    IO{ ExitCode.Success }
  }
}
