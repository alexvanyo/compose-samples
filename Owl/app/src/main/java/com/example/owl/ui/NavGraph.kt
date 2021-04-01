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

package com.example.owl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.navigate
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.owl.ui.MainDestinations.COURSE_DETAIL_ID_KEY
import com.example.owl.ui.course.CourseDetails
import com.example.owl.ui.courses.CourseTabs
import com.example.owl.ui.courses.courses
import com.example.owl.ui.onboarding.Onboarding
import java.util.UUID

/**
 * Destinations used in the ([OwlApp]).
 */
object MainDestinations {
    const val ONBOARDING_ROUTE = "onboarding"
    const val COURSES_ROUTE = "courses"
    const val COURSE_DETAIL_ROUTE = "course"
    const val COURSE_DETAIL_ID_KEY = "courseId"
}

@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    finishActivity: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    startDestination: String = MainDestinations.COURSES_ROUTE,
    showOnboardingInitially: Boolean = true
) {
    // Onboarding could be read from shared preferences.
    val onboardingComplete = remember(showOnboardingInitially) {
        mutableStateOf(!showOnboardingInitially)
    }

    val actions = remember(navController) { MainActions(navController) }
    val tokenState = navController.currentBackStackToken()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(MainDestinations.ONBOARDING_ROUTE) {
            // Intercept back in Onboarding: make it finish the activity
            BackHandler {
                finishActivity()
            }

            val onboardingCompleteLambda = actions.onboardingComplete(tokenState)

            Onboarding(
                onboardingComplete = {
                    // Set the flag so that onboarding is not shown next time.
                    onboardingComplete.value = true
                    onboardingCompleteLambda()
                }
            )
        }
        navigation(
            route = MainDestinations.COURSES_ROUTE,
            startDestination = CourseTabs.FEATURED.route
        ) {
            courses(
                onCourseSelected = { actions.selectCourse(tokenState) },
                onboardingComplete = onboardingComplete,
                navController = navController,
                modifier = modifier
            )
        }
        composable(
            "${MainDestinations.COURSE_DETAIL_ROUTE}/{$COURSE_DETAIL_ID_KEY}",
            arguments = listOf(
                navArgument(COURSE_DETAIL_ID_KEY) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val arguments = requireNotNull(backStackEntry.arguments)
            CourseDetails(
                courseId = arguments.getLong(COURSE_DETAIL_ID_KEY),
                selectCourse = actions.selectCourse(tokenState),
                upPress = actions.upPress(tokenState),
            )
        }
    }
}

/**
 * Returns a [State] of [UUID] that represents a unique token for the current back stack state.
 *
 * Whenever the backstack changes, a new [UUID] will be created, triggering a recompose.
 */
@Composable
fun NavController.currentBackStackToken(): State<UUID> {
    val currentBackStackToken = remember { mutableStateOf(UUID.randomUUID()) }

    DisposableEffect(this) {
        val callback = NavController.OnDestinationChangedListener { _, _, _ ->
            currentBackStackToken.value = UUID.randomUUID()
        }
        addOnDestinationChangedListener(callback)
        onDispose {
            removeOnDestinationChangedListener(callback)
        }
    }
    return currentBackStackToken
}

/**
 * Models the navigation actions in the app.
 */
class MainActions(
    private val navController: NavHostController,
) {
    fun onboardingComplete(tokenState: State<UUID>): () -> Unit =
        stateCheck(tokenState) { ->
            navController.popBackStack()
        }
    fun selectCourse(tokenState: State<UUID>): (Long) -> Unit =
        stateCheck(tokenState) { courseId: Long ->
            navController.navigate("${MainDestinations.COURSE_DETAIL_ROUTE}/$courseId")
        }
    fun upPress(tokenState: State<UUID>): () -> Unit = stateCheck(tokenState) { ->
        navController.navigateUp()
    }
}

/**
 * Wraps the [block] lambda in a state check.
 *
 * When this method is called, the current [State.value] of [state] will be queried.
 * When the return lambda is called, the [State.value] of [state] will be queried again.
 *
 * If those two values match, then [block] will be invoked. Otherwise, nothing will happen.
 */
private fun <S> stateCheck(state: State<S>, block: () -> Unit): () -> Unit {
    val value = state.value
    return { if (state.value == value) block() }
}

/**
 * Wraps the [block] lambda in a token check.
 *
 * When this method is called, the current [State.value] of [state] will be queried.
 * When the return lambda is called, the [State.value] of [state] will be queried again.
 *
 * If those two values match, then [block] will be invoked. Otherwise, nothing will happen.
 */
private fun <S, T1> stateCheck(state: State<S>, block: (T1) -> Unit): (T1) -> Unit {
    val value = state.value
    return { t1 -> if (state.value == value) block(t1) }
}
