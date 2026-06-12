package com.vitpool.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseUser
import com.vitpool.app.data.AuthRepository
import com.vitpool.app.data.CampusOption
import com.vitpool.app.data.ChatMessage
import com.vitpool.app.data.PlusCodeDecodeResult
import com.vitpool.app.data.GenderOption
import com.vitpool.app.data.MatchSuggestion
import com.vitpool.app.data.ProfileDraft
import com.vitpool.app.data.RiderSummary
import com.vitpool.app.data.RidePool
import com.vitpool.app.data.TripDirection
import com.vitpool.app.data.UserProfile
import com.vitpool.app.data.VitPoolRepository
import com.vitpool.app.data.decodePlusCodeInput
import com.vitpool.app.data.memberDisplayNames
import com.vitpool.app.data.toProfileDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val BACKGROUND_MATCH_REFRESH_MILLIS = 20_000L

@Composable
fun VitPoolApp(
    authRepository: AuthRepository = remember { AuthRepository() },
) {
    val user by authRepository.authState().collectAsStateWithLifecycle(initialValue = authRepository.currentUser)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun launchWithSnackbar(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: Throwable) {
                snackbarHostState.showSnackbar(error.message ?: "Something went wrong")
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                user == null -> AuthScreen(
                    onSignIn = { email, password ->
                        launchWithSnackbar { authRepository.signIn(email, password) }
                    },
                    onRegister = { email, password ->
                        launchWithSnackbar { authRepository.register(email, password) }
                    },
                )
                user?.isEmailVerified != true -> VerifyEmailScreen(
                    user = user,
                    onRefresh = { launchWithSnackbar { authRepository.refreshUser() } },
                    onResend = { launchWithSnackbar { authRepository.resendVerification() } },
                    onSignOut = authRepository::signOut,
                )
                else -> LiveApp(
                    user = user!!,
                    authRepository = authRepository,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("VITPool", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Real ride pooling for verified VIT students.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("VIT email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (registering) onRegister(email, password) else onSignIn(email, password)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (registering) "Create account" else "Sign in")
        }
        TextButton(onClick = { registering = !registering }) {
            Text(if (registering) "I already have an account" else "Create a verified VIT account")
        }
    }
}

@Composable
private fun VerifyEmailScreen(
    user: FirebaseUser?,
    onRefresh: () -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Verify your email", style = MaterialTheme.typography.headlineSmall)
        Text(
            "We sent a verification link to ${user?.email.orEmpty()}. Open it, then refresh here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh verification") }
        OutlinedButton(onClick = onResend, modifier = Modifier.fillMaxWidth()) { Text("Resend email") }
        TextButton(onClick = onSignOut) { Text("Use another account") }
    }
}

@Composable
private fun LiveApp(
    user: FirebaseUser,
    authRepository: AuthRepository,
    snackbarHostState: SnackbarHostState,
) {
    val repository = remember(user.uid) { VitPoolRepository() }
    val profile by repository.profile().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        repository.notices.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(user.uid) {
        runCatching { repository.registerFcmToken() }
    }

    if (profile == null) {
        ProfileFormScreen(
            initialEmail = user.email.orEmpty(),
            title = "Complete profile",
            initialDraft = ProfileDraft(),
            saveLabel = "Save profile",
            onSave = { draft ->
                scope.launch {
                    try {
                        repository.completeProfile(draft)
                    } catch (error: Throwable) {
                        snackbarHostState.showSnackbar(error.message ?: "Profile could not be saved")
                    }
                }
            },
            secondaryActionLabel = "Sign out",
            onSecondaryAction = authRepository::signOut,
        )
        return
    }

    MainShell(
        profile = profile!!,
        repository = repository,
        onSignOut = authRepository::signOut,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun MainShell(
    profile: UserProfile,
    repository: VitPoolRepository,
    onSignOut: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val suggestion by repository.matchSuggestion().collectAsStateWithLifecycle(initialValue = MatchSuggestion())
    val activePool by repository.activePool().collectAsStateWithLifecycle(initialValue = null)
    val activePoolMembersFlow = remember(repository, activePool?.memberIds) {
        activePool?.memberIds?.let(repository::poolMemberSummaries) ?: flowOf(emptyList<RiderSummary>())
    }
    val activePoolMembers by activePoolMembersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var editingProfile by remember(profile.uid) { mutableStateOf(false) }

    LaunchedEffect(repository) {
        while (true) {
            runCatching { repository.refreshMatches() }
            delay(BACKGROUND_MATCH_REFRESH_MILLIS)
        }
    }

    fun launchAction(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: Throwable) {
                snackbarHostState.showSnackbar(error.message ?: "Action failed")
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(profile = profile, onSignOut = onSignOut)
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    "Ride" to Icons.Default.DirectionsCar,
                    "Matches" to Icons.Default.Groups,
                    "Chat" to Icons.Default.Chat,
                    "Profile" to Icons.Default.Person,
                ).forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.second, contentDescription = item.first) },
                        label = { Text(item.first) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            when (selectedTab) {
                0 -> RideScreen(profile = profile, suggestion = suggestion, onRequestRide = { direction ->
                    launchAction {
                        repository.requestRide(direction)
                        selectedTab = 1
                    }
                })
                1 -> MatchesScreen(
                    suggestion = suggestion,
                    activePool = activePool,
                    activePoolMembers = activePoolMembers,
                    onRefresh = { launchAction { repository.refreshMatches() } },
                    onConfirmSuggestedPool = {
                        launchAction { repository.confirmPool(suggestion.suggestedPoolUserIds.filter { it != profile.uid }) }
                    },
                    onConfirmPending = { launchAction { repository.confirmPendingPool() } },
                    onReject = { launchAction { repository.rejectPool() } },
                    onLeave = { launchAction { repository.leavePool() } },
                    onCancel = { launchAction { repository.cancelPool() } },
                )
                2 -> ChatScreen(profile = profile, suggestion = suggestion, repository = repository, onAction = ::launchAction)
                else -> {
                    if (editingProfile) {
                        ProfileFormScreen(
                            initialEmail = profile.email,
                            title = "Edit profile",
                            initialDraft = profile.toProfileDraft(),
                            saveLabel = "Update profile",
                            onSave = { draft ->
                                launchAction {
                                    repository.completeProfile(draft)
                                    runCatching { repository.refreshMatches() }
                                    editingProfile = false
                                }
                            },
                            secondaryActionLabel = "Cancel",
                            onSecondaryAction = { editingProfile = false },
                        )
                    } else {
                        ProfileSummaryScreen(
                            profile = profile,
                            onEdit = { editingProfile = true },
                            onSignOut = onSignOut,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(profile: UserProfile, onSignOut: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text("VITPool")
                Text(profile.name, style = MaterialTheme.typography.labelMedium)
            }
        },
        actions = {
            TextButton(onClick = onSignOut) { Text("Sign out") }
        },
    )
}

@Composable
private fun ProfileFormScreen(
    initialEmail: String,
    title: String,
    initialDraft: ProfileDraft,
    saveLabel: String,
    onSave: (ProfileDraft) -> Unit,
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit,
) {
    var draft by remember(initialDraft) { mutableStateOf(initialDraft) }
    var plusCodeError by remember(initialDraft.plusCode) { mutableStateOf<String?>(null) }

    fun updatePlusCode(value: String) {
        if (value.isBlank()) {
            plusCodeError = null
            draft = draft.copy(plusCode = value)
            return
        }

        when (val result = decodePlusCodeInput(value)) {
            is PlusCodeDecodeResult.Success -> {
                plusCodeError = null
                draft = draft.copy(
                    plusCode = result.decoded.normalizedCode,
                    latitude = result.decoded.latitude,
                    longitude = result.decoded.longitude,
                )
            }
            is PlusCodeDecodeResult.Error -> {
                plusCodeError = result.message
                draft = draft.copy(plusCode = value)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(if (title == "Complete profile") Modifier.padding(16.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(initialEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SimpleField("Name", draft.name) { draft = draft.copy(name = it) } }
        item { SimpleField("Branch", draft.branch) { draft = draft.copy(branch = it) } }
        item { SimpleField("Division", draft.division) { draft = draft.copy(division = it) } }
        item { EnumDropdown("Campus", draft.campus, CampusOption.entries) { draft = draft.copy(campus = it) } }
        item { IntDropdown("Batch", draft.batch, listOf(1, 2, 3)) { draft = draft.copy(batch = it) } }
        item { NumberField("Year", draft.year) { draft = draft.copy(year = it.coerceIn(1, 5)) } }
        item { EnumDropdown("Gender", draft.gender, listOf(GenderOption.Male, GenderOption.Female)) { draft = draft.copy(gender = it) } }
        item { EnumDropdown("Gender preference", draft.genderPreference, GenderOption.entries) { draft = draft.copy(genderPreference = it) } }
        item { SimpleField("Stay/home label", draft.stayHomeLabel) { draft = draft.copy(stayHomeLabel = it) } }
        item {
            SimpleField("Plus code", draft.plusCode, ::updatePlusCode)
            Text(
                plusCodeError ?: "Latitude and longitude auto-fill from a full Plus Code.",
                color = if (plusCodeError == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        item { ReadOnlyField("Latitude", formatCoordinate(draft.latitude)) }
        item { ReadOnlyField("Longitude", formatCoordinate(draft.longitude)) }
        item { SimpleField("Notes", draft.notes) { draft = draft.copy(notes = it) } }
        item {
            Button(
                onClick = { onSave(draft) },
                enabled = plusCodeError == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(saveLabel)
            }
            TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
        }
    }
}

@Composable
private fun RideScreen(
    profile: UserProfile,
    suggestion: MatchSuggestion,
    onRequestRide: (TripDirection) -> Unit,
) {
    var direction by remember { mutableStateOf(TripDirection.CollegeToStay) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Request ride", style = MaterialTheme.typography.headlineSmall)
        EnumDropdown("Trip direction", direction, TripDirection.entries) { direction = it }
        Button(onClick = { onRequestRide(direction) }, modifier = Modifier.fillMaxWidth()) {
            Text("Request live matching")
        }
        InfoCard("Nearby riders", nearbyRiderNames(suggestion))
        InfoCard("Suggested pool", suggestedPoolNames(suggestion, profile))
    }
}

@Composable
private fun MatchesScreen(
    suggestion: MatchSuggestion,
    activePool: RidePool?,
    activePoolMembers: List<RiderSummary>,
    onRefresh: () -> Unit,
    onConfirmSuggestedPool: () -> Unit,
    onConfirmPending: () -> Unit,
    onReject: () -> Unit,
    onLeave: () -> Unit,
    onCancel: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Matches", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                Button(
                    onClick = onConfirmSuggestedPool,
                    enabled = suggestion.suggestedPoolUserIds.size >= 2,
                ) { Text("Confirm suggested pool") }
            }
        }
        if (activePool != null) {
            item {
                PoolCard(activePool, activePoolMembers, onConfirmPending, onReject, onLeave, onCancel)
            }
        }
        items(suggestion.candidates) { candidate ->
            InfoCard(
                title = candidate.user.name,
                body = "${candidate.user.branch}-${candidate.user.division} . ${candidate.proximityLabel} . ${candidate.departureGapMinutes} min gap",
            )
        }
    }
}

@Composable
private fun ChatScreen(
    profile: UserProfile,
    suggestion: MatchSuggestion,
    repository: VitPoolRepository,
    onAction: (suspend () -> Unit) -> Unit,
) {
    val peers = suggestion.candidates.map { it.user }
    var selectedPeerUid by remember(peers) { mutableStateOf(peers.firstOrNull()?.uid.orEmpty()) }
    val selectedPeer = peers.firstOrNull { it.uid == selectedPeerUid }
    val chatId = remember(profile.uid, selectedPeerUid) {
        if (selectedPeerUid.isBlank()) "" else listOf(profile.uid, selectedPeerUid).sorted().joinToString(separator = "_", prefix = "direct_")
    }
    var content by remember { mutableStateOf("") }
    var chatReady by remember(chatId) { mutableStateOf(chatId.isBlank()) }
    var chatSetupError by remember(chatId) { mutableStateOf<String?>(null) }
    val messageFlow = remember(chatId, chatReady) {
        if (chatId.isBlank() || !chatReady) flowOf(emptyList<ChatMessage>()) else repository.messages(chatId)
    }
    val messages by messageFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(chatId, profile.uid, selectedPeerUid) {
        if (chatId.isBlank()) {
            chatReady = true
            chatSetupError = null
            return@LaunchedEffect
        }

        chatReady = false
        chatSetupError = null
        runCatching {
            repository.ensureChat(chatId, listOf(profile.uid, selectedPeerUid))
        }.onSuccess {
            chatReady = true
        }.onFailure { error ->
            chatSetupError = error.message ?: "Chat could not be opened."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Chat", style = MaterialTheme.typography.headlineSmall)
        if (peers.isEmpty()) {
            Text("Request a ride and find matches before starting a direct chat.")
            return@Column
        }
        EnumLikeDropdown(
            label = "Peer",
            selectedLabel = selectedPeer?.name ?: "Choose rider",
            options = peers.map { it.uid to it.name },
            onSelected = { selectedPeerUid = it },
        )
        if (!chatReady || chatSetupError != null) {
            Text(
                chatSetupError ?: "Opening secure chat...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                InfoCard(message.senderName, "${formatMillis(message.createdMillis)}\n${message.content}")
            }
        }
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val text = content
                content = ""
                onAction { repository.sendMessage(chatId, text, listOf(profile.uid, selectedPeerUid)) }
            },
            enabled = content.isNotBlank() && selectedPeerUid.isNotBlank() && chatReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send")
        }
    }
}

@Composable
private fun ProfileSummaryScreen(profile: UserProfile, onEdit: () -> Unit, onSignOut: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)
        InfoCard("Email", profile.email)
        InfoCard("Campus", profile.campus.label)
        InfoCard("Stay/home", "${profile.stayHomeLabel}\n${profile.plusCode}")
        InfoCard("Coordinates", "${formatCoordinate(profile.latitude)}, ${formatCoordinate(profile.longitude)}")
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("Edit profile")
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out")
        }
    }
}

@Composable
private fun PoolCard(
    pool: RidePool,
    members: List<RiderSummary>,
    onConfirmPending: () -> Unit,
    onReject: () -> Unit,
    onLeave: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Active pool: ${pool.status}", style = MaterialTheme.typography.titleMedium)
            Text("Members: ${pool.memberDisplayNames(members).joinToString()}")
            Text("Confirmed: ${pool.confirmedMemberIds.size}; pending: ${pool.pendingMemberIds.size}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onConfirmPending, modifier = Modifier.weight(1f)) { Text("Confirm") }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("Reject") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onLeave, modifier = Modifier.weight(1f)) { Text("Leave") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body.isNotBlank()) {
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SimpleField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onChange(it.toIntOrNull() ?: value) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun IntDropdown(label: String, value: Int, options: List<Int>, onSelected: (Int) -> Unit) {
    EnumLikeDropdown(
        label = label,
        selectedLabel = value.toString(),
        options = options.map { it.toString() to it.toString() },
        onSelected = { selected -> selected.toIntOrNull()?.let(onSelected) },
    )
}

@Composable
private fun DecimalField(label: String, value: Double, onChange: (Double) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onChange(it.toDoubleOrNull() ?: value) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(label: String, value: T, options: List<T>, onSelected: (T) -> Unit) {
    val selectedLabel = when (value) {
        is TripDirection -> value.label
        is CampusOption -> value.label
        is GenderOption -> value.label
        else -> value.toString()
    }
    val mapped = options.map {
        it to when (it) {
            is TripDirection -> it.label
            is CampusOption -> it.label
            is GenderOption -> it.label
            else -> it.toString()
        }
    }
    EnumLikeDropdown(label, selectedLabel, mapped.map { it.first.hashCode().toString() to it.second }) { selectedKey ->
        mapped.firstOrNull { it.first.hashCode().toString() == selectedKey }?.first?.let(onSelected)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumLikeDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        expanded = false
                        onSelected(option.first)
                    },
                )
            }
        }
    }
}

private fun formatMillis(value: Long): String {
    if (value <= 0) return "Syncing"
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(value))
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private fun nearbyRiderNames(suggestion: MatchSuggestion): String {
    return suggestion.candidates
        .map { it.user.name.trim().ifBlank { "Rider" } }
        .distinct()
        .joinToString()
        .ifBlank { "No compatible riders nearby yet" }
}

private fun suggestedPoolNames(suggestion: MatchSuggestion, profile: UserProfile): String {
    val candidateNamesByUid = suggestion.candidates.associate { candidate ->
        candidate.user.uid to candidate.user.name.trim().ifBlank { "Rider" }
    }
    return suggestion.suggestedPoolUserIds
        .mapNotNull { uid ->
            when (uid) {
                profile.uid -> profile.name.trim().ifBlank { "You" }
                else -> candidateNamesByUid[uid]
            }
        }
        .distinct()
        .joinToString()
        .ifBlank { "No suggested pool yet" }
}
