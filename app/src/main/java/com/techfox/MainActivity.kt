package com.techfox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.max
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.techfox.data.PreferencesManager
import com.techfox.data.TopLevelDestination
import com.techfox.ui.HistoryScreen
import com.techfox.ui.MainViewModel
import com.techfox.ui.SettingsScreen
import com.techfox.ui.TranslateScreen
import com.techfox.ui.UiState
import com.techfox.ui.onboarding.OnboardingActivity
import com.techfox.ui.subtitle.SubtitleScreen
import com.techfox.ui.subtitle.SubtitleUiState
import com.techfox.ui.subtitle.SubtitleViewModel
import com.techfox.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = PreferencesManager(this)
        if (!preferences.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = viewModel(),
    subtitleViewModel: SubtitleViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subtitleState by subtitleViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val warningPrompt = stringResource(R.string.warning_imprecise_translation)
    val retryLabel = stringResource(R.string.btn_retry)

    LaunchedEffect(state.translationWarning) {
        state.translationWarning?.let {
            val result = snackbarHostState.showSnackbar(
                message = warningPrompt,
                actionLabel = retryLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retryTranslationStrict()
            }
            viewModel.dismissWarning()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissMessages()
        }
    }

    LaunchedEffect(subtitleState.errorMessage) {
        subtitleState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            subtitleViewModel.dismissMessages()
        }
    }

    LaunchedEffect(subtitleState.successMessage) {
        subtitleState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            subtitleViewModel.dismissMessages()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.reloadSettings()
        subtitleViewModel.reloadSettings()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MainNavigationBar(
                currentDestination = currentDestination,
                onNavigateToDestination = navController::navigateToTopLevel
            )
        }
    ) { innerPadding ->
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val navBottom = innerPadding.calculateBottomPadding()
        val effectiveBottom = max(imeBottom, navBottom)

        MainNavHost(
            navController = navController,
            state = state,
            subtitleState = subtitleState,
            viewModel = viewModel,
            subtitleViewModel = subtitleViewModel,
            modifier = Modifier.padding(bottom = effectiveBottom)
        )
    }
}

@Composable
private fun MainNavigationBar(
    currentDestination: NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    ShortNavigationBar(
        modifier = modifier.testTag("navigation_bar")
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag(destination.testTag)
            )
        }
    }
}

@Composable
private fun MainNavHost(
    navController: NavHostController,
    state: UiState,
    subtitleState: SubtitleUiState,
    viewModel: MainViewModel,
    subtitleViewModel: SubtitleViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.TRANSLATE.route,
        enterTransition = {
            fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        },
        exitTransition = {
            fadeOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        },
        modifier = modifier
    ) {
        composable(TopLevelDestination.TRANSLATE.route) {
            TranslateScreen(
                state = state,
                onInputChanged = viewModel::onInputTextChanged,
                onTargetLanguageChanged = viewModel::onTargetLanguageChanged,
                onTranslateClicked = viewModel::translate,
                onClearClicked = viewModel::clearInput
            )
        }
        composable(TopLevelDestination.SUBTITLES.route) {
            SubtitleScreen(
                state = subtitleState,
                onInputChanged = subtitleViewModel::onInputTextChanged,
                onFileLoaded = subtitleViewModel::onFileLoaded,
                onTargetLanguageChanged = subtitleViewModel::onTargetLanguageChanged,
                onContextChanged = subtitleViewModel::onContextDescriptionChanged,
                onPresetSelected = subtitleViewModel::onPresetSelected,
                onCustomPromptChanged = subtitleViewModel::onCustomPromptChanged,
                onTokenGuardChanged = subtitleViewModel::onTokenGuardChanged,
                onAddCharacter = subtitleViewModel::addCharacter,
                onUpdateCharacter = subtitleViewModel::updateCharacter,
                onRemoveCharacter = subtitleViewModel::removeCharacter,
                onTranslateClicked = subtitleViewModel::startTranslation,
                onResumeClicked = subtitleViewModel::resumeTranslation,
                onDiscardResume = subtitleViewModel::discardResumableJob,
                onCancelClicked = subtitleViewModel::cancelTranslation,
                onClearClicked = subtitleViewModel::clearInput
            )
        }
        composable(TopLevelDestination.HISTORY.route) {
            HistoryScreen(
                state = state,
                onHistoryItemSelected = { item ->
                    viewModel.selectHistoryItem(item)
                    navController.navigateToTopLevel(TopLevelDestination.TRANSLATE)
                },
                onDeleteItem = viewModel::deleteHistoryItem,
                onClearAllHistory = viewModel::clearAllHistory
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(
                state = state,
                onApiKeyChanged = viewModel::onApiKeyChanged,
                onModelChanged = viewModel::onModelChanged,
                onCustomInstructionChanged = viewModel::onCustomInstructionChanged,
                onEnableInsightChanged = viewModel::onEnableInsightChanged,
                onClearHistory = viewModel::clearAllHistory,
                onDismissMessage = viewModel::dismissMessages
            )
        }
    }
}

fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
