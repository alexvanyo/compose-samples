/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetsnack.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.jetsnack.ui.components.JetsnackScaffold
import com.example.jetsnack.ui.home.HomeSections
import com.example.jetsnack.ui.home.JetsnackBottomBar
import com.example.jetsnack.ui.theme.JetsnackTheme
import com.google.accompanist.insets.ProvideWindowInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun JetsnackApp() {
    ProvideWindowInsets {
        JetsnackTheme {
            val scaffoldState = rememberScaffoldState()
            val scaffoldStateHolder = rememberScaffoldStateHolder(scaffoldState)

            JetsnackScaffold(
                scaffoldState = scaffoldState,
                bottomBar = {
                    JetsnackBottomBar(scaffoldStateHolder.navController, scaffoldStateHolder.tabs)
                }
            ) { innerPaddingModifier ->
                JetsnackNavGraph(
                    scaffoldStateHolder = scaffoldStateHolder,
                    modifier = Modifier.padding(innerPaddingModifier)
                )
            }
        }
    }
}

/**
 * Creates a [ScaffoldStateHolder] and memoizes it.
 *
 * Pending snackbar messages to show on the screen are remembered across
 * activity and process recreation.
 */
@Composable
private fun rememberScaffoldStateHolder(
    scaffoldState: ScaffoldState = rememberScaffoldState()
): ScaffoldStateHolder {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbarHostState = scaffoldState.snackbarHostState

//    return rememberSaveable(
//        inputs = arrayOf(navController, snackbarHostState, coroutineScope, context, lifecycle),
//        saver = ScaffoldStateHolderSaver(
//            navController, snackbarHostState, coroutineScope, lifecycle
//        )
//    ) {
//
//
//    }

    return remember {
        Log.d("SNACKBAR", "Created scaffold state holder")
        ScaffoldStateHolder(
            navController, scaffoldState.snackbarHostState, context, coroutineScope, lifecycle, listOf()
        )
    }
}

/**
 * State holder for Jetsnack's app scaffold that contains state related to [JetsnackScaffold], and
 * handles Navigation and Snackbar events.
 */
class ScaffoldStateHolder(
    val navController: NavHostController,
    private val snackbarHostState: SnackbarHostState,
    context: Context,
    coroutineScope: CoroutineScope,
    lifecycle: Lifecycle,
    initialSnackbarMessages: List<String>
) {
    // Tabs for JetsnackBottomBar
    val tabs = HomeSections.values()

    // Queue a maximum of 3 snackbar messages
    private val snackbarMessages = DataStoreMessagesStateToEventProducer(3, context)

    init {
        coroutineScope.launch {
            // Restore snackbar messages state
            initialSnackbarMessages.forEach { showSnackbar(it) }

            // Process snackbar events only when the lifecycle is at least STARTED
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                snackbarMessages.stream
                    .collect { message ->
                        Log.d("SNACKBAR", "Showing snackbar: $message")
                        snackbarHostState.showSnackbar(message)
                    }
            }
        }
    }

    suspend fun showSnackbar(message: String) {
        snackbarMessages.send(message)
    }

    fun navigateToSnackDetail(from: NavBackStackEntry, snackId: Long) {
        // In order to discard duplicated navigation events, we check the Lifecycle
        if (from.lifecycleIsResumed()) {
            navController.navigate("${MainDestinations.SNACK_DETAIL_ROUTE}/$snackId")
        }
    }

    fun onBackPress() {
        navController.navigateUp()
    }
//
//    companion object {
//        fun ScaffoldStateHolderSaver(
//            navController: NavHostController,
//            snackbarHostState: SnackbarHostState,
//            coroutineScope: CoroutineScope,
//            context: Context,
//            lifecycle: Lifecycle
//        ) = listSaver<ScaffoldStateHolder, String>(
//            save = {
//                Log.d("SNACKBAR", "saving: ${it.snackbarMessages.state}")
//                it.snackbarMessages.state
//            },
//            restore = { pendingMessages ->
//                Log.d("SNACKBAR", "restored: $pendingMessages")
//                ScaffoldStateHolder(
//                    navController, snackbarHostState, coroutineScope, context, lifecycle, pendingMessages
//                )
//            }
//        )
//    }
}

/**
 * A producer of events from state.
 *
 * This class performs a delicate handshake of acting upon state to produce events, and by the act
 * of creating an event, updates the state.
 *
 * This class keeps a state of [A], with the initial value of [initialState].
 *
 * Elements of type [X] can be sent via [send], and will be combined with the current state [A]
 * to produce a new state [A] with [elementToState].
 *
 * Once sent, these elements must be thought of as "state", since the elements may not be
 * handled immediately, depending on the requirements of the consumer.
 *
 * This state can be persisted through recreation and process death by retrieving it
 * synchronously via [state].
 *
 * In order to convert the elements back into an event, a [stream] of elements is provided.
 * This stream is special: the act of collecting an event from the stream consumes it, creating a
 * new state, as governed by [stateToElement] returning [StateToElementResult.ProducedElement].
 *
 * In other words, as soon as the collector receives an element [Y], this producer considers the
 * event handled.
 *
 * If your collector never suspends, this will guarantee that an element will be removed
 * from the list if and only if the handler runs.
 *
 * However, if your collector suspends, that suspension point may result in everything after the
 * suspension point to not be run, even though the event is removed from the list.
 * In particular, a pausing dispatcher (like `launchWhenResumed`) will consume the element if the
 * state requirement isn't met, causing the event to be consumed even if nothing else gets a chance
 * to run.
 *
 * If the stored state provides for no elements, [StateToElementResult.NoProducedElement] should
 * be returned by [stateToElement]. This corresponds with the underlying state not changing,
 * indicating that there is nothing to do until a new element is sent.
 */
open class LocalStateToEventProducer<A, X, Y>(
    initialState: A,
    private val elementToState: suspend (X, A) -> A,
    private val stateToElement: suspend (A) -> StateToElementResult<A, Y>
) : StateToEventProducer<A, X, Y>(
    elementToState,
    stateToElement,
) {

    /**
     * The state for elements that haven't yet been consumed.
     */
    var state = initialState
        private set

    override suspend fun <T> runStateTransaction(
        stateTransaction: suspend StateTransaction<A>.() -> T
    ): T =
        with(object : StateTransaction<A> {
            override fun getState(): A = state

            override fun setState(state: A) {
                this@LocalStateToEventProducer.state = state
            }
        }) {
            stateTransaction()
        }
}

/**
 * A producer of events from state.
 *
 * This class performs a delicate handshake of acting upon state to produce events, and by the act
 * of creating an event, updates the state.
 *
 * This class keeps a state of [A], with the initial value of [initialState].
 *
 * Elements of type [X] can be sent via [send], and will be combined with the current state [A]
 * to produce a new state [A] with [elementToState].
 *
 * Once sent, these elements must be thought of as "state", since the elements may not be
 * handled immediately, depending on the requirements of the consumer.
 *
 * This state can be persisted through recreation and process death by retrieving it
 * synchronously via [state].
 *
 * In order to convert the elements back into an event, a [stream] of elements is provided.
 * This stream is special: the act of collecting an event from the stream consumes it, creating a
 * new state, as governed by [stateToElement] returning [StateToElementResult.ProducedElement].
 *
 * In other words, as soon as the collector receives an element [Y], this producer considers the
 * event handled.
 *
 * If your collector never suspends, this will guarantee that an element will be removed
 * from the list if and only if the handler runs.
 *
 * However, if your collector suspends, that suspension point may result in everything after the
 * suspension point to not be run, even though the event is removed from the list.
 * In particular, a pausing dispatcher (like `launchWhenResumed`) will consume the element if the
 * state requirement isn't met, causing the event to be consumed even if nothing else gets a chance
 * to run.
 *
 * If the stored state provides for no elements, [StateToElementResult.NoProducedElement] should
 * be returned by [stateToElement]. This corresponds with the underlying state not changing,
 * indicating that there is nothing to do until a new element is sent.
 */
abstract class StateToEventProducer<A, X, Y>(
    private val elementToState: suspend (X, A) -> A,
    private val stateToElement: suspend (A) -> StateToElementResult<A, Y>
) {
    private val stateMutex = Mutex()

    /**
     * A [MutableSharedFlow] that purely acts as a "trigger" to look at the state again for ongoing
     * collectors.
     */
    private val stateUpdatedPing = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply {
        tryEmit(Unit)
    }

    val stream: Flow<Y> = stateUpdatedPing
        .transform {
            val result = stateMutex.withLock {
                runStateTransaction {
                    val oldState = getState()
                    val result = stateToElement(oldState)
                    when (result) {
                        is StateToElementResult.NoProducedElement -> Unit
                        is StateToElementResult.ProducedElement -> {
                            setState(result.newState)
                        }
                    }
                    result
                }
            }

            val element = when (result) {
                is StateToElementResult.NoProducedElement -> return@transform
                is StateToElementResult.ProducedElement -> result.element
            }

            // The state was updated, so send out another ping to handle it
            stateUpdatedPing.tryEmit(Unit)
            emit(element)
        }

    protected abstract suspend fun <T> runStateTransaction(
        stateTransaction: suspend StateTransaction<A>.() -> T
    ): T

    suspend fun send(element: X) {
        stateMutex.withLock {
            runStateTransaction {
                val oldState = getState()
                val newState = elementToState(element, oldState)
                setState(newState)
            }
        }
        // The state was updated, so send out another ping to handle it
        stateUpdatedPing.tryEmit(Unit)
    }

    interface StateTransaction<T> {
        fun getState(): T
        fun setState(state: T)
    }

    sealed class StateToElementResult<out A, out Y> {
        data class ProducedElement<A, Y>(
            val element: Y,
            val newState: A,
        ) : StateToElementResult<A, Y>()

        object NoProducedElement : StateToElementResult<Nothing, Nothing>()
    }
}

/**
 * A simple producer of events from state, where elements are enqueued, with a maximum [capacity].
 *
 * This class keeps a list of pending elements [T], with the given [capacity].
 *
 * Elements can be sent via [send], and if the list of pending elements exceeds the given
 * [capacity], the oldest elements will be dropped.
 *
 * Any dropped elements are guaranteed to _not_ be handled.
 */
class PendingMessagesStateToEventProducer<T>(
    private val capacity: Int,
): LocalStateToEventProducer<List<T>, T, T>(
    initialState = emptyList(),
    elementToState = { element, list ->
        (list + element).takeLast(capacity)
    },
    stateToElement = { list ->
        if (list.isEmpty()) {
            StateToElementResult.NoProducedElement
        } else {
            StateToElementResult.ProducedElement(
                element = list.first(),
                newState = list.drop(1)
            )
        }
    }
) {
    init {
        require(capacity >= 0)
    }
}

class DataStoreMessagesStateToEventProducer(
    private val capacity: Int,
    private val context: Context
) : StateToEventProducer<List<String>, String, String>(
    elementToState = { element, list ->
        (list + element).takeLast(capacity)
    },
    stateToElement = { list ->
        if (list.isEmpty()) {
            StateToElementResult.NoProducedElement
        } else {
            StateToElementResult.ProducedElement(
                element = list.first(),
                newState = list.drop(1)
            )
        }
    }
) {
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> runStateTransaction(
        stateTransaction: suspend StateTransaction<List<String>>.() -> T
    ): T {
        var result: T? = null
        context.dataStore.edit { mutablePreferences ->
            result = with(
                // Note: very silly serialization logic (assuming no `,` in strings, etc.)
                object : StateTransaction<List<String>> {
                    override fun getState(): List<String> =
                        mutablePreferences[stringPreferencesKey("test")]
                            ?.split(",")
                            .orEmpty()
                            .filter { it.isNotEmpty() }

                    override fun setState(state: List<String>) {
                        mutablePreferences[stringPreferencesKey("test")] =
                            state.joinToString(",")
                    }
                }
            ) {
                stateTransaction()
            }
        }
        return result as T
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "test")

/**
 * If the lifecycle is not resumed it means this NavBackStackEntry already processed a nav event.
 *
 * This is used to de-duplicate navigation events.
 */
private fun NavBackStackEntry.lifecycleIsResumed() =
    this.lifecycle.currentState == Lifecycle.State.RESUMED
