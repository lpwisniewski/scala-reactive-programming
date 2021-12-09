//#full-example
package com.akka.actor

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Await
import scala.util.Random

object Main {

  case class PrintNextFibonacci(reply: ActorRef[Int])

  /** Create an actor that sends to [[reply]] the next fibonacci number at each
    * new message received. Start with 0
    */
  def exercise1(): Behavior[PrintNextFibonacci] = ???

  case class ShowRandomNumbers(i: Long, reply: ActorRef[Int])

  /** Create an actors that starts i actors in parallel, each of them should
    * send to [[reply]] a random number
    */
  def exercise2(): Behavior[ShowRandomNumbers] = ???

  /** Implement the parMap operator based on actors. You can assume that there
    * is only one [[ParMapQuery]] received.
    */
  trait ParProtocol[A, B]

  /** Message received when a new query arrives
    */
  case class ParMapQuery[A, B](
      list: List[A],
      operation: A => B,
      returnActor: ActorRef[List[B]]
  ) extends ParProtocol[A, B]

  /** Message received when a new part of the result arrives
    */
  case class Result[A, B](result: B) extends ParProtocol[A, B]

  def exercise3[A, B](): Behavior[ParProtocol[A, B]] = ???

  // Once you've implemented the exercice3, you now have access to list.parMap
  // operator thanks the implicit class ImprovedList. Congrats

  /** Implement an actor that will answer to these operations. Each operation
    * needs to be handled by a specific actor (AddActor and MultiplyActor for
    * example)
    */
  trait Calculus
  case class Add(a: Int, b: Int, ref: ActorRef[Int]) extends Calculus
  case class Multiply(a: Int, b: Int, ref: ActorRef[Int]) extends Calculus
  def exercise4(): Behavior[Calculus] = ???

  /** Reimplement the 3rd exercise with a pooling mechanism.
    */
  def bonusExercise1 = ???
}

object ImprovedList {
  implicit class ImprovedList[A](l: List[A]) {
    def parMap[B](operation: A => B)(
        timeout: FiniteDuration
    ): List[B] = {
      implicit val system: ActorSystem[Main.ParMapQuery[A, B]] =
        ActorSystem(Main.exercise3[A, B](), "my-system")
      val scheduler = system.scheduler
      val t: Timeout = timeout

      Await.result(
        system.ask((ref: ActorRef[List[B]]) =>
          Main.ParMapQuery(l, operation, ref)
        )(
          t,
          scheduler
        ),
        timeout
      )
    }
  }
}
