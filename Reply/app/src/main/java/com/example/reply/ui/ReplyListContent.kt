/*
 * Copyright 2022 The Android Open Source Project
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

package com.example.reply.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.AdaptiveLayoutDirective
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.GutterSizes
import androidx.compose.material3.adaptive.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldState
import androidx.compose.material3.adaptive.PaneAdaptedValue
import androidx.compose.material3.adaptive.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.calculateWindowAdaptiveInfo
import androidx.compose.material3.adaptive.rememberListDetailPaneScaffoldState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import com.example.reply.data.Email
import com.example.reply.ui.components.EmailDetailAppBar
import com.example.reply.ui.components.ReplyDockedSearchBar
import com.example.reply.ui.components.ReplyEmailListItem
import com.example.reply.ui.components.ReplyEmailThreadItem
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

val minPaneWidth = 250.dp
val fullInnerVerticalGutter = 24.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ReplyInboxScreenCAMAL(
    replyHomeUIState: ReplyHomeUIState,
    navigateToDetail: (Long) -> Unit,
    toggleSelectedEmail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val emailLazyListState = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints {

        var isInitialAnchors by remember { mutableStateOf(false) }
        val anchors = remember(constraints.maxWidth, density) {
            val min = 0f
            val max = constraints.maxWidth.toFloat() - with(density) { 48.dp.toPx() }

            val diffToPreserveMinSize =
                minPaneWidth + fullInnerVerticalGutter / 2

            // Continuous
//            val middleMin = with(density) { min + diffToPreserveMinSize.toPx() }
//            val middleMax = with(density) { max - diffToPreserveMinSize.toPx() }

            val middleMin = min + max / 2
            val middleMax = middleMin
            val hasMiddle = middleMin <= middleMax

            // Create a DraggableAnchors with a large continuous interval in the middle, where
            // every point is a valid anchor. The valid anchor set looks like:
            // +    +--------------+    +
            // TODO: Check if this is a good idea with Jossi
            object : DraggableAnchors<DraggablePaneType> {
                /**
                 * TODO: This isn't really true? The middle anchor is a large continuous
                 *       one
                 */
                override val size: Int = 3

                override fun closestAnchor(position: Float): DraggablePaneType =
                    if (hasMiddle) {
                        if (position < (min + middleMin) / 2) {
                            DraggablePaneType.DetailMax
                        } else if (position < (max + middleMax) / 2) {
//                            DraggablePaneType.Split(
//                                (position.coerceIn(middleMin, middleMax) - middleMin) /
//                                        (middleMax - middleMin)
//                            )
                            DraggablePaneType.Split(0.5f)
                        } else {
                            DraggablePaneType.ListMax
                        }
                    } else {
                        if (position < (min + max) / 2) {
                            DraggablePaneType.DetailMax
                        } else {
                            DraggablePaneType.ListMax
                        }
                    }

                override fun closestAnchor(
                    position: Float,
                    searchUpwards: Boolean
                ): DraggablePaneType? =
                    if (searchUpwards) {
                        if (hasMiddle) {
                            if (position <= min) {
                                DraggablePaneType.DetailMax
                            } else if (position < (max + middleMax) / 2) {
//                                DraggablePaneType.Split(
//                                    (position.coerceIn(middleMin, middleMax) - middleMin) /
//                                            (middleMax - middleMin)
//                                )
                                DraggablePaneType.Split(0.5f)
                            } else if (position <= max) {
                                DraggablePaneType.ListMax
                            } else {
                                null
                            }
                        } else {
                            if (position <= min) {
                                DraggablePaneType.DetailMax
                            } else if (position <= max) {
                                DraggablePaneType.ListMax
                            } else {
                                null
                            }
                        }
                    } else {
                        if (hasMiddle) {
                            if (position < min) {
                                null
                            } else if (position < (min + middleMin) / 2) {
                                DraggablePaneType.DetailMax
                            } else if (position < max) {
//                                DraggablePaneType.Split(
//                                    (position.coerceIn(middleMin, middleMax) - middleMin) /
//                                            (middleMax - middleMin)
//                                )
                                DraggablePaneType.Split(0.5f)
                            } else {
                                DraggablePaneType.ListMax
                            }
                        } else {
                            if (position < min) {
                                null
                            } else if (position <= max) {
                                DraggablePaneType.DetailMax
                            } else {
                                DraggablePaneType.ListMax
                            }
                        }
                    }

                override fun maxAnchor(): Float = max

                override fun minAnchor(): Float = min

                override fun positionOf(value: DraggablePaneType): Float =
                    when (value) {
                        DraggablePaneType.DetailMax -> min
                        DraggablePaneType.ListMax -> max
                        is DraggablePaneType.Split -> with(density) {
                            lerp(
                                middleMin.toDp(),
                                middleMax.toDp(),
                                value.listPreferredWeight
                            ).toPx()
                        }
                    }

                override fun hasAnchorFor(value: DraggablePaneType): Boolean =
                    when (value) {
                        DraggablePaneType.DetailMax -> true
                        DraggablePaneType.ListMax -> true
                        is DraggablePaneType.Split -> hasMiddle
                    }
            }
        }

        val initialAnchor: DraggablePaneType = DraggablePaneType.Split(0.5f)

        val anchoredDraggableState = rememberSaveable(
            saver = Saver<AnchoredDraggableState<DraggablePaneType>, List<Any>>(
                save = {
                    when (val currentValue = it.currentValue) {
                        DraggablePaneType.DetailMax -> listOf(0)
                        DraggablePaneType.ListMax -> listOf(1)
                        is DraggablePaneType.Split -> listOf(2, currentValue.listPreferredWeight)
                    }
                },
                restore = {
                    AnchoredDraggableState(
                        initialValue = when (it[0] as Int) {
                            0 -> DraggablePaneType.DetailMax
                            1 -> DraggablePaneType.ListMax
                            2 -> DraggablePaneType.Split(it[1] as Float)
                            else -> error("unknown type!")
                        },
                        animationSpec = spring(),
                        confirmValueChange = { true },
                        positionalThreshold = { distance: Float -> distance * 0.5f },
                        velocityThreshold = { with(density) { 400.dp.toPx() } },
                    )
                }
            )
        ) {
            AnchoredDraggableState<DraggablePaneType>(
                initialValue = initialAnchor,
                positionalThreshold = { distance: Float -> distance * 0.5f },
                velocityThreshold = { with(density) { 400.dp.toPx() } },
                animationSpec = spring(),
            )
        }.apply {
            updateAnchors(anchors)
        }

        var isFocusedOnDetail by rememberSaveable { mutableStateOf(false) }

        DisposableEffect(anchoredDraggableState.currentValue, anchoredDraggableState.isAnimationRunning) {
            if (!anchoredDraggableState.isAnimationRunning) {
                when (anchoredDraggableState.currentValue) {
                    DraggablePaneType.DetailMax -> {
                        isFocusedOnDetail = true
                    }
                    DraggablePaneType.ListMax -> {
                        isFocusedOnDetail = false
                    }
                    is DraggablePaneType.Split -> {
                    }
                }
            }
            onDispose {}
        }

//        LaunchedEffect(constraints.maxWidth) {
//            anchoredDraggableState.snapTo(
//                anchors.closestAnchor(
//                    anchors.positionOf(anchoredDraggableState.currentValue)
//                )
//            )
//        }

        val layoutDirectives = calculateCustomAdaptiveLayoutDirective(
            anchoredDraggableState,
            calculateWindowAdaptiveInfo()
        )

        val coroutineScope = rememberCoroutineScope()

        val listDetailLayoutState = object : ListDetailPaneScaffoldState {
            override val layoutDirective: AdaptiveLayoutDirective
                get() = layoutDirectives
            override val layoutValue: ThreePaneScaffoldValue
                get() = if (layoutDirective.maxHorizontalPartitions >= 2) {
                    ThreePaneScaffoldValue(
                        primary = PaneAdaptedValue.Expanded,
                        secondary = PaneAdaptedValue.Expanded,
                        tertiary = PaneAdaptedValue.Hidden
                    )
                } else {
                    ThreePaneScaffoldValue(
                        primary = if (isFocusedOnDetail) {
                            PaneAdaptedValue.Expanded
                        } else {
                            PaneAdaptedValue.Hidden
                        },
                        secondary = if (isFocusedOnDetail) {
                            PaneAdaptedValue.Hidden
                        } else {
                            PaneAdaptedValue.Expanded
                        },
                        tertiary = PaneAdaptedValue.Hidden
                    )
                }

            override fun canNavigateBack(layoutValueMustChange: Boolean): Boolean =
                (layoutDirective.maxHorizontalPartitions == 1 && isFocusedOnDetail) || (
                        layoutDirective.maxHorizontalPartitions >= 2 &&
                                anchoredDraggableState.currentValue == DraggablePaneType.DetailMax &&
                                !anchoredDraggableState.isAnimationRunning
                        )

            override fun navigateBack(popUntilLayoutValueChange: Boolean): Boolean =
                if (layoutDirective.maxHorizontalPartitions >= 2) {
                    if (anchoredDraggableState.currentValue == DraggablePaneType.DetailMax &&
                        !anchoredDraggableState.isAnimationRunning) {
                        coroutineScope.launch {
                            anchoredDraggableState.animateTo(
                                anchoredDraggableState.anchors.closestAnchor(
                                    anchoredDraggableState.anchors.maxAnchor(),
                                )!!
                            )
                        }
                        true
                    } else {
                        false
                    }
                } else if (isFocusedOnDetail) {
                    isFocusedOnDetail = false
                    if (anchoredDraggableState.currentValue == DraggablePaneType.DetailMax &&
                        !anchoredDraggableState.isAnimationRunning) {
                        coroutineScope.launch {
                            anchoredDraggableState.snapTo(
                                anchoredDraggableState.anchors.closestAnchor(
                                    anchoredDraggableState.anchors.maxAnchor(),
                                )!!
                            )
                        }
                        true
                    }
                    true
                } else {
                    false
                }

            override fun navigateTo(pane: ListDetailPaneScaffoldRole) {
                when (pane) {
                    ListDetailPaneScaffoldRole.List -> {
                        isFocusedOnDetail = false
                    }
                    ListDetailPaneScaffoldRole.Detail -> {
                        isFocusedOnDetail = true
                    }
                    ListDetailPaneScaffoldRole.Extra -> {

                    }
                }
            }

        }

        BackHandler(enabled = listDetailLayoutState.canNavigateBack()) {
            listDetailLayoutState.navigateBack()
        }

        ListDetailPaneScaffold(
            layoutState = listDetailLayoutState,
            listPane = {
                val listPreferredWidth = anchoredDraggableState.requireOffset()

                Box(
                    // TODO: Could we do some sort of preferred weight here?
                    modifier = Modifier
                        .preferredWidth(
                            with(density) {
                                listPreferredWidth
                                    .coerceAtLeast(0.0001f)
                                    .toDp()
                            }
                        )
                        .clipToBounds(),
                ) {
                    ReplyEmailList(
                        emails = replyHomeUIState.emails,
                        openedEmail = replyHomeUIState.openedEmail,
                        selectedEmailIds = replyHomeUIState.selectedEmails,
                        toggleEmailSelection = toggleSelectedEmail,
                        emailLazyListState = emailLazyListState,
                        navigateToDetail = { id ->
                            navigateToDetail.invoke(id)
                            listDetailLayoutState.navigateTo(ListDetailPaneScaffoldRole.Detail)
                            if (anchoredDraggableState.currentValue == DraggablePaneType.ListMax) {
                                coroutineScope.launch {
                                    anchoredDraggableState.animateTo(
                                        anchoredDraggableState.anchors.closestAnchor(
                                            anchoredDraggableState.anchors.minAnchor(),
                                        )!!
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val width = max(minPaneWidth.roundToPx(), constraints.maxWidth)
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = minPaneWidth.roundToPx(),
                                        maxWidth = width
                                    )
                                )
                                layout(width, placeable.height) {
                                    placeable.placeRelative(max(width - constraints.maxWidth, 0) / 2, 0)
                                }
                            }
                    )
                }
            },
            detailPane = {
                val detailPreferredWidth =
                    constraints.maxWidth -
                            with(density) {
                                listDetailLayoutState.layoutDirective.gutterSizes.outerVertical.toPx() * 2
                            } - anchoredDraggableState.requireOffset()


                Box(
                    modifier = Modifier
                        .preferredWidth(
                            with(density) {
                                detailPreferredWidth
                                    .coerceAtLeast(0.0001f)
                                    .toDp()
                            }
                        )
                        .clipToBounds(),
                ) {
                    ReplyEmailDetail(
                        email = replyHomeUIState.openedEmail ?: replyHomeUIState.emails.first(),
                        isFullScreen = !listDetailLayoutState.isListVisible ||
                                anchoredDraggableState.targetValue == DraggablePaneType.DetailMax,
                        onBackPressed = {
                            if (listDetailLayoutState.canNavigateBack()) {
                                listDetailLayoutState.navigateBack()
                            }
                        },
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val width = max(minPaneWidth.roundToPx(), constraints.maxWidth)
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = minPaneWidth.roundToPx(),
                                        maxWidth = width
                                    )
                                )
                                layout(width, placeable.height) {
                                    placeable.placeRelative(-max(width - constraints.maxWidth, 0) / 2, 0)
                                }
                            }
                    )
                }
            },
            modifier = modifier,
        )


        if (listDetailLayoutState.isListVisible && listDetailLayoutState.isDetailVisible) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .offset(x = -32.dp)
                        .offset {
                            IntOffset(
                                anchoredDraggableState
                                    .requireOffset()
                                    .roundToInt(),
                                0
                            )
                        }
                        .anchoredDraggable(
                            state = anchoredDraggableState,
                            orientation = Orientation.Horizontal,
                            interactionSource = interactionSource,
                        )
                        .systemGestureExclusion()
                ) {
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val isDragged by interactionSource.collectIsDraggedAsState()
                    val isActive = isHovered || isPressed || isDragged

                    val width by animateDpAsState(
                        if (isActive) 12.dp else 4.dp
                    )
                    val color by animateColorAsState(
                        if (isActive) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )

                    Canvas(
                        modifier = Modifier.fillMaxSize()
//                            .offset {
//                                val progress =
//                                    (anchoredDraggableState.requireOffset() -
//                                            anchoredDraggableState.anchors.minAnchor()) /
//                                            (anchoredDraggableState.anchors.maxAnchor() -
//                                                    anchoredDraggableState.anchors.minAnchor())
//
//                                IntOffset(
//                                    -((progress * 2 - 1) * 16.dp.toPx()).roundToInt(),
//                                    0
//                                )
//                            }
                    ) {
                        val height = 48.dp
                        val rectSize = DpSize(width, height).toSize()

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(
                                (size.width - rectSize.width) / 2,
                                (size.height - rectSize.height) / 2,
                            ),
                            size = rectSize,
                            cornerRadius = CornerRadius(rectSize.width / 2f),
                        )
                    }
                }
            }
        }
    }
}

sealed interface DraggablePaneType {
    object ListMax : DraggablePaneType
    object DetailMax : DraggablePaneType
    data class Split(
        val listPreferredWeight: Float
    ) : DraggablePaneType
}


@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3AdaptiveApi::class)
fun calculateCustomAdaptiveLayoutDirective(
    anchoredDraggableState: AnchoredDraggableState<DraggablePaneType>,
    windowAdaptiveInfo: WindowAdaptiveInfo,
): AdaptiveLayoutDirective {
    val maxHorizontalPartitions: Int
    val gutterOuterVertical: Dp
    val gutterInnerVertical: Dp
    when (windowAdaptiveInfo.windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            maxHorizontalPartitions = 1
            gutterOuterVertical = 16.dp
            gutterInnerVertical = 0.dp
        }
        WindowWidthSizeClass.Medium -> {
            maxHorizontalPartitions = 1
            gutterOuterVertical = 24.dp
            gutterInnerVertical = 0.dp
        }
        else -> {
            maxHorizontalPartitions = 2
            gutterOuterVertical = 24.dp

            val distanceFromMinAnchor =
                with(LocalDensity.current) {
                    abs(anchoredDraggableState.anchors.minAnchor() - anchoredDraggableState.requireOffset()).toDp()
                }
            val distanceFromMaxAnchor =
                with(LocalDensity.current) {
                    abs(anchoredDraggableState.anchors.maxAnchor() - anchoredDraggableState.requireOffset()).toDp()
                }
            gutterInnerVertical = min(fullInnerVerticalGutter, min(distanceFromMinAnchor, distanceFromMaxAnchor))
        }
    }
    val maxVerticalPartitions: Int
    val gutterInnerHorizontal: Dp

    // TODO(conradchen): Confirm the table top mode settings
    if (windowAdaptiveInfo.posture.isTabletop) {
        maxVerticalPartitions = 2
        gutterInnerHorizontal = 24.dp
    } else {
        maxVerticalPartitions = 1
        gutterInnerHorizontal = 0.dp
    }

    return AdaptiveLayoutDirective(
        maxHorizontalPartitions,
        GutterSizes(
            gutterOuterVertical, gutterInnerVertical, innerHorizontal = gutterInnerHorizontal
        ),
        maxVerticalPartitions,
        emptyList()
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val ListDetailPaneScaffoldState.isDetailVisible get() =
    layoutValue.primary == PaneAdaptedValue.Expanded

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val ListDetailPaneScaffoldState.isListVisible get() =
    layoutValue.secondary == PaneAdaptedValue.Expanded

@Composable
fun ReplyEmailList(
    emails: List<Email>,
    openedEmail: Email?,
    selectedEmailIds: Set<Long>,
    toggleEmailSelection: (Long) -> Unit,
    emailLazyListState: LazyListState,
    modifier: Modifier = Modifier,
    navigateToDetail: (Long) -> Unit
) {
    Box(modifier = modifier) {
        ReplyDockedSearchBar(
            emails = emails,
            onSearchItemSelected = { searchedEmail ->
                navigateToDetail(searchedEmail.id)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            state = emailLazyListState
        ) {
            items(items = emails, key = { it.id }) { email ->
                ReplyEmailListItem(
                    email = email,
                    navigateToDetail = { emailId ->
                        navigateToDetail(emailId)
                    },
                    toggleSelection = toggleEmailSelection,
                    isOpened = openedEmail?.id == email.id,
                    isSelected = selectedEmailIds.contains(email.id)
                )
            }
        }
    }
}

@Composable
fun ReplyEmailDetail(
    email: Email,
    isFullScreen: Boolean = true,
    modifier: Modifier = Modifier.fillMaxSize(),
    onBackPressed: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.inverseOnSurface)
            .padding(top = 16.dp)
    ) {
        item {
            EmailDetailAppBar(email, isFullScreen) {
                onBackPressed()
            }
        }
        items(items = email.threads, key = { it.id }) { email ->
            ReplyEmailThreadItem(email = email)
        }
    }
}
