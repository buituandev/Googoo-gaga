package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.max
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PreferencesManager
import com.example.ui.HistoryScreen
import com.example.ui.MainViewModel
import com.example.ui.SettingsScreen
import com.example.ui.TranslateScreen
import com.example.ui.onboarding.OnboardingActivity
import com.example.ui.subtitle.SubtitleScreen
import com.example.ui.subtitle.SubtitleViewModel
import com.example.ui.theme.MyApplicationTheme

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
    viewModel: MainViewModel = viewModel(),
    subtitleViewModel: SubtitleViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subtitleState by subtitleViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ShortNavigationBar(
                modifier = Modifier
                    .testTag("navigation_bar"),
            ) {
                // Destination 1: Translate
                ShortNavigationBarItem(
                    selected = state.currentTab == 0,
                    onClick = { viewModel.switchTab(0) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = stringResource(R.string.nav_translate)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_translate),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.testTag("nav_item_translate")
                )

                // Destination 2: Subtitles
                ShortNavigationBarItem(
                    selected = state.currentTab == 1,
                    onClick = { viewModel.switchTab(1) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = stringResource(R.string.nav_subtitles)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_subtitles),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.testTag("nav_item_subtitles")
                )

                // Destination 3: History
                ShortNavigationBarItem(
                    selected = state.currentTab == 2,
                    onClick = { viewModel.switchTab(2) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = stringResource(R.string.nav_history)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_history),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.testTag("nav_item_history")
                )

                // Destination 4: Settings
                ShortNavigationBarItem(
                    selected = state.currentTab == 3,
                    onClick = { viewModel.switchTab(3) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_settings),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.testTag("nav_item_settings")
                )
            }
        }
    ) { innerPadding ->
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val navBottom = innerPadding.calculateBottomPadding()
        val effectiveBottom = max(imeBottom, navBottom)

        AnimatedContent(
            targetState = state.currentTab,
            transitionSpec = {
                fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) togetherWith fadeOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            },
            modifier = Modifier.padding(bottom = effectiveBottom),
            label = "ScreenTransition"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> TranslateScreen(
                    state = state,
                    onInputChanged = { viewModel.onInputTextChanged(it) },
                    onTargetLanguageChanged = { viewModel.onTargetLanguageChanged(it) },
                    onTranslateClicked = { viewModel.translate() },
                    onClearClicked = { viewModel.clearInput() },
                    onHistoryItemSelected = { viewModel.selectHistoryItem(it) }
                )
                1 -> SubtitleScreen(
                    state = subtitleState,
                    onInputChanged = { subtitleViewModel.onInputTextChanged(it) },
                    onFileLoaded = { name, content -> subtitleViewModel.onFileLoaded(name, content) },
                    onTargetLanguageChanged = { subtitleViewModel.onTargetLanguageChanged(it) },
                    onContextChanged = { subtitleViewModel.onContextDescriptionChanged(it) },
                    onPresetSelected = { subtitleViewModel.onPresetSelected(it) },
                    onCustomPromptChanged = { subtitleViewModel.onCustomPromptChanged(it) },
                    onAddCharacter = { subtitleViewModel.addCharacter() },
                    onUpdateCharacter = { id, name, desc -> subtitleViewModel.updateCharacter(id, name, desc) },
                    onRemoveCharacter = { subtitleViewModel.removeCharacter(it) },
                    onTranslateClicked = { subtitleViewModel.startTranslation() },
                    onResumeClicked = { subtitleViewModel.resumeTranslation() },
                    onDiscardResume = { subtitleViewModel.discardResumableJob() },
                    onCancelClicked = { subtitleViewModel.cancelTranslation() },
                    onClearClicked = { subtitleViewModel.clearInput() }
                )
                2 -> HistoryScreen(
                    state = state,
                    onHistoryItemSelected = { viewModel.selectHistoryItem(it) },
                    onDeleteItem = { viewModel.deleteHistoryItem(it) },
                    onClearAllHistory = { viewModel.clearAllHistory() }
                )
                3 -> SettingsScreen(
                    state = state,
                    onApiKeyChanged = { viewModel.onApiKeyChanged(it) },
                    onModelChanged = { viewModel.onModelChanged(it) },
                    onCustomInstructionChanged = { viewModel.onCustomInstructionChanged(it) },
                    onEnableInsightChanged = { viewModel.onEnableInsightChanged(it) },
                    onClearHistory = { viewModel.clearAllHistory() },
                    onDismissMessage = { viewModel.dismissMessages() }
                )
            }
        }
    }
}
