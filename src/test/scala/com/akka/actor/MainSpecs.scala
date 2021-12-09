//#full-example
package com.akka.actor

import org.scalatest.flatspec.AnyFlatSpec
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import scala.concurrent.duration._
import java.time.Instant
import akka.actor.testkit.typed.scaladsl.TestInbox
import com.akka.actor.Main._
import akka.actor.testkit.typed.Effect
import akka.actor.testkit.typed.scaladsl.ActorTestKit

class MainSpecs extends AnyFlatSpec {

  "exercise1" should "print the first fibonacci number correctly" in {
    val testKit = BehaviorTestKit(exercise1())

    val inbox = TestInbox[Int]()

    testKit.run(
      PrintNextFibonacci(inbox.ref)
    )

    inbox.expectMessage(0)
  }

  "exercise1" should "print the 10 first fibonacci number correctly" in {
    val testKit = BehaviorTestKit(exercise1())

    val inbox = TestInbox[Int]()

    for (i <- 1 to 10) {
      testKit.run(
        PrintNextFibonacci(inbox.ref)
      )
    }

    val messages = inbox.receiveAll()
    messages equals Seq(0, 1, 1, 2, 3, 5, 8, 13, 21, 34)
  }

  "exercise2" should "create nth actors" in {
    val testKit = BehaviorTestKit(exercise2)

    val inbox = TestInbox[Int]()

    testKit.run(
      ShowRandomNumbers(10, inbox.ref)
    )

    val effects = testKit.retrieveAllEffects()
    val spawned = effects.filter { effect =>
      effect match {
        case _: Effect.Spawned[_] => true
        case _                    => false
      }
    }

    spawned.size equals 10
  }

  "exercise3" should "paralellize map operations on string" in {
    val testKit = ActorTestKit()
    val pinger = testKit.spawn(exercise3[String, Int](), "ex")
    val probe = testKit.createTestProbe[List[Int]]()
    pinger ! ParMapQuery(
      List("toto", "pouet", "aa", "b", ""),
      (_.size),
      probe.ref
    )
    probe.receiveMessage.sorted equals List(0, 1, 2, 4, 5)
  }

  "exercise3" should "paralellize map operations on int" in {
    val testKit = ActorTestKit()
    val pinger = testKit.spawn(exercise3[Int, Int](), "ex")
    val probe = testKit.createTestProbe[List[Int]]()
    pinger ! ParMapQuery(List(1, 2, 3, 4, 5), (_ * 2), probe.ref)
    probe.expectMessage(List(2, 4, 6, 8, 10))
  }

  "exercise3" should "mparalellize map operations with parMap" in {
    import ImprovedList._
    List(1, 2, 3).parMap(x => x * 2)(2.seconds).sorted equals List(2, 4, 6)
  }

  "exercise4" should "return the correct results without creating too many actors" in {
    val testKit = ActorTestKit()
    val pinger = testKit.spawn(exercise4(), "ex")
    val probe = testKit.createTestProbe[Int]()
    pinger ! Add(1, 2, probe.ref)
    pinger ! Multiply(1, 2, probe.ref)
    pinger ! Add(100, 2, probe.ref)
    probe.expectMessage(3)
    probe.expectMessage(2)
    probe.expectMessage(102)
  }
}
