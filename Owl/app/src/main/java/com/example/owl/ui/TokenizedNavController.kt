package com.example.owl.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavController
import androidx.navigation.NavHostController

/**
 * A wrapper around [NavHostController] to provide navigation with origin token support.
 *
 * Rather than navigating with [NavHostController] directly, actions that perform navigation
 * should be created with [createAction], or one of the [TokenizedNavController.createActionGetter]
 * extensions.
 */
fun interface TokenizedNavController<T> {

    /**
     * Creates an action lambda that will perform the given [navigationAction] when invoked,
     * subject to the [navigationAction]'s [TokenPolicy].
     */
    @Composable
    fun createAction(
        navigationAction: NavigationAction<T>,
    ): (Array<Any?>) -> Unit
}

/**
 * Wraps the [NavHostController] to create a [TokenizedNavController], with the given
 * [initialToken].
 */
@Composable
fun <T> NavHostController.tokenizedNavController(initialToken: T): TokenizedNavController<T> {
    val tokenState = rememberSaveable { mutableStateOf(initialToken) }

    return TokenizedNavController { navigationAction ->
        // Get the token value upon composition
        val composedToken = tokenState.value

        Log.d("TNC", "composedToken: $composedToken");

        { args ->
            // Get the current value (which may have changed since composition)
            val currentToken = tokenState.value

            Log.d("TNC", "currentToken: $currentToken")

            // Check the token policy
            if (navigationAction.tokenPolicy.shouldNavigate(composedToken, currentToken, args)) {
                // If the token policy is met, navigate and update the token
                tokenState.value = navigationAction.action(
                    this@tokenizedNavController, currentToken, args
                )

                Log.d("TNC", "newToken: ${tokenState.value}")
            }
        }
    }
}

/**
 * The policy that governs when a [NavigationAction] should be carried out when requested, based on
 * comparing the token value when the action was composed (tracking the "origination" of the
 * request) and the current token value.
 */
fun interface TokenPolicy<T> {

    /**
     * Returns `true` if the navigation should be performed, given the [composedToken] (the token
     * value when the action was composed), the [currentToken] (the actual current value of the
     * token), and the [args] of the navigation.
     */
    fun shouldNavigate(
        composedToken: T,
        currentToken: T,
        args: Array<Any?>,
    ): Boolean
}

/**
 * A [TokenPolicy] that always permits navigation. This is the default behavior of just calling
 * navigate on [NavController] directly.
 */
fun <T> alwaysNavigateTokenPolicy(): TokenPolicy<T> = TokenPolicy { _, _, _ -> true }

/**
 * A [TokenPolicy] that permits navigation when the tokens are structurally equal (`==`).
 */
fun <T> structuralEqualityTokenPolicy(): TokenPolicy<T> =
    TokenPolicy { composedToken, currentToken, _ -> composedToken == currentToken }

/**
 * The specification of a specific navigation action.
 *
 * This has two parts: a [tokenPolicy] and an [action].
 */
interface NavigationAction<T> {

    /**
     * The [TokenPolicy] for this action. If [TokenPolicy.shouldNavigate] returns `true`, then
     * [action] will be called, performing the navigation
     */
    val tokenPolicy: TokenPolicy<T>

    /**
     * The navigation action to perform. This will likely look like a set of methods called
     * on the [NavController] receiver, followed by computing the updated token based on the
     * navigation.
     */
    val action: NavController.(currentToken: T, args: Array<Any?>) -> T
}

/**
 * A simple implementation of [NavigationAction] via constructor arguments.
 */
private class NavigationActionImpl<T>(
    override val tokenPolicy: TokenPolicy<T>,
    override val action: NavController.(currentToken: T, args: Array<Any?>) -> T,
) : NavigationAction<T>

/**
 * Creates a [Composable] getter for a zero argument action to navigate.
 *
 * The specified [action] will be performed and update the token subject to the [tokenPolicy],
 * which as a default of structural equality.
 */
fun <T> TokenizedNavController<T>.createActionGetter(
    tokenPolicy: TokenPolicy<T> = structuralEqualityTokenPolicy(),
    action: NavController.(currentToken: T) -> T,
): @Composable () -> (() -> Unit) = {
    val navigationAction = createAction(
        NavigationActionImpl(
            action = { currentToken, _ ->
                action(this, currentToken)
            },
            tokenPolicy = tokenPolicy,
        )
    );

    { navigationAction(emptyArray()) }
}

/**
 * Creates a [Composable] getter for a one argument action to navigate.
 *
 * The specified [action] will be performed and update the token subject to the [tokenPolicy],
 * which as a default of structural equality.
 */
fun <T, A1> TokenizedNavController<T>.createActionGetter(
    tokenPolicy: TokenPolicy<T> = structuralEqualityTokenPolicy(),
    action: NavController.(currentToken: T, A1) -> T,
): @Composable () -> ((A1) -> Unit) = {
    val navigationAction = createAction(
        @Suppress("UNCHECKED_CAST")
        NavigationActionImpl(
            action = { currentToken, args ->
                action(this, currentToken, args[0] as A1)
            },
            tokenPolicy = tokenPolicy,
        )
    );

    { arg1 -> navigationAction(arrayOf(arg1)) }
}

/**
 * Creates a [Composable] getter for a two argument action to navigate.
 *
 * The specified [action] will be performed and update the token subject to the [tokenPolicy],
 * which as a default of structural equality.
 */
fun <T, A1, A2> TokenizedNavController<T>.createActionGetter(
    tokenPolicy: TokenPolicy<T> = structuralEqualityTokenPolicy(),
    action: NavController.(currentToken: T, A1, A2) -> T,
): @Composable () -> ((A1, A2) -> Unit) = {
    val navigationAction = createAction(
        @Suppress("UNCHECKED_CAST")
        NavigationActionImpl(
            action = { currentToken, args ->
                action(this, currentToken, args[0] as A1, args[1] as A2)
            },
            tokenPolicy = tokenPolicy,
        )
    );

    { arg1, arg2 -> navigationAction(arrayOf(arg1, arg2)) }
}

// Add arities as needed
