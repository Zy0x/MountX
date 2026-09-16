package app.mountify.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountify.R
import app.mountify.data.model.MountStatus
import app.mountify.ui.theme.CyberEmerald
import app.mountify.ui.theme.NeonCrimson

@Composable
fun StatusChip(
    status: MountStatus,
    modifier: Modifier = Modifier
) {
    val (labelRes, icon, containerColor, borderColor, contentColor) = when (status) {
        MountStatus.MOUNTED -> Quintuple(
            R.string.status_mounted,
            Icons.Default.Check,
            CyberEmerald.copy(alpha = 0.14f),
            CyberEmerald.copy(alpha = 0.45f),
            CyberEmerald
        )
        MountStatus.UNMOUNTED -> Quintuple(
            R.string.status_unmounted,
            Icons.Default.Close,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        MountStatus.ERROR -> Quintuple(
            R.string.status_error,
            Icons.Default.ErrorOutline,
            NeonCrimson.copy(alpha = 0.14f),
            NeonCrimson.copy(alpha = 0.45f),
            NeonCrimson
        )
        MountStatus.UNKNOWN -> Quintuple(
            R.string.status_unknown,
            Icons.AutoMirrored.Filled.HelpOutline,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = stringResource(labelRes),
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

