package com.dialer.truedialer.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.CenterUser
import com.dialer.truedialer.sync.MongoAtlasSyncManager
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmLoginView(
    onLoginSuccess: (CenterUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val dbHelper = remember { EnquiryDatabaseHelper(context) }

    var userList by remember { mutableStateOf(dbHelper.getCenterUsers()) }
    val managerUsers = remember(userList) { userList.filter { it.isManager } }

    var selectedManager by remember { mutableStateOf<CenterUser?>(managerUsers.firstOrNull()) }
    var isManualInputMode by remember { mutableStateOf(false) }

    var manualIdentifier by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var isSyncingMongo by remember { mutableStateOf(false) }

    // When manager users list updates, ensure a selection is kept if null
    LaunchedEffect(managerUsers) {
        if (selectedManager == null && managerUsers.isNotEmpty()) {
            selectedManager = managerUsers.first()
        }
    }

    fun performLogin() {
        loginError = null
        focusManager.clearFocus()
        if (passwordInput.isBlank()) {
            loginError = "Please enter your password or 4-digit PIN."
            return
        }

        isAuthenticating = true
        coroutineScope.launch {
            try {
                val authenticatedUser: CenterUser? = if (isManualInputMode) {
                    dbHelper.authenticateByEmailOrUsername(manualIdentifier, passwordInput)
                } else {
                    val currentSelected = selectedManager
                    if (currentSelected != null && dbHelper.authenticateUser(currentSelected.id, passwordInput)) {
                        currentSelected
                    } else {
                        null
                    }
                }

                if (authenticatedUser != null) {
                    AppConfig.setCurrentUser(
                        context = context,
                        userId = authenticatedUser.id,
                        userName = authenticatedUser.name,
                        role = authenticatedUser.role.name,
                        email = authenticatedUser.email,
                        username = authenticatedUser.username,
                        centerId = authenticatedUser.centerId
                    )
                    Toast.makeText(context, "Welcome back, ${authenticatedUser.name}!", Toast.LENGTH_SHORT).show()
                    onLoginSuccess(authenticatedUser)
                } else {
                    loginError = if (isManualInputMode) {
                        "Invalid username/email or password. Only Managers may log in."
                    } else {
                        "Incorrect password or PIN for ${selectedManager?.name ?: "manager"}. (PIN default: 9999)"
                    }
                }
            } catch (e: Exception) {
                loginError = "Authentication error: ${e.localizedMessage}"
            } finally {
                isAuthenticating = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Shield Icon & Title
            item {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "CRM Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "School of Athens CRM",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Lead Pipeline & Database Enquiries",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Database enquiries and CRM sessions are protected. Please sign in as a Manager to access lead records.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Sync from MongoDB Banner / Button
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Center: ${AppConfig.CENTER_NAME}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "MongoDB Atlas Live Sync",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (!isSyncingMongo) {
                                    isSyncingMongo = true
                                    coroutineScope.launch {
                                        val res = MongoAtlasSyncManager.syncCenterUsers(context)
                                        userList = dbHelper.getCenterUsers()
                                        isSyncingMongo = false
                                        Toast.makeText(
                                            context,
                                            if (res.success) "Profiles synced from MongoDB" else "Sync failed: ${res.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isSyncingMongo) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Login Mode Selector (Quick Select Manager vs Username/Email)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isManualInputMode,
                        onClick = { isManualInputMode = false; loginError = null },
                        label = { Text("Manager Profiles (${managerUsers.size})") },
                        leadingIcon = {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = isManualInputMode,
                        onClick = { isManualInputMode = true; loginError = null },
                        label = { Text("Username / Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Manager Selector List
            if (!isManualInputMode) {
                item {
                    Text(
                        text = "Select your Manager Account:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(managerUsers) { manager ->
                    val isSelected = selectedManager?.id == manager.id
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedManager = manager
                                loginError = null
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else EmeraldGreen.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else EmeraldGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = manager.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${manager.role.displayName} • ${manager.email.ifBlank { manager.username }}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedManager = manager
                                    loginError = null
                                }
                            )
                        }
                    }
                }
            } else {
                // Manual Username/Email input
                item {
                    OutlinedTextField(
                        value = manualIdentifier,
                        onValueChange = { manualIdentifier = it; loginError = null },
                        label = { Text("Manager Username or Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
            }

            // Password / PIN Input
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isManualInputMode) "Enter Credentials" else "Authenticate as ${selectedManager?.name ?: "Manager"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; loginError = null },
                            label = { Text("Password or 4-digit PIN") },
                            placeholder = { Text("Default PIN: 9999") },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { performLogin() }),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = loginError != null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        AnimatedVisibility(visible = loginError != null) {
                            Text(
                                text = loginError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        Button(
                            onClick = { performLogin() },
                            enabled = !isAuthenticating && (isManualInputMode && manualIdentifier.isNotBlank() || !isManualInputMode && selectedManager != null),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Verifying...")
                            } else {
                                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Unlock Leads & CRM Session", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Text(
                            text = "Note: Authorized manager accounts can use their registered MongoDB password or the manager PIN 9999.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
