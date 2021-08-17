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

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.jetsnack.ui.ScaffoldStateHolder.Companion.ScaffoldStateHolderSaver
import com.example.jetsnack.ui.components.JetsnackScaffold
import com.example.jetsnack.ui.home.HomeSections
import com.example.jetsnack.ui.home.JetsnackBottomBar
import com.example.jetsnack.ui.theme.JetsnackTheme
import com.google.accompanist.insets.ProvideWindowInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbarHostState = scaffoldState.snackbarHostState

    return rememberSaveable(
        inputs = arrayOf(navController, snackbarHostState, coroutineScope, lifecycle),
        saver = ScaffoldStateHolderSaver(
            navController, snackbarHostState, coroutineScope, lifecycle
        )
    ) {
        Log.d("SNACKBAR", "Created scaffold state holder")
        ScaffoldStateHolder(
            navController, scaffoldState.snackbarHostState, coroutineScope, lifecycle, listOf()
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
    coroutineScope: CoroutineScope,
    lifecycle: Lifecycle,
    initialSnackbarMessages: List<String>
) {
    // Tabs for JetsnackBottomBar
    val tabs = HomeSections.values()

    private val snackbarMessagesState = MutableStateFlow(emptyList<String>())

    // Queue a maximum of 3 snackbar messages
    private val snackbarMessages = PendingMessagesStateToEventProducer(
        snackbarMessagesState,
        3
    )

    init {
        coroutineScope.launch {
            // Restore snackbar messages state
            initialSnackbarMessages.forEach { showSnackbar(it) }

            // Process snackbar events only when the lifecycle is at least STARTED
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                snackbarMessages.handleElements { message ->
                    Log.d("SNACKBAR", "Showing snackbar: $message")
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    fun showSnackbar(message: String) {
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

    companion object {
        fun ScaffoldStateHolderSaver(
            navController: NavHostController,
            snackbarHostState: SnackbarHostState,
            coroutineScope: CoroutineScope,
            lifecycle: Lifecycle
        ) = listSaver<ScaffoldStateHolder, String>(
            save = {
                Log.d("SNACKBAR", "saving: ${it.snackbarMessagesState.value}")
                it.snackbarMessagesState.value
            },
            restore = { pendingMessages ->
                Log.d("SNACKBAR", "restored: $pendingMessages")
                ScaffoldStateHolder(
                    navController, snackbarHostState, coroutineScope, lifecycle, pendingMessages
                )
            }
        )
    }
}

/**
 * A producer of events from state.
 *
 * This class performs a delicate handshake of acting upon state to produce events, and by the act
 * of creating an event, updates the state.
 *
 * This class manages a [MutableStateFlow] of [A].
 *
 * Elements of type [X] can be sent via [send], and will be combined with the current state [A]
 * to produce a new state [A] with [elementToState].
 *
 * Once sent, these elements must be thought of as "state", since the elements may not be
 * handled immediately, depending on the requirements of the consumer.
 *
 * This state can be persisted through recreation and process death by backing the [stateFlow] by
 * some form of saved instance state.
 *
 * In order to convert the elements back into an event, observers can call [handleElements].
 * This suspending function will listen for updates, and handle produced events using the provided
 * handler [block].
 *
 * The state-to-event contract is setup so the block will be called if and only if an event was
 * produced, thereby updating the backing state. In other words, as soon as the handler receives an
 * element [Y], this producer considers the event handled.
 *
 * [block] is allowed to be suspending. However, if it is, then there is no guarantee that all
 * of the suspending work will be done to handle the event.
 *
 * If the stored state provides for no elements, [StateToElementResult.NoProducedElement] should
 * be returned by [stateToElement]. This corresponds with the underlying state not changing,
 * indicating that there is nothing to do until a new element is sent.
 */
open class StateToEventProducer<A, X, Y>(
    val stateFlow: MutableStateFlow<A>,
    val elementToState: (X, A) -> A,
    val stateToElement: (A) -> StateToElementResult<A, Y>
) {
    suspend inline fun handleElements(
        crossinline block: suspend (Y) -> Unit
    ) {
        stateFlow
            .onEach {
                // Ignore the onEach state, since we're going to be using a CAS loop below

                // Keep track of the final result, which will be set by the "winning"/final
                // iteration of the loop
                var result: StateToElementResult<A, Y>

                // CAS loop. Don't use MutableStateFlow.update here, since we are keeping track of
                // the result directly
                while (true) {
                    val prevValue = stateFlow.value
                    result = stateToElement(prevValue)
                    val nextValue = when (result) {
                        is StateToElementResult.NoProducedElement -> prevValue
                        is StateToElementResult.ProducedElement -> result.newState
                    }

                    if (stateFlow.compareAndSet(prevValue, nextValue)) {
                        break
                    }
                }

                // With the winning result, handle the produced element.
                when (result) {
                    is StateToElementResult.NoProducedElement -> Unit
                    is StateToElementResult.ProducedElement -> {
                        block(result.element)
                    }
                }
            }
            .collect()
    }

    fun send(element: X) {
        stateFlow.update { state ->
            elementToState(element, state)
        }
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
    stateFlow: MutableStateFlow<List<T>> = MutableStateFlow(emptyList()),
    private val capacity: Int,
): StateToEventProducer<List<T>, T, T>(
    stateFlow = stateFlow,
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

/**
 * If the lifecycle is not resumed it means this NavBackStackEntry already processed a nav event.
 *
 * This is used to de-duplicate navigation events.
 */
private fun NavBackStackEntry.lifecycleIsResumed() =
    this.lifecycle.currentState == Lifecycle.State.RESUMED
