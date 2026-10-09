package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.campaigns.CampaignsScreen
import com.example.ui.components.SaaSBottomNavigationBar
import com.example.ui.components.SaaSTopAppBar
import com.example.ui.contacts.ContactsScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.scanner.ScannerScreen
import com.example.ui.settings.SettingsScreen
import com.example.viewmodel.ContactFlowViewModel
import com.example.viewmodel.NavigationItem
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ContactFlowApp(
    viewModel: ContactFlowViewModel = viewModel()
) {
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
                connectedEmail = if (googleConn?.isConnected == true) googleConn?.googleAccountEmail else null,
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
