package examples

import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.scalatest.wordspec.AnyWordSpec

class TestMapOperator extends AnyWordSpec {
  "A MapOperator" should {
    "map an observable" in {
      val upstream = Observable(1,2,3)
      val downstream = new MapOperator[Int,Int](upstream, _ * 2)
      val result = downstream.toListL.runSyncUnsafe()
      assert(result == List(2,4,6))
    }
  }
}
