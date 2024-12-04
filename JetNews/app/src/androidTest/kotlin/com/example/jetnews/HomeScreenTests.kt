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

package com.example.jetnews

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.LayoutDirection
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.then
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetnews.ui.home.HomeFeedScreen
import com.example.jetnews.ui.home.HomeUiState
import com.example.jetnews.ui.home.PostCardPreview
import com.example.jetnews.ui.theme.JetnewsTheme
import com.example.jetnews.utils.ErrorMessage
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class HomeScreenTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Checks that the Snackbar is shown when the HomeScreen data contains an error.
     */
//    @Test
    fun postsContainError_snackbarShown() {
        val snackbarHostState = SnackbarHostState()
        composeTestRule.setContent {
            JetnewsTheme {

                // When the Home screen receives data with an error
                HomeFeedScreen(
                    uiState = HomeUiState.NoPosts(
                        isLoading = false,
                        errorMessages = listOf(ErrorMessage(0L, R.string.load_error)),
                        searchInput = ""
                    ),
                    showTopAppBar = false,
                    onToggleFavorite = {},
                    onSelectPost = {},
                    onRefreshPosts = {},
                    onErrorDismiss = {},
                    openDrawer = {},
                    homeListLazyListState = rememberLazyListState(),
                    snackbarHostState = snackbarHostState,
                    onSearchInputChanged = {}
                )
            }
        }

        // Then the first message received in the Snackbar is an error message
        runBlocking {
            // snapshotFlow converts a State to a Kotlin Flow so we can observe it
            // wait for the first a non-null `currentSnackbarData`
            val actualSnackbarText = snapshotFlow { snackbarHostState.currentSnackbarData }
                .filterNotNull().first().visuals.message
            val expectedSnackbarText = InstrumentationRegistry.getInstrumentation()
                .targetContext.resources.getString(R.string.load_error)
            assertEquals(expectedSnackbarText, actualSnackbarText)
        }
    }

//    @Test
    fun postCardSemantic() {
        composeTestRule.setContent {
            DeviceConfigurationOverride(
                // ...
                DeviceConfigurationOverride.ForcedSize(DpSize(200.dp, 300.dp))
            ) {
                Box {
                    PostCardPreview()
                }
            }
        }

        composeTestRule.onRoot().printToLog("vanyo")
    }

    @Test
    fun TermsPane() {
        var hasBeenClicked = false
        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f)
            ) {
                TermsPane(
                    continueOnClick = { hasBeenClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Continue")
            .performScrollTo()
            .performClick()

        assertTrue(hasBeenClicked)
    }















    @Test
    fun AnchoredDraggableLtr() {
        val anchoredDraggableState = AnchoredDraggableState(false)

        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 200.dp)) then
                DeviceConfigurationOverride.LayoutDirection(LayoutDirection.Ltr)
            ) {
                SimpleAnchoredDraggable(anchoredDraggableState)
            }
        }

        composeTestRule.onNodeWithTag("handle")
            .performMouseInput {
                dragAndDrop(
                    start = center,
                    end = center + Offset(400.dp.toPx(), 0f))
            }

        assertTrue(anchoredDraggableState.currentValue)
    }

    @Test
    fun AnchoredDraggableRtl() {
        val anchoredDraggableState = AnchoredDraggableState(false)

        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 200.dp)) then
                DeviceConfigurationOverride.LayoutDirection(LayoutDirection.Rtl)
            ) {
                SimpleAnchoredDraggable(anchoredDraggableState)
            }
        }

        composeTestRule.onNodeWithTag("handle")
            .performMouseInput {
                dragAndDrop(
                    start = center,
                    end = center - Offset(400.dp.toPx(), 0f)
                )
            }

        assertTrue(anchoredDraggableState.currentValue)
    }

    @Composable
    fun SimpleAnchoredDraggable(
        anchoredDraggableState: AnchoredDraggableState<Boolean>,
        modifier: Modifier = Modifier,
    ) {

        Box(
            modifier
                .fillMaxWidth()
                .padding(50.dp)
                .height(100.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    anchoredDraggableState.updateAnchors(
                        DraggableAnchors {
                            false at 0f
                            true at placeable.width.toFloat()
                        }
                    )
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
        ) {
            Spacer(
                Modifier
                    .size(100.dp)
                    .offset((-50).dp)
                    .offset {
                        IntOffset(
                            x = anchoredDraggableState.offset.roundToInt(),
                            y = 0
                        )
                    }
                    .background(Color.Blue)
                    .testTag("handle")
                    .anchoredDraggable(
                        state = anchoredDraggableState,
                        orientation = Orientation.Horizontal
                    )
            )
        }

    }
}

@Composable
fun TermsPane(
    continueOnClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("About", style = MaterialTheme.typography.titleLarge)
            Text(
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas sed diam eget risus varius blandit sit amet non magna.\n" +
                        "\n" +
                        "Nullam id dolor id nibh ultricies Column.  Vestibulum id ligula porta felis euismod semper.  Cras justo odio, dapibus ac facilisis in, egestas eget quam.   \n" +
                        "\n" +
                        "Donec id elit non mi porta gravida at eget metus.  Modifier.clickable {}, padding(), background().  Nulla vitae elit libero, a pharetra augue.   \n" +
                        "\n" +
                        "Aenean eu leo quam. Pellentesque ornare sem lacinia quam venenatis vestibulum. remember  state, recompose UI.  ViewModel provides data, survives configuration changes.\n" +
                        "\n" +
                        "Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus.  LazyColumn for efficient lists.  Text composable displays text.\n" +
                        "\n" +
                        "Maecenas faucibus mollis interdum.  Kotlin Coroutines for asynchronous operations.  Scaffold provides structure.\n" +
                        "\n" +
                        "Aenean lacinia bibendum nulla sed consectetur.  Navigation Component for in-app navigation.  Row arranges items horizontally.\n" +
                        "\n" +
                        "Donec ullamcorper nulla non metus auctor fringilla.  StateObject  manages state.  LaunchedEffect  runs side effects.\n" +
                        "\n" +
                        "Vestibulum id ligula porta felis euismod semper.  MaterialTheme for styling.  Canvas for custom drawing.",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                modifier = Modifier.align(Alignment.End),
                onClick = continueOnClick,
            ) {
                Text("Continue")
            }
        }
    }
}

@Preview(locale = "ar")
@Composable
fun AnchoredDraggableTest() {

    val anchoredDraggableState = rememberSaveable(saver = AnchoredDraggableState.Saver()) {
        AnchoredDraggableState(false)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(50.dp)
            .height(100.dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                anchoredDraggableState.updateAnchors(
                    DraggableAnchors {
                        false at 0f
                        true at placeable.width.toFloat()
                    }
                )
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
    ) {
        Spacer(
            Modifier
                .size(100.dp)
                .offset((-50).dp)
                .offset {
                    IntOffset(
                        x = anchoredDraggableState.offset.roundToInt(),
                        y = 0
                    )
                }
                .background(Color.Blue)
                .anchoredDraggable(
                    state = anchoredDraggableState,
                    orientation = Orientation.Horizontal
                )
        )
    }

}

@Preview(widthDp = 300, heightDp = 300)
@Composable
fun ABC() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .wrapContentSize(unbounded = true, align = Alignment.CenterEnd)
        ) {
            Spacer(Modifier.widthIn(min = 400.dp).fillMaxWidth().background(Color.Yellow).border(3.dp, Color.Red))
        }
    }
}
