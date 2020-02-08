package coop.rchain.rbalance.txns
import java.io._
import scala.collection.immutable.Set
import scala.collection.immutable.HashSet
import scala.collection.immutable.Map
import scala.collection.immutable.HashMap

//import scala.concurrent.ExecutionContext.global
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent._
import cats.effect._
import io.circe._
//import io.circe.literal._
import org.http4s._
import org.http4s.dsl.io._
import cats.effect._
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.client.blaze._
import org.http4s.client._
import coop.rchain.rbalance.transitive._

trait EdgeT[Node] {
  def src       : Node
  def trgt      : Node
  def weight    : Double
  def timestamp : Int
  def hash      : String
  def blockId   : String

  override def toString() : String = {
    s"$src -$weight-> $trgt"
  }
}

case class Edge[Node](
  override val src           : Node,
  override val trgt          : Node,
  override val weight        : Double,
  override val timestamp     : Int,
  override val hash          : String,
  override val blockId       : String,
  override val justification : Set[Edge[Node]]
) extends EdgeT[Node] with Justified[Edge[Node]]

trait AddressT {
  def addr      : String
  def balance   : List[Double]
}

case class Address( 
  override val addr    : String,
  // Because there are loops in the txn graph coming up with a purely functional solution will take extra thought
  // So, i'm punting in the interest of time
  var balance : List[Double],
) extends AddressT {
  override def equals( a : Any ) = {
    a match {
      case Address( `addr`, _ ) => true
      case _ => false
    }
  }
  override def hashCode = ( addr ).##
}

trait RHOCTxnEdge extends EdgeT[Address] with Justified[RHOCTxnEdge]

case class RHOCTxnIdentity(
//  override val src           : Address,
//  override val trgt          : Address,
  val addr                   : Address,
  override val weight        : Double,
  override val hash          : String,
  override val blockId       : String,
  override val justification : Set[RHOCTxnEdge]
) extends RHOCTxnEdge {
  override def src = addr
  override def trgt = addr
  override def timestamp = 0
}

case class RHOCTxnEdgeRep(
  override val src           : Address,
  override val trgt          : Address,
  override val weight        : Double,
  override val timestamp     : Int,
  override val hash          : String,
  override val blockId       : String,
  override val justification : Set[RHOCTxnEdge],
  var moreJust               : Set[RHOCTxnEdge]
) extends RHOCTxnEdge

object RHOCTxnGraphClosure 
    extends JustifiedClosure[Address, RHOCTxnEdge] with InputCSVData {
  import AdjustmentConstants._

  def sortTxnsByTimestamp( txn1 : RHOCTxnEdge, txn2 : RHOCTxnEdge ) = {
    txn1.timestamp < txn2.timestamp
  }

  def sortChild( folks : List[RHOCTxnEdge], kinder : RHOCTxnEdge, pos : Int ) : Int = {
    folks match {
      case Nil => pos
      case folk :: rFolks => {
        if ( kinder.timestamp <= folk.timestamp ) {
          pos
        }
        else {
          sortChild( rFolks, kinder, pos + 1 )
        }
      }
    }
  }
  def separateChildren( folks : List[RHOCTxnEdge], children : List[RHOCTxnEdge] ) : List[List[RHOCTxnEdge]] = {
    children.foldLeft( folks.map( ( f ) => { List[RHOCTxnEdge]() } ) )(
      ( acc, txn ) => {
        val kIdx = sortChild( folks, txn, 0 )
        val prefix = acc.take( kIdx - 1 )
        acc.drop( kIdx - 1 ) match {
          case fL :: rFl => {
            prefix ++ List( fL ++ List( txn ) ) ++ rFl
          }
          case _ => {
            throw new Exception( s"unexpected timestamp sorting ${kIdx} ${txn}" )
          }
        }
      }
    )
  }

  // Use this initial edge to generate the Barcelona clique
  val barcelonaEdge : RHOCTxnEdge = {
    new RHOCTxnIdentity(
      new Address( barcelonaAddr.toLowerCase, List[Double]( barcelonaTaint ) ),
      barcelonaTaint,
      "scam",
      "scam",
      new HashSet[RHOCTxnEdge]()
    )
  }

  // Use this initial edge to generate the Pithia clique
  val pithiaEdge : RHOCTxnEdge = {
    new RHOCTxnIdentity(
      new Address( pithiaAddr.toLowerCase, List[Double]( pithiaTaint ) ),
      pithiaTaint,
      "scam",
      "scam",
      new HashSet[RHOCTxnEdge]()
    )
  }

  def loadAndFormatWalletData() : Map[String,Double] = {
    loadWalletData().foldLeft( new HashMap[String,Double]() )(
      ( acc, walletArray ) => {
        acc + ( walletArray( 0 ).toLowerCase -> walletArray( 1 ).toDouble )
      }
    )
  }

  var balancesD : Option[Map[String,Double]] = None
  def balances() : Map[String,Double] = {
    balancesD match {
      case None => {
        val balanceMap = loadAndFormatWalletData()
        balancesD = Some( balanceMap )
        balanceMap
      }
      case Some( balanceMap ) => balanceMap
    }
  }
    
  def loadAndFormatTxnData() : List[RHOCTxnEdge] = {
    for( txnArray <- loadTxnData() ) yield {
      RHOCTxnEdgeRep(
        new Address( txnArray(4), List[Double]( 0.0 ) ),
        new Address( txnArray(5), List[Double]( 0.0 ) ),
        txnArray(6).toDouble,
        txnArray(2).toInt,
        txnArray(0),
        txnArray(1),
        new HashSet[RHOCTxnEdge](),
        new HashSet[RHOCTxnEdge]()
      )
    }
  }

  var txnDataV : Option[List[RHOCTxnEdge]] = None
  def txnData() : List[RHOCTxnEdge] = {
    txnDataV match {
      case None => {
        val txnD = barcelonaEdge :: pithiaEdge :: loadAndFormatTxnData()
        txnDataV = Some( txnD )
        txnD
      }
      case Some( txnD ) => txnD
    }
  }

  // We run the transitive closure to generate the tainted
  // clique, i.e. the graph of transactions that communicate taint.
  // During the calculation of the transitive closure we calculate the
  // weight of each edge to be the fraction of the amt of the
  // transaction divided by the sum of all the outgoing transactions.

  def nextRHOCTxnWeightedEdges( txn : RHOCTxnEdge ) : Set[RHOCTxnEdge] = {
    val parents = txnData().filter( ( txnD ) => { txnD.trgt == txn.src } ).sortWith( sortTxnsByTimestamp )
    val progeny = txnData().filter( ( txnD ) => { txnD.src == txn.trgt } ).sortWith( sortTxnsByTimestamp )
    ( parents, progeny ) match {
      case ( _, Nil ) => new HashSet[RHOCTxnEdge]()      
      case ( folks, children ) => {
        val seed : Double = 0
        val childGroups = separateChildren( folks, children )
        childGroups.flatMap(
          ( cG ) => {
            val totalWeight = cG.foldLeft( seed )( ( acc, t ) => { acc + t.weight } )
            cG.map( 
              ( t ) => { 
                RHOCTxnEdgeRep(
                  t.src,
                  t.trgt,
                  ( t.weight / totalWeight ),
                  t.timestamp,
                  t.hash,
                  t.blockId,
                  (new HashSet[RHOCTxnEdge]() + txn),
                  new HashSet[RHOCTxnEdge]()
                ) 
              } 
            )
          }
        ).toSet
      }
    }        
  }    

  override def next = nextRHOCTxnWeightedEdges
  override def key = _.trgt

  def taintedClique( taintMap : Map[Address,Set[_ <: RHOCTxnEdge]] ) : List[RHOCTxnEdge] = {
    taintMap.values.foldLeft( new HashSet[RHOCTxnEdge]() )( ( acc, s ) => { acc ++ s } ).toList
  }

  def getClique( taintedEdge : RHOCTxnEdge ) : List[RHOCTxnEdge] = { taintedClique( close( taintedEdge ) ) }

  // Now, we can rerun the closure just on the clique and calculate
  // the taint at an address as the application of the weight to the
  // "balance" of the target address of the edge. Note that the balance here is
  // just the taint because we begin from the root of the clique where
  // the balance is entirely taint and carry that forward through the
  // calculation of the closure.

  def nextRHOCTxnTaint( clique : List[RHOCTxnEdge] )( txn : RHOCTxnEdge ) : Set[RHOCTxnEdge] = {
    val parents = txnData().filter( ( txnD ) => { (txnD.trgt == txn.trgt) && ( txnD != txn ) } ).sortWith( sortTxnsByTimestamp )
    val progeny = clique.filter( ( txnD ) => { txnD.src == txn.trgt } ).sortWith( sortTxnsByTimestamp )

    ( parents, progeny ) match {
      case ( _, Nil ) => new HashSet[RHOCTxnEdge]()      
      case ( folks, children ) => {
        txn.trgt.balance = List[Double]( txn.weight ) ++ folks.map( _.weight )
        println( s"${txn} has ${folks.size} incoming edges and ${children.size} outgoing edges" )
        println( s"and balance list ${txn.trgt.balance}" )
        val seed : ( Int, Double, Set[RHOCTxnEdge] ) = ( 0, 0.0, new HashSet[RHOCTxnEdge]() )
        val childGroups = separateChildren( folks, children )
        println( s"split as ${childGroups}" )
        childGroups.foldLeft( seed )(
          ( acc, cG ) => {
            val ( idx, carry, rslt ) = acc
            if ( cG.size == 0 ) {
              ( idx + 1, txn.trgt.balance( idx ) + carry , rslt )
            }
            else {
              val cGtxns : Set[RHOCTxnEdge] = cG.map(
                ( t ) => {
                  val trgtTaint : Double = (txn.trgt.balance( idx ) + carry) * t.weight
                  RHOCTxnEdgeRep(
                    t.src,
                    t.trgt,
                    trgtTaint,
                    t.timestamp,
                    t.hash,
                    t.blockId,
                    (new HashSet[RHOCTxnEdge]() + txn),                    
                    (new HashSet[RHOCTxnEdge]() ++ folks.toSet )
                  )
                }
              ).toSet

              ( idx + 1, 0.0, rslt ++ cGtxns )
            }
          }
        )._3
      }
    }
  }

  // def nextRHOCTxnTaint( clique : List[RHOCTxnEdge] )( addr : Address ) : Set[Address] = {
  //   val parents = txnData().filter( ( txnD ) => { txnD.trgt == txn.trgt } ).sortWith( sortTxnsByTimestamp )
  //   val progeny = clique.filter( ( txnD ) => { txnD.src == addr } ).sortWith( sortTxnsByTimestamp )

  //   ( parents, progeny ) match {
  //     case ( _, Nil ) => new HashSet[Address]()      
  //     case ( incomingEdges, outgoingEdges ) => {
  //       addr.balance = incomingEdges.map( ( f ) => { f.weight } )
  //       println( s"${addr.addr} has ${incomingEdges.size} incoming edges and ${outgoingEdges.size} outgoing edges" )
  //       println( s"and balance list ${addr.balance}" )
  //       val seed : ( Int, Double, Set[Address] ) = ( 0, 0.0, new HashSet[Address]() )
  //       val childGroups = separateOutgoingEdges( incomingEdges, outgoingEdges )
  //       println( s"split as ${childGroups}" )
  //       childGroups.foldLeft( seed )(
  //         ( acc, cG ) => {
  //           val ( idx, carry, rslt ) = acc
  //           if ( cG.size == 0 ) {
  //             ( idx + 1, txn.trgt.balance( idx ) + carry , rslt )
  //           }
  //           else {
  //             val cGtxns : Set[Address] = cG.map(
  //               ( t ) => {
  //                 val trgtTaint : Double = (addr.balance( idx ) + carry) * t.weight
  //                 Address( t.trgt, List[Double]( trgtTaint ) )
  //               }
  //             ).toSet

  //             ( idx + 1, 0.0, rslt ++ cGtxns )
  //           }
  //         }
  //       )._3
  //     }
  //   }
  // }

  def adjustmentsMap( adjustments : List[RHOCTxnEdge] ) : Map[String,( Double, Double, Set[List[RHOCTxnEdge]] )] = {
    val empty : Set[List[RHOCTxnEdge]] = new HashSet[List[RHOCTxnEdge]]()
    val seed : Map[String,( Double, Double, Set[List[RHOCTxnEdge]] )] = new HashMap[String,( Double, Double, Set[List[RHOCTxnEdge]] )]()
    balances().foldLeft( seed )(
      ( acc, entry ) => {
        val ( addr, balance ) = entry
        val addrCreditAdj : List[RHOCTxnEdge] = adjustments.filter( ( e ) => e.trgt.addr == addr ).sortWith( sortTxnsByTimestamp )
        val addrDebitAdj : List[RHOCTxnEdge] = adjustments.filter( ( e ) => e.src.addr == addr ).sortWith( sortTxnsByTimestamp )
        ( addrCreditAdj, addrDebitAdj ) match {
          case ( Nil, _ ) => {
            println( s"$addr not in clique" )
            val adj = ( balance, 0.toDouble, empty )
            acc + ( addr -> adj )
          }
          case ( inEdges, outEdges ) => {            
            val childGroups : List[List[RHOCTxnEdge]] = separateChildren( inEdges, outEdges )

            val seed : ( Int, Double, Set[List[RHOCTxnEdge]] )= ( 0, 0.0, new HashSet[List[RHOCTxnEdge]]() )
            val ( idx, totalAdjustment, proofs ) =
              childGroups.foldLeft( seed )(
                ( acc, cG ) => {
                  val ( idx, adjustment, pfs ) = acc
                  val groupParent = inEdges( idx )
                  val credit = groupParent.weight
                  val debit = cG.foldLeft( 0.asInstanceOf[Double] )( ( dAcc, t ) => { dAcc + t.weight } )
                  val nPfs : Set[List[RHOCTxnEdge]] = groupParent.paths().asInstanceOf[Set[List[RHOCTxnEdge]]]

                  ( idx + 1, adjustment + ( credit - debit ), pfs ++ nPfs )
                }
              )

            println( s"$addr in clique with" )
            
            val balAdj = ( balance, totalAdjustment, proofs )
            acc + ( addr -> balAdj )
          }
        }
      }
    )
  }

  def reportAdjustmentsMap(
    adjustmentsMap : Map[String,( Double, Double, Set[List[RHOCTxnEdge]] )],
    adjFileName : String, proofFileName : String,
    dir : String
  ) : Unit = {
    val adjFName          = s"${dir}/${adjFileName}"
    val pfFName           = s"${dir}/${proofFileName}"

    // collect stats
    val numberOfAdjustments =
      adjustmentsMap.foldLeft( 0 )(
      ( acc, entry ) => {
        val ( k, ( balance, adjustment, pf ) ) = entry
        if ( adjustment != 0 ) {
          acc + 1
        }
        else acc
      }
    )
    val nonDust =
      adjustmentsMap.foldLeft( ( 0, 0.toDouble, List[(String,Double)]() ) )(
        ( acc, entry ) => {
          val ( k, ( balance, adjustment, pf ) ) = entry
          if ( adjustment > 1 ) {
            val ( count, total, addrs ) = acc
            val biglyHit = ( k, balance )
            ( count + 1, total + adjustment, addrs ++ List[(String,Double)](biglyHit) )
          }
          else acc
        }
      )
    val adjZeros =
      adjustmentsMap.foldLeft( ( 0, 0.toDouble, List[(String,Double)]() ) )(
      ( acc, entry ) => {
        val ( k, ( balance, adjustment, pf ) ) = entry
        if ( ( balance == 0 ) && ( adjustment > 0.000000001 ) ) {
          val ( count, total, addrs ) = acc
          val oddlyHit = ( k, adjustment )
          ( count + 1, total + adjustment, addrs ++ List[(String,Double)]( oddlyHit ) )
        }
        else acc
      }
    )
    

    println( s"RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR" )
    println( "\n\n" )
    println( s"Total number of adjustments ${numberOfAdjustments}" )
    println( s"Out of ${adjustmentsMap.size}\n\n" )
    println( s"Total adjustments that are not dust ${nonDust._1} adding to ${nonDust._2}" )
    for( addr <- nonDust._3 ) { println( s"${addr._1}" ) }
    println( "\n\n" )
    println( s"Total adjustments on addresses with zero balance ${adjZeros._1} adding to ${adjZeros._2}" )
    for( addr <- adjZeros._3 ) { println( s"${addr}" ) }
    println( "\n\n" )
    println( s"RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR" )

    // proofs were being written to adjustment file... lol!
    val adjustmentsFile   = new File( adjFName )
    val adjustmentsWriter = new BufferedWriter( new FileWriter( adjustmentsFile ) )
    
    for( ( k, v ) <- adjustmentsMap ) {
      val ( balance, adjustment, proof ) = v
      if ( adjustment > 0.00000001 ) { // RHOC precision
        println( s"${k} -> ${adjustment}" )
        adjustmentsWriter.write( s"$k, ${balance}, ${adjustment}\n" )        
      }      
    }

    adjustmentsWriter.flush()
    adjustmentsWriter.close()

    val proofFile         = new File( pfFName )
    val proofWriter       = new BufferedWriter( new FileWriter( proofFile ) )
    for( ( k, v ) <- adjustmentsMap ) {
      val ( balance, adjustment, proof ) = v
      if ( adjustment > 0.00000001 ) { // RHOC precision
        println( s"${k} -> ${adjustment}" )
        proofWriter.write( s"$k, ${proof}\n" )
      }      
    }

    proofWriter.flush()
    proofWriter.close()
  }

  def reportAdjustmentsMap( adjustmentsMap : Map[String,( Double, Double, Set[List[RHOCTxnEdge]] )] ) : Unit = {
    reportAdjustmentsMap( adjustmentsMap, adjustmentsFile, proofFile, reportingDir )
  }

  def annotateFileName( fNameStr : String, annotation : String ) = {
    val fNameComponents = fNameStr.split( '.' )
    val fName = fNameComponents( 0 )
    val fExt = fNameComponents( 1 )
    s"${fName}${annotation}.${fExt}"
  }

  val BarcelonaWeights = getClique( barcelonaEdge )
  val PithiaWeights = getClique( pithiaEdge )

  def reportClique( clique : List[RHOCTxnEdge] ) : Unit = {
    val cliqueFile = new File( s"clique.csv" )
    val cliqueWriter = new BufferedWriter( new FileWriter( cliqueFile ) )
    for( txn <- clique ) {
      cliqueWriter.write( s"$txn\n" )
    }
    cliqueWriter.flush()
    cliqueWriter.close()
  }

  // Exploration
  def findTxns( clique : List[RHOCTxnEdge] )( addr : String ) : List[RHOCTxnEdge] = {
    clique.filter( ( txn ) => { ( txn.src == addr ) || ( txn.trgt == addr ) } )
  }

  def findTxn( clique : List[RHOCTxnEdge] )( hash : String ) : Option[RHOCTxnEdge] = {
    clique.filter( ( txn ) => { txn.hash == hash } ) match {
      case Nil => None
      case txn :: Nil => Some( txn )
      case txns => throw new Exception( s"more than one transaction with the same hash: $hash \n txns: $txns" )
    }
  }

  def findTxnProof( clique : List[RHOCTxnEdge] )( hash : String ) : Set[List[RHOCTxnEdge]] = {
    findTxn( clique )( hash ) match {
      case None => new HashSet[List[RHOCTxnEdge]]
      case Some( txn ) => txn.paths().asInstanceOf[Set[List[RHOCTxnEdge]]]
    }
  }

  object BarcelonaClique extends JustifiedClosure[Address, RHOCTxnEdge] {
    override def next = nextRHOCTxnTaint( BarcelonaWeights )
    override def key = _.trgt

    def getClique( taintedEdge : RHOCTxnEdge ) : List[RHOCTxnEdge] = { taintedClique( close( taintedEdge ) ) }

    val BarcelonaAdjustments = getClique( barcelonaEdge )
    val BarcelonaMap = adjustmentsMap( BarcelonaAdjustments )

    def reportAdjustments( ) : Unit = {
      val adjFName = annotateFileName( AdjustmentConstants.adjustmentsFile, "Barcelona" )
      val pfFName = annotateFileName( AdjustmentConstants.proofFile, "Barcelona" )

      reportAdjustmentsMap(
        BarcelonaMap,
        adjFName,
        pfFName,
        reportingDir
      )
    }
  }  

  object PithiaClique extends JustifiedClosure[Address, RHOCTxnEdge] {
    override def next = nextRHOCTxnTaint( PithiaWeights )
    override def key = _.trgt

    def getClique( taintedEdge : RHOCTxnEdge ) : List[RHOCTxnEdge] = { taintedClique( close( taintedEdge ) ) }

    val PithiaAdjustments = getClique( pithiaEdge )
    val PithiaMap = adjustmentsMap( PithiaAdjustments )

    def reportAdjustments( ) : Unit = {
      val adjFName = annotateFileName( adjustmentsFile, "Pithia" )
      val pfFName = annotateFileName( proofFile, "Pithia" )
      reportAdjustmentsMap(
        PithiaMap,
        adjFName,
        pfFName,
        reportingDir
      )
    }
  }

  object NaiveCalculation {
    import scala.collection.mutable.Map
    import scala.collection.mutable.HashMap

    def cup( addr : Address ) : List[RHOCTxnEdge] = {
      txnData().filter( ( txnD ) => { txnD.trgt == addr.addr } ).sortWith( sortTxnsByTimestamp )
    }
    def cap( addr : Address ) : List[RHOCTxnEdge] = {
      txnData().filter( ( txnD ) => { txnD.src == addr.addr } ).sortWith( sortTxnsByTimestamp )
    }
    def cupNCap( addr : Address ) : ( List[RHOCTxnEdge], List[RHOCTxnEdge] ) = { ( cup( addr ), cap( addr ) ) }
    def weight( txn : RHOCTxnEdge, memo : Map[RHOCTxnEdge,Double] ) : Double = {
      memo.get( txn ) match {
        case None => {
          val w : Double = 
            txn match {
              case idTxn : RHOCTxnIdentity => idTxn.weight
              case tRep@RHOCTxnEdgeRep( s, _, tw, _, _, _, _, _ ) => {
                cap( s ) match {
                  case Nil => throw new Exception( s"impossible cap: ${txn}" )
                  case t :: Nil => 1.0
                  case rTs => {
                    tw / rTs.foldLeft( 0.0 )( ( acc, t ) => { acc + t.weight } )
                  }
                }
              }
            }
          memo + ( txn -> w )
          w
        }
        case Some( w ) => w
      }
    }
    def taintOut( 
      txn1 : RHOCTxnEdge, txn2 : RHOCTxnEdge, 
      cap : List[RHOCTxnEdge],
      edgeMemo : Map[RHOCTxnEdge,Double],
      addrMemo : Map[Address,Double]
    ) : Double = {
      cap.filter( 
        ( txn ) => { ( txn1.timestamp <= txn.timestamp ) && ( txn.timestamp <= txn2.timestamp ) } 
      ).foldLeft( 0.0 )( ( acc, u ) => acc + taint( u, edgeMemo, addrMemo ) )
    }
    def taint( txn : RHOCTxnEdge, edgeMemo : Map[RHOCTxnEdge,Double], addrMemo : Map[Address,Double] ) : Double = {
      edgeMemo.get( txn ) match {
        case None => {
          val t = weight( txn, edgeMemo ) * taint( txn.src, edgeMemo, addrMemo )
          edgeMemo + ( txn -> t )
          t
        }
        case Some( t ) => t
      }
    }
    def taint( addr : Address, edgeMemo : Map[RHOCTxnEdge,Double], addrMemo : Map[Address,Double] ) : Double = {
      addrMemo.get( addr ) match {
        case None => {
          val ( cup, cap ) = cupNCap( addr )
          val t = 
            cup.foldLeft( ( 0, 0.0 ) )(
              ( acc, inTxnL ) => {
                val ( idx, tnt ) = acc
                val inTxnR = cup( idx + 1 )
                val cTnt = taint( inTxnL, edgeMemo, addrMemo ) - taintOut( inTxnL, inTxnR, cap, edgeMemo, addrMemo )

                ( idx + 1, tnt + cTnt )
              }
            )
          addrMemo + ( addr -> t._1 )
          t._1
        }
        case Some( t ) => t
      }
    }

    def reportAdjustments( ) : Unit = {
      val adjDir = AdjustmentConstants.reportingDir
      val adjFileName = annotateFileName( AdjustmentConstants.adjustmentsFile, "Combined" )
      //      val pfFileName = annotateFileName( AdjustmentConstants.proofFile, "Combined" )
      val adjFName          = s"${adjDir}/${adjFileName}"
      //      val pfFName           = s"${adjDir}/${proofFileName}"

      val adjustmentsFile   = new File( adjFName )
      val adjustmentsWriter = new BufferedWriter( new FileWriter( adjustmentsFile ) )

      val edgeMemo : Map[RHOCTxnEdge,Double] = new HashMap[RHOCTxnEdge,Double]()
      val addrMemo : Map[Address,Double] = new HashMap[Address,Double]()

      for( ( addr, balance ) <- balances() ) {
        val address = new Address( addr, List[Double]( 0.0 ) )
        val adjustment = taint( address, edgeMemo, addrMemo )
        if ( adjustment > 0.00000001 ) { // RHOC precision
          println( s"${addr} -> ${adjustment}" )
          adjustmentsWriter.write( s"${addr}, ${balance}, ${adjustment}\n" )
        }
      }
      adjustmentsWriter.flush()
      adjustmentsWriter.close()
    }
  }
}
