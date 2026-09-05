package com.claude.messages.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.claude.messages.ui.theme.avatarColorFor
import com.claude.messages.util.Formatting

/**
 * Contact photo when there is one, a coloured monogram otherwise. Morphs into a
 * check mark while the row is selected, which reads better than an overlay.
 */
@Composable
fun Avatar(
    name: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    isGroup: Boolean = false,
    selected: Boolean = false,
) {
    val tint = avatarColorFor(name.ifBlank { "?" })
    val background by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            photoUri != null -> Color.Transparent
            else -> tint
        },
        label = "avatarBackground",
    )
    // Selected avatars square off slightly, echoing Material 3 shape morphing.
    val shape = if (selected) RoundedCornerShape(16.dp) else CircleShape

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onPrimary,
            )

            photoUri != null -> AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )

            isGroup -> Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = Color.White,
            )

            else -> Text(
                text = Formatting.initials(name),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value / 2.5f).sp,
            )
        }
    }
}
