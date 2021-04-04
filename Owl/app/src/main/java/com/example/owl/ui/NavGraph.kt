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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
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
    val tokenizedNavController = navController.tokenizedNavController(
        initialToken = UUID.randomUUID()
    )
    val actions = remember(tokenizedNavController) { MainActions(tokenizedNavController) }
    val previousBackStackEntry by navController.previousBackStackEntryAsState()

    BackHandler(
        enabled = previousBackStackEntry != null,
        onBack = actions.backPress()
    )

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(MainDestinations.ONBOARDING_ROUTE) {
            // Intercept back in Onboarding: make it finish the activity
            BackHandler {
                finishActivity()
            }

            val onboardingCompleteLambda = actions.onboardingComplete()

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
                onCourseSelected = { actions.selectCourse() },
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
                selectCourse = actions.selectCourse(),
                upPress = actions.upPress(),
            )
        }
    }
}

/**
 * Models the navigation actions in the app.
 */
class MainActions(tokenizedNavController: TokenizedNavController<UUID>) {
    val onboardingComplete = tokenizedNavController.createActionGetter { currentToken ->
        // Update the token if navigation succeeded.
        if (popBackStack()) {
            UUID.randomUUID()
        } else {
            currentToken
        }
    }

    val selectCourse = tokenizedNavController.createActionGetter { _, courseId: Long ->
        navigate("${MainDestinations.COURSE_DETAIL_ROUTE}/$courseId")
        UUID.randomUUID()
    }

    val upPress = tokenizedNavController.createActionGetter { currentToken ->
        // Update the token if navigation succeeded.
        if (navigateUp()) {
            UUID.randomUUID()
        } else {
            currentToken
        }
    }

    val backPress = tokenizedNavController.createActionGetter { currentToken ->
        // Update the token if navigation succeeded.
        if (popBackStack()) {
            UUID.randomUUID()
        } else {
            currentToken
        }
    }
}

/**
 * Gets the previous navigation back stack entry as a [MutableState]. When the given navController
 * changes the back stack due to a [NavController.navigate] or [NavController.popBackStack] this
 * will trigger a recompose and return the previous entry on the back stack.
 *
 * @return a mutable state of the previous back stack entry
 */
@Composable
fun NavController.previousBackStackEntryAsState(): State<NavBackStackEntry?> {
    val previousBackStackEntry = remember { mutableStateOf(previousBackStackEntry) }
    // setup the onDestinationChangedListener responsible for detecting when the
    // current back stack entry changes
    DisposableEffect(this) {
        val callback = NavController.OnDestinationChangedListener { controller, _, _ ->
            previousBackStackEntry.value = controller.previousBackStackEntry
        }
        addOnDestinationChangedListener(callback)
        // remove the navController on dispose (i.e. when the composable is destroyed)
        onDispose {
            removeOnDestinationChangedListener(callback)
        }
    }
    return previousBackStackEntry
}
