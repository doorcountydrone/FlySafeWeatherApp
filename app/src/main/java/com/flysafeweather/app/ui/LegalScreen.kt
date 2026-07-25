package com.flysafeweather.app.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            CenterAlignedTopAppBar(
                title = { Text("Legal Information") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Privacy Policy") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Terms of Service") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Disclaimers") }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    loadUrl("file:///android_asset/privacy_policy.html")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    1 -> {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    loadUrl("file:///android_asset/terms_of_service.html")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    2 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Important Disclaimers",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            DisclaimerCard(
                                title = "Not an Official Aviation Tool",
                                description = "FlySafe Weather is a supplementary tool for drone pilots. It should not be used as the sole source for flight planning or decision-making."
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            DisclaimerCard(
                                title = "Weather Data Accuracy",
                                description = "Weather conditions can change rapidly and may vary from displayed data. Always verify conditions through official sources before flight."
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            DisclaimerCard(
                                title = "Flight Safety",
                                description = "Users are responsible for complying with all applicable aviation regulations and ensuring safe flight operations."
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            DisclaimerCard(
                                title = "Data Attribution",
                                description = "Weather data is sourced from Aviation Weather Center (METAR) and FAA Services (TFR information). Map data ©Google Maps."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclaimerCard(
    title: String,
    description: String
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
} 
