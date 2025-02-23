package com.freeletics.flowredux.dsl

import app.cash.turbine.awaitComplete
import app.cash.turbine.awaitItem
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class OnActionEffectTest {

    @Test
    fun `action effect block stops when moved to another state`() = runTest {
        val signal = Channel<Unit>()
        val blockEntered = Channel<Boolean>(Channel.UNLIMITED)

        var reached = false
        val sm = StateMachine {
            inState<TestState.Initial> {
                onActionEffect<TestAction.A1> { _, _ ->
                    blockEntered.send(true)
                    signal.awaitComplete()
                    // this should never be reached because state transition did happen in the meantime,
                    // therefore this whole block must be canceled
                    reached = true
                }

                on<TestAction.A2> { _, state ->
                    state.override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertTrue(blockEntered.awaitItem())
            sm.dispatchAsync(TestAction.A2)
            assertEquals(TestState.S2, awaitItem())
            signal.close()
        }

        assertFalse(reached)
    }

    @Test
    fun `on action effect is triggered`() = runTest {
        val signal = Channel<Unit>()
        val triggered = Channel<Boolean>(Channel.UNLIMITED)

        val sm = StateMachine {
            inState<TestState.Initial> {
                onActionEffect<TestAction.A1> { _, _ ->
                    triggered.send(true)
                }

                on<TestAction.A1> { _, state ->
                    signal.awaitComplete()
                    state.override { TestState.S2 }
                }
            }
        }

        sm.state.test {
            assertEquals(TestState.Initial, awaitItem())
            sm.dispatchAsync(TestAction.A1)
            assertTrue(triggered.awaitItem())
            signal.close()
            assertEquals(TestState.S2, awaitItem())
        }
    }
}
