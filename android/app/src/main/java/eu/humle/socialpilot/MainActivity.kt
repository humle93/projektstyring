package eu.humle.socialpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF6366F1), secondary = Color(0xFF10B981))) {
                val state by vm.state.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize(), color = Color(0xFF020617)) {
                    if (state.loggedIn) MainScreen(state, vm) else LoginScreen(state, vm)
                }
            }
        }
    }

    override fun onResume() { super.onResume(); if (vm.state.value.loggedIn) vm.refresh() }
}

private fun parseColor(hex: String) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF6366F1))

@Composable
fun LoginScreen(state: UiState, vm: MainViewModel) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("SocialPilot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(if (state.needsSetup) "Første gang: opret din konto" else "Log ind på din projektstyrings-server",
            color = Color.Gray, modifier = Modifier.padding(bottom = 24.dp))
        OutlinedTextField(state.serverUrl, vm::setServer, label = { Text("Server-URL") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { vm.checkSetup() }) { Icon(Icons.Default.Refresh, "Test forbindelse") } })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(user, { user = it }, label = { Text("Brugernavn") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pass, { pass = it }, label = { Text("Adgangskode") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = Color(0xFFF87171), modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = { vm.login(user.trim(), pass) }, enabled = !state.loading && user.length >= 3 && pass.length >= 8,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (state.needsSetup) "Opret konto" else "Log ind")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: UiState, vm: MainViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active) { while (state.active != null) { now = System.currentTimeMillis(); delay(1000) } }
    var newTask by remember { mutableStateOf("") }
    var showNewProject by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = { Text("SocialPilot") },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Opdater") }
                    IconButton(onClick = vm::logout) { Icon(Icons.AutoMirrored.Filled.Logout, "Log ud") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        bottomBar = { state.active?.let { TimerBar(it, vm.elapsedSeconds(it, now), vm::stopTimer) } },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewProject = true }) { Icon(Icons.Default.Add, "Nyt projekt") }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.error?.let { item { Text(it, color = Color(0xFFF87171)) } }
            state.summary?.let { s ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("I dag", MainViewModel.fmtHm(s.todaySeconds), Modifier.weight(1f))
                        Stat("Uge", MainViewModel.fmtHm(s.weekSeconds), Modifier.weight(1f))
                        Stat("Åbne", s.openTasks.toString(), Modifier.weight(1f))
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.projects, key = { it.id }) { p ->
                        FilterChip(selected = state.selected?.id == p.id, onClick = { vm.select(p) },
                            label = { Text("${p.name} · ${MainViewModel.fmtHm(p.totalSeconds)}") },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(parseColor(p.color))) })
                    }
                }
            }
            if (state.projects.isEmpty() && !state.loading) item { Text("Ingen projekter endnu — tryk + for at oprette et.", color = Color.Gray) }
            state.selected?.let { p ->
                item {
                    val running = state.active?.projectId == p.id
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Button(onClick = { vm.toggleTimer(p.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFFE11D48) else Color(0xFF059669))) {
                            Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                            Text(if (running) "Stop" else "Start")
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newTask, { newTask = it }, placeholder = { Text("Ny opgave…") }, singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (newTask.isNotBlank()) { vm.addTask(newTask.trim()); newTask = "" } }) { Icon(Icons.Default.Add, "Tilføj") }
                    }
                }
                items(state.tasks, key = { it.id }) { t -> TaskRow(t, onAdvance = { vm.advanceTask(t) }, onTimer = { vm.toggleTimer(p.id, t.id) }) }
            }
        }
    }

    if (showNewProject) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            title = { Text("Nyt projekt") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Navn") }) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.addProject(name.trim()); showNewProject = false }) { Text("Opret") } },
            dismissButton = { TextButton(onClick = { showNewProject = false }) { Text("Annuller") } }
        )
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TaskRow(t: Task, onAdvance: () -> Unit, onTimer: () -> Unit) {
    val (icon, tint) = when (t.status) {
        "done" -> Icons.Default.CheckCircle to Color(0xFF10B981)
        "doing" -> Icons.Default.Pending to Color(0xFFF59E0B)
        else -> Icons.Default.RadioButtonUnchecked to Color.Gray
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onAdvance).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, "Skift status", tint = tint)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, textDecoration = if (t.status == "done") TextDecoration.LineThrough else null,
                    color = if (t.status == "done") Color.Gray else Color.Unspecified)
                t.dueDate?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
            }
            if (t.status != "done") IconButton(onClick = onTimer) { Icon(Icons.Default.PlayArrow, "Start timer", tint = Color(0xFF10B981)) }
        }
    }
}

@Composable
fun TimerBar(a: ActiveTimer, seconds: Long, onStop: () -> Unit) {
    Surface(color = Color(0xFF064E3B), tonalElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.projectName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                a.taskTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA7F3D0), maxLines = 1) }
            }
            Text(MainViewModel.fmt(seconds), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onStop) { Icon(Icons.Default.Stop, "Stop timer") }
        }
    }
}
