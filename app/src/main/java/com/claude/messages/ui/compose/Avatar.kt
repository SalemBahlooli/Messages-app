package com.claude.messages.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Contact photo when we have one, a coloured monogram when we do not. */
@Composable
fun Avatar(
    name: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isGroup: Boolean = false,
) {
    val background = avatarColorFor(name.ifBlank { "?" })
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (photoUri != null) Color.Transparent else background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photoUri != null -> AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )

            isGroup -> Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = Color.White,
            )

            else -> Text(
                text = Formatting.initials(name),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value / 2.4f).sp,
            )
        }
    }
}
