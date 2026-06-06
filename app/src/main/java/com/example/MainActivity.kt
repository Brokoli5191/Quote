package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.QuoteRepository
import com.example.ui.AuraViewModel
import com.example.ui.AuraViewModelFactory
import com.example.ui.screens.DailyScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.screens.WidgetSettingsScreen
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Room Database & Repository
        val database = AppDatabase.getInstance(applicationContext)
        val repository = QuoteRepository(database.quoteDao())
        val factory = AuraViewModelFactory(repository)

        setContent {
            val viewModel: AuraViewModel by viewModels { factory }
            
            // Load persistent settings on first composition
            LaunchedEffect(Unit) {
                viewModel.loadThemeSettings(applicationContext)
                viewModel.checkAndSeedDatabase(applicationContext)
            }

            val themeMode by viewModel.themeMode.collectAsState()
            val themeAccent by viewModel.themeAccent.collectAsState()

            MyApplicationTheme(themeMode = themeMode, themeAccent = themeAccent) {
                val activeTab by viewModel.selectedTab.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavigationBar(
                            activeTab = activeTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = {
                                val currentIdx = when (initialState) {
                                    "Daily" -> 0
                                    "Library" -> 1
                                    "Saved" -> 2
                                    "Settings" -> 3
                                    else -> 0
                                }
                                val targetIdx = when (targetState) {
                                    "Daily" -> 0
                                    "Library" -> 1
                                    "Saved" -> 2
                                    "Settings" -> 3
                                    else -> 0
                                }
                                
                                val isForward = targetIdx > currentIdx
                                
                                // Material Expressive organic motions with dynamic bouncy spring physics
                                val exprSpring = spring<Float>(dampingRatio = 0.52f, stiffness = 220f)
                                val exprOffsetSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.52f, stiffness = 220f)
                                
                                if (isForward) {
                                    (slideInHorizontally(animationSpec = exprOffsetSpring) { width -> (width * 0.18f).toInt() } +
                                     fadeIn(animationSpec = exprSpring) +
                                     scaleIn(initialScale = 0.88f, animationSpec = exprSpring))
                                    .togetherWith(
                                     slideOutHorizontally(animationSpec = exprOffsetSpring) { width -> -(width * 0.18f).toInt() } +
                                     fadeOut(animationSpec = exprSpring) +
                                     scaleOut(targetScale = 0.88f, animationSpec = exprSpring))
                                } else {
                                    (slideInHorizontally(animationSpec = exprOffsetSpring) { width -> -(width * 0.18f).toInt() } +
                                     fadeIn(animationSpec = exprSpring) +
                                     scaleIn(initialScale = 0.88f, animationSpec = exprSpring))
                                    .togetherWith(
                                     slideOutHorizontally(animationSpec = exprOffsetSpring) { width -> (width * 0.18f).toInt() } +
                                     fadeOut(animationSpec = exprSpring) +
                                     scaleOut(targetScale = 0.88f, animationSpec = exprSpring))
                                }
                            },
                            label = "MainTabsTransition"
                        ) { tab ->
                            when (tab) {
                                "Daily" -> DailyScreen(viewModel)
                                "Library" -> LibraryScreen(viewModel)
                                "Saved" -> SavedScreen(viewModel)
                                "Settings" -> WidgetSettingsScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(activeTab: String, onTabSelected: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp
    ) {
        val items = listOf(
            Triple("Daily", Pair(Icons.Outlined.FormatQuote, Icons.Default.FormatQuote), "Daily"),
            Triple("Library", Pair(Icons.Outlined.AutoStories, Icons.Default.AutoStories), "Library"),
            Triple("Saved", Pair(Icons.Default.FavoriteBorder, Icons.Default.Favorite), "Saved"),
            Triple("Settings", Pair(Icons.Outlined.Settings, Icons.Default.Settings), "Settings")
        )

        items.forEach { (tab, iconPair, label) ->
            val isSelected = activeTab == tab

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTabSelected(tab)
                },
                icon = {
                    Crossfade(
                        targetState = isSelected,
                        animationSpec = tween(durationMillis = 250),
                        label = "icon_crossfade"
                    ) { selected ->
                        val targetIcon = if (selected) iconPair.second else iconPair.first
                        Icon(
                            imageVector = targetIcon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
    }
}
