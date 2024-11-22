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

package com.example.jetnews.ui.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Horizontal
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.example.jetnews.R
import com.example.jetnews.data.posts.impl.post3
import com.example.jetnews.model.Post
import com.example.jetnews.ui.theme.JetnewsTheme
import com.example.jetnews.ui.utils.BookmarkButton
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun AuthorAndReadTime(
    post: Post,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        Text(
            text = stringResource(
                id = R.string.home_post_min_read,
                formatArgs = arrayOf(
                    post.metadata.author.name,
                    post.metadata.readTimeMinutes
                )
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun PostImage(post: Post, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(post.imageThumbId),
        contentDescription = null, // decorative
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
    )
}

@Composable
fun PostTitle(post: Post) {
    Text(
        text = post.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCardSimple(
    post: Post,
    navigateToArticle: (String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val bookmarkAction = stringResource(if (isFavorite) R.string.unbookmark else R.string.bookmark)

    Layout(
        contents = listOf(
            {
                PostImage(
                    post,
                    Modifier
                        .padding(10.dp)
                        .size(80.dp)
                )
            },
            {
                BookmarkButton(
                    isBookmarked = isFavorite,
                    onClick = onToggleFavorite,
                    // Remove button semantics so action can be handled at row level
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .padding(vertical = 2.dp, horizontal = 6.dp)
                )
            },
            {
                Column(
                    modifier = Modifier
                        .padding(10.dp)
                ) {
                    PostTitle(post)
                    AuthorAndReadTime(post)
                }
            },
        ),
        modifier = Modifier
            .clickable(onClick = { navigateToArticle(post.id) })
            .semantics {
                // By defining a custom action, we tell accessibility services that this whole
                // composable has an action attached to it. The accessibility service can choose
                // how to best communicate this action to the user.
                customActions = listOf(
                    CustomAccessibilityAction(
                        label = bookmarkAction,
                        action = { onToggleFavorite(); true }
                    )
                )
            }
    ) { measurables, constraints ->
        val headerMeasurables = measurables[0]
        val bookmarkMeasurables = measurables[1]
        val infoMeasurables = measurables[2]

        val isHorizontal = constraints.maxWidth >= 320.dp.toPx()

        val width = constraints.maxWidth
        val height: Int

        val headerPlaceables: List<Placeable>
        val bookmarkPlaceables: List<Placeable>
        val infoPlaceables: List<Placeable>

        if (isHorizontal) {
            headerPlaceables = headerMeasurables.map {
                it.measure(constraints)
            }
            bookmarkPlaceables = bookmarkMeasurables.map {
                it.measure(constraints)
            }
            infoPlaceables = infoMeasurables.map {
                it.measure(
                    constraints.offset(
                        horizontal = -headerPlaceables.maxOf { it.width } - bookmarkPlaceables.maxOf { it.width }
                    )
                )
            }
            height = max(
                headerPlaceables.maxOf { it.height },
                max(
                    bookmarkPlaceables.maxOf { it.height },
                    infoPlaceables.maxOf { it.height }
                )
            )
        } else {
            headerPlaceables = headerMeasurables.map {
                it.measure(constraints)
            }
            bookmarkPlaceables = bookmarkMeasurables.map {
                it.measure(constraints)
            }
            infoPlaceables = infoMeasurables.map {
                it.measure(
                    constraints.offset(
                        vertical = -max(
                            headerPlaceables.maxOf { it.height },
                            bookmarkPlaceables.maxOf { it.height }
                        )
                    )
                )
            }
            height = max(
                bookmarkPlaceables.maxOf { it.height },
                headerPlaceables.maxOf { it.height }
            ) + infoPlaceables.maxOf { it.height }
        }

        layout(width, height) {
            if (isHorizontal) {
                headerPlaceables.forEach {
                    it.place(0, 0)
                }
                bookmarkPlaceables.forEach {
                    it.place(width - it.width, 0)
                }
                infoPlaceables.forEach {
                    it.place(headerPlaceables.maxOf { it.width }, 0)
                }
            } else {
                headerPlaceables.forEach {
                    it.place(0, 0)
                }
                bookmarkPlaceables.forEach {
                    it.place(width - it.width, 0)
                }
                infoPlaceables.forEach {
                    it.place(
                        0,
                        max(
                            headerPlaceables.maxOf { it.height },
                            bookmarkPlaceables.maxOf { it.height }
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun PostCardHistory(post: Post, navigateToArticle: (String) -> Unit) {
    var openDialog by remember { mutableStateOf(false) }

    Row(
        Modifier
            .clickable(onClick = { navigateToArticle(post.id) })
    ) {
        PostImage(
            post = post,
            modifier = Modifier.padding(16.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.home_post_based_on_history),
                style = MaterialTheme.typography.labelMedium
            )
            PostTitle(post = post)
            AuthorAndReadTime(
                post = post,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        IconButton(onClick = { openDialog = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_more_actions)
            )
        }
    }
    if (openDialog) {
        AlertDialog(
            modifier = Modifier.padding(20.dp),
            onDismissRequest = { openDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.fewer_stories),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.fewer_stories_content),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Text(
                    text = stringResource(id = R.string.agree),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(15.dp)
                        .clickable { openDialog = false }
                )
            }
        )
    }
}

@Preview("Bookmark Button")
@Composable
fun BookmarkButtonPreview() {
    JetnewsTheme {
        Surface {
            BookmarkButton(isBookmarked = false, onClick = { })
        }
    }
}

@Preview("Bookmark Button Bookmarked")
@Composable
fun BookmarkButtonBookmarkedPreview() {
    JetnewsTheme {
        Surface {
            BookmarkButton(isBookmarked = true, onClick = { })
        }
    }
}

@Preview("Simple post card", widthDp = 300)
@Preview("Simple post card", widthDp = 500)
@Preview("Simple post card", widthDp = 500, fontScale = 1.5f)
@Preview("Simple post card", widthDp = 600)
@Composable
fun SimplePostPreview() {
    JetnewsTheme {
        Surface {
            Box {
                PostCardSimple(post3, {}, false, {})
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

@Preview("Post History card")
@Composable
fun HistoryPostPreview() {
    JetnewsTheme {
        Surface {
            PostCardHistory(post3, {})
        }
    }
}
