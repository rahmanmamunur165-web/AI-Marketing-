package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.AuthManager
import com.example.ui.auth.AuthScreen
import com.example.ui.campaigns.CampaignsScreen
import com.example.ui.components.SaaSBottomNavigationBar
import com.example.ui.components.SaaSTopAppBar
import com.example.ui.contacts.ContactsScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.scanner.ScannerScreen
import com.example.ui.settings.SettingsScreen
import com.example.viewmodel.ContactFlowViewModel
import com.example.viewmodel.NavigationItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ContactFlowApp(
    viewModel: ContactFlowViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var firebaseUser by remember { mutableStateOf<FirebaseUser?>(AuthManager.currentUser) }

    // Listen to Firebase auth state changes
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            firebaseUser = auth.currentUser
        }
        val authInstance = FirebaseAuth.getInstance()
        authInstance.addAuthStateListener(listener)
        onDispose {
            authInstance.removeAuthStateListener(listener)
        }
    }

    // Try silent auto-sign-in on startup
    LaunchedEffect(Unit) {
        if (firebaseUser == null) {
            AuthManager.attemptAutoSignIn(
                context = context,
                scope = coroutineScope,
                onSuccess = { user -> firebaseUser = user },
                onUnauthenticated = { /* user stays on AuthScreen */ }
            )
        }
    }

    // Auth gate: if unauthenticated, show AuthScreen with interactive Google Sign-In button
    if (firebaseUser == null) {
        AuthScreen(
            onAuthSuccess = { user ->
                firebaseUser = user
                viewModel.showMessage("Welcome, ${user.displayName ?: user.email}!")
            }
        )
        return
    }

    val currentScreen by viewModel.currentScreen.collectAsState()
    val googleConn by viewModel.googleConnection.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect user messages for snackbar
    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Hardware back handler
    BackHandler(enabled = currentScreen != NavigationItem.DASHBOARD) {
        viewModel.navigateTo(NavigationItem.DASHBOARD)
    }

    Scaffold(
        topBar = {
            SaaSTopAppBar(
                connectedEmail = firebaseUser?.email ?: if (googleConn?.isConnected == true) googleConn?.googleAccountEmail else null,
                onConnectClick = { viewModel.navigateTo(NavigationItem.SETTINGS) }
            )
        },
        bottomBar = {
            SaaSBottomNavigationBar(
                currentScreen = currentScreen,
                onNavigate = { viewModel.navigateTo(it) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                NavigationItem.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                NavigationItem.SCANNER -> ScannerScreen(viewModel = viewModel)
                NavigationItem.CONTACTS -> ContactsScreen(viewModel = viewModel)
                NavigationItem.CAMPAIGNS -> CampaignsScreen(viewModel = viewModel)
                NavigationItem.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
