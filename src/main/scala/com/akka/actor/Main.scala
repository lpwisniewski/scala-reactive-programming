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
  def exercise1(current: Int = 0, last: Int = 1): Behavior[PrintNextFibonacci] =
    Behaviors.receive { (context, input) =>
      input.reply ! current
      exercise1(current + last, current)
    }

  case class ShowRandomNumbers(i: Long, reply: ActorRef[Int])

  /** Create an actors that starts i actors in parallel, each of them should
    * send to [[reply]] a random number
    */
  def exercise2(): Behavior[ShowRandomNumbers] = Behaviors.receive {
    (context, input) =>
      for (i <- 1L to input.i) {
        val ref = context.spawn(random, s"random-$i")
        ref ! RandomNumber(input.reply)
        ref ! Kill()
      }
      Behaviors.same
  }

  trait RandomProtocol
  case class RandomNumber(reply: ActorRef[Int]) extends RandomProtocol
  case class Kill() extends RandomProtocol
  def random: Behavior[RandomProtocol] = Behaviors.receive { (context, input) =>
    input match {
      case RandomNumber(reply) =>
        reply ! Random.nextInt()
        Behaviors.same
      case Kill() => Behaviors.stopped
    }
  }

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

  def exercise3[A, B](): Behavior[ParProtocol[A, B]] = Behaviors.receive {
    (context, input) =>
      input match {
        case ParMapQuery(list, operation, returnRef) =>
          list.foreach { item =>
            val processActor =
              context.spawn(process[A, B], s"process-${Random.nextInt}")
            processActor ! Query(item, operation, context.self)
          }
          parMapBehavior(returnRef, List.empty, list.size)
        case Result(_) =>
          Behaviors.same
      }
  }

  def parMapBehavior[A, B](
      returnActor: ActorRef[List[B]],
      pendingResult: List[B],
      listSize: Int
  ): Behavior[ParProtocol[A, B]] = Behaviors.receive { (context, input) =>
    input match {
      case ParMapQuery(_, _, _) =>
        Behaviors.same
      case Result(result) =>
        val aggregatedResult = pendingResult :+ result
        if (aggregatedResult.size == listSize) {
          returnActor ! aggregatedResult
          Behaviors.stopped
        } else
          parMapBehavior(returnActor, aggregatedResult, listSize)
    }
  }

  case class Query[A, B](a: A, operation: A => B, reply: ActorRef[Result[A, B]])
  def process[A, B]: Behavior[Query[A, B]] = Behaviors.receive {
    (context, input) =>
      input.reply ! Result(input.operation(input.a))
      Behaviors.stopped
  }

  // Once you've implemented the exercice3, you now have access to list.parMap
  // operator thanks the implicit class ImprovedList. Congrats

  /** Implement an actor that will answer to these operations. Each operation
    * needs to be handled by a specific actor (AddActor and MultiplyActor for
    * example)
    */
  trait Calculus
  case class Add(a: Int, b: Int, ref: ActorRef[Int]) extends Calculus
  case class Multiply(a: Int, b: Int, ref: ActorRef[Int]) extends Calculus
  def exercise4(): Behavior[Calculus] = Behaviors.setup { context =>
    val addActor = context.spawn(addBehavior, "add")
    val multiplyActor = context.spawn(multiplyBehavior, "mult")
    Behaviors.receive { (context, input) =>
      input match {
        case a: Add      => addActor ! a
        case m: Multiply => multiplyActor ! m
      }
      Behaviors.same
    }
  }

  def addBehavior: Behavior[Add] = Behaviors.receive { (context, input) =>
    input.ref ! input.a + input.b
    Behaviors.same
  }

  def multiplyBehavior: Behavior[Multiply] = Behaviors.receive {
    (context, input) =>
      input.ref ! input.a * input.b
      Behaviors.same
  }

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
