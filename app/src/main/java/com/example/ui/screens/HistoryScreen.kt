package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val sessions by viewModel.allSessions.collectAsState()
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Session History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (sessions.isNotEmpty()) {
                Row {
                    IconButton(onClick = {
                        val jsonArray = JSONArray()
                        sessions.forEach { s ->
                            val obj = JSONObject()
                            obj.put("id", s.id)
                            obj.put("startTime", s.startTime)
                            obj.put("durationSeconds", s.durationSeconds)
                            obj.put("reelsViewed", s.reelsViewed)
                            obj.put("averageSecondsPerReel", s.averageSecondsPerReel)
                            jsonArray.put(obj)
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, jsonArray.toString(2))
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Export Sessions"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export JSON")
                    }
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sessions recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(formatter.format(Date(session.startTime)), style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reels Viewed:", fontWeight = FontWeight.Bold)
                                Text("${session.reelsViewed}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Duration:", style = MaterialTheme.typography.bodyMedium)
                                val m = session.durationSeconds / 60
                                val s = session.durationSeconds % 60
                                Text("${m}m ${s}s", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Avg time/reel:", style = MaterialTheme.typography.bodyMedium)
                                Text(String.format(Locale.US, "%.1fs", session.averageSecondsPerReel), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
