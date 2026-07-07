package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel, 
    onPrivacyPolicyClick: () -> Unit = {},
    onDisclosureClick: () -> Unit = {}
) {
    val overlayEnabled by viewModel.settingsManager.overlayEnabledFlow.collectAsState(initial = false)
    val retryDelay by viewModel.settingsManager.retryDelayFlow.collectAsState(initial = 500L)
    val confirmationDelay by viewModel.settingsManager.confirmationDelayFlow.collectAsState(initial = 1500L)
    
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

        Text("Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDisclosureClick() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accessibility Disclosure", fontWeight = FontWeight.Medium)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "View Disclosure")
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPrivacyPolicyClick() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Privacy Policy", fontWeight = FontWeight.Medium)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "View Privacy Policy")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
