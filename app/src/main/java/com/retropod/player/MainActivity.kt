package com.retropod.player

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.retropod.player.ui.theme.RetroPodTheme
import com.retropod.player.ui.theme.RetroBlack
import com.retropod.player.ui.theme.RetroSurface
import com.retropod.player.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RetroPodTheme {
                RetroPodApp()
            }
        }
    }
}

@Composable
fun RetroPodApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var backPressedOnce by remember { mutableStateOf(false) }

    BackHandler {
        if (currentRoute != "home") {
            navController.popBackStack("home", inclusive = false)
        } else {
            if (backPressedOnce) {
                (context as ComponentActivity).finish()
            } else {
                backPressedOnce = true
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    delay(2000)
                    backPressedOnce = false
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = RetroSurface) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentRoute == "home",
                    onClick = { navController.navigate("home") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "Songs") },
                    label = { Text("Songs") },
                    selected = currentRoute == "songs",
                    onClick = { navController.navigate("songs") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Playing") },
                    label = { Text("Playing") },
                    selected = currentRoute == "playing" || currentRoute == "equalizer" || currentRoute == "sleeptimer" || currentRoute == "lyrics" || currentRoute == "coverflow",
                    onClick = { navController.navigate("playing") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") { launchSingleTop = true } }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(RetroBlack)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        if (Math.abs(dragAmount) > 50) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                    }
                }
        ) {
            NavHost(
                navController = navController, 
                startDestination = "home",
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) }
            ) {
                composable("home") { HomeScreen(navController) }
                composable("songs") { SongsScreen(navController) }
                composable("playing") { NowPlayingScreen(navController) }
                composable("settings") { SettingsScreen(navController) }
                composable("equalizer") { EqualizerScreen(navController) }
                composable("coverflow") { CoverFlowScreen(navController) }
                composable("sleeptimer") { SleepTimerScreen(navController) }
                composable("lyrics") { LyricsScreen(navController) }
            }
        }
    }
}
