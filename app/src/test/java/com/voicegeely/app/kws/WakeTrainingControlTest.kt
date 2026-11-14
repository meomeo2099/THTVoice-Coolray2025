package com.voicegeely.app.kws
import org.junit.Assert.*
import org.junit.Test
class WakeTrainingControlTest {
 @Test fun cancellationRetainsOwnershipUntilWorkerFinishes() {
  val c=WakeTrainingControl(); val token=c.begin()!!
  assertTrue(c.active(token)); c.cancel(); assertFalse(c.active(token))
  assertNull(c.begin()); assertTrue(c.busy)
  c.finish(token); assertFalse(c.busy); assertNotNull(c.begin())
 }
 @Test fun staleWorkerCannotFinishNewSession() {
  val c=WakeTrainingControl(); val first=c.begin()!!; c.finish(first)
  val next=c.begin()!!; c.finish(first); assertTrue(c.active(next))
 }
}