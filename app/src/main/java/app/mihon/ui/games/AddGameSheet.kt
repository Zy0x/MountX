package app.mihon.ui.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mihon.R
import app.mihon.data.model.MountMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameSheet(
    installedApps: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onAdd: (packageName: String, displayName: String, mode: MountMode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPackage by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(MountMode.PKG) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = installedApps.filter {
        it.first.contains(searchQuery, ignoreCase = true) ||
        it.second.contains(searchQuery, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.add_game_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Package Name input or selected
            OutlinedTextField(
                value = selectedPackage,
                onValueChange = {
                    selectedPackage = it
                    if (selectedName.isBlank()) selectedName = it
                },
                label = { Text(stringResource(R.string.add_game_package_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Mount Mode Selection
            Text(
                text = stringResource(R.string.add_game_mode_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMode == MountMode.PKG,
                    onClick = { selectedMode = MountMode.PKG },
                    label = { Text("PKG") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedMode == MountMode.FILES,
                    onClick = { selectedMode = MountMode.FILES },
                    label = { Text("FILES") },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (selectedMode == MountMode.PKG)
                    stringResource(R.string.add_game_mode_pkg_desc)
                else
                    stringResource(R.string.add_game_mode_files_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (selectedPackage.isNotBlank()) {
                        onAdd(selectedPackage.trim(), selectedName.trim(), selectedMode)
                    }
                },
                enabled = selectedPackage.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_game_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // App picker list
            Text(
                text = stringResource(R.string.add_game_browse),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.games_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(filteredApps) { (pkg, name) ->
                    ListItem(
                        headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.clickable {
                            selectedPackage = pkg
                            selectedName = name
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
