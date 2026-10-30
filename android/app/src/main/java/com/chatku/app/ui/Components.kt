@file:OptIn(ExperimentalMaterial3Api::class)

package com.chatku.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.chatku.app.data.Net
import com.chatku.app.util.initials
import kotlin.math.absoluteValue

private val avatarPalette = listOf(
    Color(0xFF00A884), Color(0xFF7E57C2), Color(0xFFEF6C00), Color(0xFF1E88E5),
    Color(0xFFD81B60), Color(0xFF43A047), Color(0xFF6D4C41), Color(0xFF546E7A),
)

fun colorFor(key: String): Color = avatarPalette[key.hashCode().absoluteValue % avatarPalette.size]

@Composable
fun Avatar(mediaId: String?, name: String, size: Dp = 48.dp) {
    val bg = remember(name) { colorFor(name) }
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(initials(name), color = Color.White, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Medium)
        if (mediaId != null) {
            AsyncImage(
                model = Net.mediaUrl(mediaId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
fun waTopBarColors(): TopAppBarColors {
    val wa = LocalWa.current
    return TopAppBarDefaults.topAppBarColors(
        containerColor = wa.topBar,
        titleContentColor = wa.onTopBar,
        navigationIconContentColor = wa.onTopBar,
        actionIconContentColor = wa.onTopBar,
    )
}

/** Centang status pesan: pending ⏱, sent ✓, delivered ✓✓ abu, read ✓✓ biru, failed ❗. */
@Composable
fun Ticks(status: String?, modifier: Modifier = Modifier) {
    val wa = LocalWa.current
    val s = Modifier.size(16.dp).then(modifier)
    when (status) {
        "pending" -> Icon(Icons.Filled.Schedule, null, tint = wa.tickGrey, modifier = Modifier.size(13.dp).then(modifier))
        "failed" -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = s)
        "read" -> Icon(Icons.Filled.DoneAll, null, tint = wa.tickRead, modifier = s)
        "delivered" -> Icon(Icons.Filled.DoneAll, null, tint = wa.tickGrey, modifier = s)
        else -> Icon(Icons.Filled.Check, null, tint = wa.tickGrey, modifier = s)
    }
}
