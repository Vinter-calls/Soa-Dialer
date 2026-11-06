package com.dialer.truedialer.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.CenterUser
import com.dialer.truedialer.data.model.UserRole
import com.dialer.truedialer.sync.MongoAtlasSyncManager
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import kotlinx.coroutines.launch

@Composable
fun UserProfileBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (AppConfig.isCurrentUserManager) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (AppConfig.isCurrentUserManager) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                contentDescription = "User",
                tint = if (AppConfig.isCurrentUserManager) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = AppConfig.CURRENT_USER_NAME,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (AppConfig.isCurrentUserManager) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                Text(
                    text = "${AppConfig.CURRENT_USER_ROLE} • ${AppConfig.CENTER_NAME}",
                    fontSize = 9.sp,
                    color = if (AppConfig.isCurrentUserManager) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Switch",
                tint = if (AppConfig.isCurrentUserManager) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun UserSwitcherDialog(
    onDismiss: () -> Unit,
    onUserSwitched: (CenterUser) -> Unit,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dbHelper = remember { EnquiryDatabaseHelper(context) }
    var userList by remember { mutableStateOf(dbHelper.getCenterUsers()) }

    var selectedUserForLogin by remember { mutableStateOf<CenterUser?>(null) }
    var deniedUser by remember { mutableStateOf<CenterUser?>(null) }
    var showDirectLoginDialog by remember { mutableStateOf(false) }
    var isSyncingMongo by remember { mutableStateOf(false) }

    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    // Only display managers' profiles
    val managerUsers = remember(userList) {
        userList.filter { it.isManager }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Center Manager Profiles", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${AppConfig.CENTER_NAME} • Manager Switcher",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Sync with MongoDB button
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
                                    if (res.success) res.message else "Sync failed: ${res.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    if (isSyncingMongo) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = "Sync from MongoDB", tint = EmeraldGreen)
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = EmeraldGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                        Text(
                            "Only center managers are authorized to log in.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = EmeraldGreen
                        )
                    }
                }

                Text(
                    text = "Select a manager profile (${managerUsers.size}):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 340.dp)
                ) {
                    items(managerUsers) { user ->
                        val isCurrent = user.id == AppConfig.CURRENT_USER_ID

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else if (user.isManager) {
                                    EmeraldGreen.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isCurrent) {
                                        if (user.canLogin) {
                                            selectedUserForLogin = user
                                            passwordInput = ""
                                            passwordVisible = false
                                            loginError = null
                                        } else {
                                            deniedUser = user
                                        }
                                    }
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
                                        .size(42.dp)
                                        .background(
                                            if (user.isManager) EmeraldGreen.copy(alpha = 0.2f) else TrueBlue.copy(alpha = 0.15f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (user.isManager) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (user.isManager) EmeraldGreen else TrueBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = user.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        if (isCurrent) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = EmeraldGreen.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "Active",
                                                    color = EmeraldGreen,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (user.email.isNotBlank()) {
                                        Text(
                                            text = user.email,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Surface(
                                            color = if (user.isManager) EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = user.role.displayName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (user.isManager) EmeraldGreen else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }

                                        if (user.canLogin) {
                                            Text("• Can Log In", fontSize = 10.sp, color = EmeraldGreen)
                                        } else {
                                            Text("• Staff (No Login)", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }

                                if (!isCurrent) {
                                    if (user.canLogin) {
                                        Icon(
                                            Icons.Default.Login,
                                            contentDescription = "Login",
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Restricted",
                                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { showDirectLoginDialog = true }) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Manager Login")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (AppConfig.isLoggedIn) {
                    OutlinedButton(
                        onClick = {
                            AppConfig.logout(context)
                            Toast.makeText(context, "Logged out from CRM.", Toast.LENGTH_SHORT).show()
                            onLogout()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp), tint = CoralRed)
                        Spacer(Modifier.width(4.dp))
                        Text("Log Out", color = CoralRed)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )

    // 1. Password Verification Modal for Selected Manager
    selectedUserForLogin?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { selectedUserForLogin = null },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldGreen) },
            title = { Text("Manager Login") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Authenticating ${targetUser.name}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${targetUser.email.ifBlank { targetUser.username }} • Role: ${targetUser.role.displayName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Mobile Unlock Option (Fingerprint / Face / Screen Lock Pattern / PIN)
                    val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                    if (fragmentActivity != null) {
                        Button(
                            onClick = {
                                com.dialer.truedialer.util.DeviceUnlockHelper.promptDeviceUnlock(
                                    activity = fragmentActivity,
                                    title = "Unlock for ${targetUser.name}",
                                    subtitle = "Use your Fingerprint, Face, or Phone Screen Lock",
                                    onSuccess = {
                                        AppConfig.setCurrentUser(
                                            context = context,
                                            userId = targetUser.id,
                                            userName = targetUser.name,
                                            role = targetUser.role.name,
                                            email = targetUser.email,
                                            username = targetUser.username,
                                            centerId = targetUser.centerId
                                        )
                                        Toast.makeText(context, "Unlocked! Welcome, ${targetUser.name}.", Toast.LENGTH_SHORT).show()
                                        onUserSwitched(targetUser)
                                        selectedUserForLogin = null
                                        onDismiss()
                                    },
                                    onError = { err ->
                                        loginError = err
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock with Mobile Lock", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                "  OR PASSWORD  ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("MongoDB Account Password") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        isError = loginError != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    loginError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Text(
                        "Verified against MongoDB password hash. Only users with Role Manager can log in.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = dbHelper.authenticateUser(targetUser.id, passwordInput)
                        if (ok) {
                            AppConfig.setCurrentUser(
                                context = context,
                                userId = targetUser.id,
                                userName = targetUser.name,
                                role = targetUser.role.name,
                                email = targetUser.email,
                                username = targetUser.username,
                                centerId = targetUser.centerId
                            )
                            Toast.makeText(context, "Welcome, ${targetUser.name}! Manager logged in.", Toast.LENGTH_SHORT).show()
                            onUserSwitched(targetUser)
                            selectedUserForLogin = null
                            onDismiss()
                        } else {
                            loginError = "Incorrect password. Please verify and try again."
                        }
                    }
                ) {
                    Text("Verify & Log In")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedUserForLogin = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Access Denied Modal when Non-Manager Profile is Tapped
    deniedUser?.let { staffUser ->
        AlertDialog(
            onDismissRequest = { deniedUser = null },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CoralRed) },
            title = { Text("Manager Login Only") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${staffUser.name} is registered as ${staffUser.role.displayName}.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Under company security policy, only users with the Manager role can log in to this center device. Faculty and staff accounts have view-only access in the directory.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { deniedUser = null }) {
                    Text("Understood")
                }
            }
        )
    }

    // 3. Direct Login Dialog (Email or Username + Password)
    if (showDirectLoginDialog) {
        var directIdentifier by remember { mutableStateOf("") }
        var directPassword by remember { mutableStateOf("") }
        var directPasswordVisible by remember { mutableStateOf(false) }
        var directError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showDirectLoginDialog = false },
            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = EmeraldGreen) },
            title = { Text("Manager Login") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Enter your Manager credentials (User ID, Username or Email):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = directIdentifier,
                        onValueChange = { directIdentifier = it },
                        label = { Text("User ID / Username / Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = directPassword,
                        onValueChange = { directPassword = it },
                        label = { Text("Password") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (directPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { directPasswordVisible = !directPasswordVisible }) {
                                Icon(
                                    imageVector = if (directPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        isError = directError != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                    if (fragmentActivity != null) {
                        OutlinedButton(
                            onClick = {
                                val cleanId = directIdentifier.trim()
                                val user = if (cleanId.isNotBlank()) {
                                    userList.find {
                                        it.email.equals(cleanId, ignoreCase = true) ||
                                        it.username.equals(cleanId, ignoreCase = true) ||
                                        it.id.equals(cleanId, ignoreCase = true)
                                    }
                                } else {
                                    userList.firstOrNull { it.isManager }
                                }

                                if (user == null || !user.isManager) {
                                    directError = "Please enter a valid Manager User ID or Email first."
                                    return@OutlinedButton
                                }

                                com.dialer.truedialer.util.DeviceUnlockHelper.promptDeviceUnlock(
                                    activity = fragmentActivity,
                                    title = "Unlock for ${user.name}",
                                    subtitle = "Use your Fingerprint, Face, or Phone Screen Lock",
                                    onSuccess = {
                                        AppConfig.setCurrentUser(
                                            context = context,
                                            userId = user.id,
                                            userName = user.name,
                                            role = user.role.name,
                                            email = user.email,
                                            username = user.username,
                                            centerId = user.centerId
                                        )
                                        Toast.makeText(context, "Unlocked! Welcome, ${user.name}.", Toast.LENGTH_SHORT).show()
                                        onUserSwitched(user)
                                        showDirectLoginDialog = false
                                        onDismiss()
                                    },
                                    onError = { err ->
                                        directError = err
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp), tint = EmeraldGreen)
                            Spacer(Modifier.width(6.dp))
                            Text("Or Unlock with Mobile Lock", color = EmeraldGreen)
                        }
                    }

                    directError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val user = dbHelper.authenticateByEmailOrUsername(directIdentifier, directPassword)
                        if (user != null) {
                            AppConfig.setCurrentUser(
                                context = context,
                                userId = user.id,
                                userName = user.name,
                                role = user.role.name,
                                email = user.email,
                                username = user.username,
                                centerId = user.centerId
                            )
                            Toast.makeText(context, "Welcome, ${user.name}! Logged in successfully.", Toast.LENGTH_SHORT).show()
                            onUserSwitched(user)
                            showDirectLoginDialog = false
                            onDismiss()
                        } else {
                            directError = "Invalid credentials or account is not a Manager."
                        }
                    }
                ) {
                    Text("Log In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectLoginDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
