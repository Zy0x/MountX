package app.mihon.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mihon.R
import app.mihon.data.model.MountStatus
import app.mihon.ui.theme.StatusError
import app.mihon.ui.theme.StatusErrorContainer
import app.mihon.ui.theme.StatusSuccess
import app.mihon.ui.theme.StatusSuccessContainer

@Composable
fun StatusChip(
    status: MountStatus,
    modifier: Modifier = Modifier
) {
    val (labelRes, icon, containerColor, labelColor) = when (status) {
        MountStatus.MOUNTED -> Quadruple(
            R.string.status_mounted,
            Icons.Default.Check,
            StatusSuccessContainer,
            StatusSuccess
        )
        MountStatus.UNMOUNTED -> Quadruple(
            R.string.status_unmounted,
            Icons.Default.Close,
            Color.LightGray.copy(alpha = 0.3f),
            Color.DarkGray
        )
        MountStatus.ERROR -> Quadruple(
            R.string.status_error,
            Icons.Default.ErrorOutline,
            StatusErrorContainer,
            StatusError
        )
        MountStatus.UNKNOWN -> Quadruple(
            R.string.status_unknown,
            Icons.Default.HelpOutline,
            Color.Transparent,
            Color.Gray
        )
    }

    FilterChip(
        selected = status == MountStatus.MOUNTED,
        onClick = { },
        label = { Text(text = stringResource(labelRes), color = labelColor) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = containerColor,
            containerColor = containerColor
        ),
        modifier = modifier.padding(vertical = 2.dp)
    )
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
