package app.mountx.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mountx.R

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = stringResource(R.string.common_confirm),
    cancelText: String = stringResource(R.string.common_cancel),
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color(0xFF111726),
        shape = RoundedCornerShape(24.dp),
        title = { 
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = androidx.compose.ui.graphics.Color(0xFFF1F5F9)
            ) 
        },
        text = { 
            Text(
                text = message, 
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
            ) 
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(42.dp),
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF6366F1))
                }
            ) {
                Text(text = confirmText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text(
                    text = cancelText, 
                    fontSize = 13.sp, 
                    fontWeight = FontWeight.Normal,
                    color = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                )
            }
        }
    )
}
