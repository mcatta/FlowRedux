package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.dsl.internal.ChangeStateAction
import com.freeletics.flowredux.dsl.internal.reducer
import kotlin.test.Test
import kotlin.test.assertEquals

class ReducerTest {

    private sealed class TestState {
        object A : TestState()
        object B : TestState()
    }

    @Test
    fun `run SelfReducibleAction on returning True`() {
        val action =
            ChangeStateAction<TestState, Any>(
                changedState = UnsafeMutateState<TestState, TestState> { TestState.B },
                runReduceOnlyIf = { true }
            )

        val expected = TestState.B
        val actual = reducer(
            TestState.A,
            action
        )

        assertEquals(expected, actual)
    }

    @Test
    fun `do not run SelfReducibleAction on returning False`() {
        val action =
            ChangeStateAction<TestState, Any>(
                changedState = UnsafeMutateState<TestState, TestState> { TestState.B },
                runReduceOnlyIf = { false }
            )

        val expected = TestState.A
        val actual = reducer(
            TestState.A,
            action
        )

        assertEquals(expected, actual)
    }
}
