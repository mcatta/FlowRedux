package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.dsl.internal.ExternalWrappedAction
import com.freeletics.flowredux.dsl.internal.InitialStateAction
import com.freeletics.flowredux.dsl.internal.reducer
import com.freeletics.flowredux.dsl.util.AtomicCounter
import com.freeletics.flowredux.reduxStore
import com.freeletics.mad.statemachine.StateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow

@FlowPreview
@ExperimentalCoroutinesApi
public abstract class FlowReduxStateMachine<S : Any, A : Any>(
    private val initialStateSupplier: () -> S,
) : StateMachine<S, A> {

    private val inputActions = Channel<A>()
    private lateinit var outputState: Flow<S>

    private val activeFlowCounter = AtomicCounter(0)

    public constructor(initialState: S) : this(initialStateSupplier = { initialState })

    protected fun spec(specBlock: FlowReduxStoreBuilder<S, A>.() -> Unit) {
        if (::outputState.isInitialized) {
            throw IllegalStateException(
                "State machine spec has already been set. " +
                    "It's only allowed to call spec {...} once."
            )
        }

        val sideEffects = FlowReduxStoreBuilder<S, A>().apply(specBlock).generateSideEffects()

        outputState = inputActions
            .receiveAsFlow()
            .map<A, Action<S, A>> { ExternalWrappedAction(it) }
            .onStart {
                emit(InitialStateAction())
            }
            .reduxStore(initialStateSupplier, sideEffects, ::reducer)
            .distinctUntilChanged { old, new -> old === new } // distinct until not the same object reference.
            .onStart {
                if (activeFlowCounter.incrementAndGet() > 1) {
                    throw IllegalStateException(
                        "Can not collect state more than once at the same time. Make sure the" +
                            "previous collection is cancelled before starting a new one. " +
                            "Collecting state in parallel would lead to subtle bugs."
                    )
                }
            }
            .onCompletion {
                activeFlowCounter.decrementAndGet()
            }
    }

    override val state: Flow<S>
        get() {
            checkSpecBlockSet()
            return outputState
        }

    override suspend fun dispatch(action: A) {
        checkSpecBlockSet()
        if (activeFlowCounter.get() <= 0) {
            throw IllegalStateException(
                "Cannot dispatch action $action because state Flow of this " +
                    "FlowReduxStateMachine is not collected yet. " +
                    "Start collecting the state Flow before dispatching any action."
            )
        }
        inputActions.send(action)
    }

    private fun checkSpecBlockSet() {
        if (!::outputState.isInitialized) {
            throw IllegalStateException(
                """
                    No state machine specs are defined. Did you call spec { ... } in init {...}?
                    Example usage:

                    class MyStateMachine : FlowReduxStateMachine<State, Action>(InitialState) {

                        init{
                            spec {
                                inState<FooState> {
                                    on<BarAction> { ... }
                                }
                                ...
                            }
                        }
                    }
                """.trimIndent()
            )
        }
    }
}
