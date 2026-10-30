package com.chatku.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatku.app.data.User

@Composable
fun UserRow(
    user: User,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val wa = LocalWa.current
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.avatarMediaId, user.displayName, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(user.displayName, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = subtitle ?: user.about ?: user.phone
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = wa.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.Unspecified) {
    val wa = LocalWa.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(wa.accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}
