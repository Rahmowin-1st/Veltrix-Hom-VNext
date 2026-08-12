package com.veltrix.hom.vnext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veltrix.hom.vnext.core.CapabilityRoute
import com.veltrix.hom.vnext.core.PrimaryDestination

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncScheduler.ensure(applicationContext)
        setContent { MaterialTheme { DeveloperShell() } }
    }
}

@Composable
private fun DeveloperShell(vm: AppViewModel = viewModel()) {
    var destination by remember { mutableStateOf(PrimaryDestination.HOME) }
    var capability by remember { mutableStateOf<CapabilityRoute?>(null) }
    val projects by vm.projects.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(PrimaryDestination.HOME, PrimaryDestination.PERSONAL, PrimaryDestination.STORE, PrimaryDestination.PROJECTS).forEach { dest ->
                    NavigationBarItem(
                        selected = destination == dest && capability == null,
                        onClick = { destination = dest; capability = null },
                        icon = { Text(dest.name.take(1)) },
                        label = { Text(dest.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Veltrix Hom vNext — Part 1 developer harness", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { capability = CapabilityRoute.CHAT }) { Text("Capabilities") }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            if (capability != null) CapabilityScreen(capability!!, onSelect = { capability = it })
            else when (destination) {
                PrimaryDestination.HOME -> HomeDeveloperScreen(projects.size)
                PrimaryDestination.PERSONAL -> Placeholder("Personal backend contract shell")
                PrimaryDestination.STORE -> Placeholder("Store: NOT_AVAILABLE / COMING_IN_PART_2 — no fake economy")
                PrimaryDestination.PROJECTS -> ProjectsDeveloperScreen(projects, vm::createProject)
            }
        }
    }
}

@Composable
private fun HomeDeveloperScreen(projectCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("HomeSnapshot harness", style = MaterialTheme.typography.headlineSmall)
        Text("Cached local projects: $projectCount")
        Text("Final Home layout, Liquid Glass, BG Elm/BG Svet and game visuals are intentionally not implemented in Part 1.")
    }
}

@Composable
private fun ProjectsDeveloperScreen(projects: List<LocalProjectEntity>, create: (String, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Text("Projects — functional local persistence harness", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { create(title, purpose); title = ""; purpose = "" }, modifier = Modifier.padding(vertical = 8.dp)) { Text("Create Project") }
        LazyColumn(Modifier.weight(1f)) {
            items(projects, key = { it.id }) { p ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium)
                    Text(p.purpose ?: "No purpose")
                    Text("${p.status} · sync=${p.syncState}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CapabilityScreen(current: CapabilityRoute, onSelect: (CapabilityRoute) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(0.42f)) {
            items(CapabilityRoute.entries) { item ->
                Button(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(item.name) }
            }
        }
        Spacer(Modifier.padding(8.dp))
        Column(Modifier.weight(0.58f)) {
            Text(current.name, style = MaterialTheme.typography.headlineSmall)
            Text("Route contract reachable. Feature business logic belongs to repositories/domain services, not this composable.")
        }
    }
}

@Composable
private fun Placeholder(text: String) { Text(text, style = MaterialTheme.typography.bodyLarge) }
