package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val overlayEnabled by viewModel.settingsManager.overlayEnabledFlow.collectAsState(initial = false)
    val retryDelay by viewModel.settingsManager.retryDelayFlow.collectAsState(initial = 500L)
    val confirmationDelay by viewModel.settingsManager.confirmationDelayFlow.collectAsState(initial = 1500L)
    
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Floating Overlay", fontWeight = FontWeight.Bold)
                    Text("Show live count while on Instagram", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = { scope.launch { viewModel.settingsManager.setOverlayEnabled(it) } }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Retry Delay (${retryDelay}ms)", fontWeight = FontWeight.Bold)
                Text("Delay before retrying failed reads", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = retryDelay.toFloat(),
                    onValueChange = { scope.launch { viewModel.settingsManager.setRetryDelay(it.toLong()) } },
                    valueRange = 100f..2000f,
                    steps = 18
                )
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Confirmation Delay (${confirmationDelay}ms)", fontWeight = FontWeight.Bold)
                Text("Time spent on reel before confirming", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = confirmationDelay.toFloat(),
                    onValueChange = { scope.launch { viewModel.settingsManager.setConfirmationDelay(it.toLong()) } },
                    valueRange = 500f..5000f,
                    steps = 44
                )
            }
        }
    }
}
