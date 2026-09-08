package com.example.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.R
import com.example.ui.components.ExpressiveButton
import com.example.ui.components.ExpressiveIconButton
import com.example.ui.theme.PillShape
import com.example.ui.theme.TextFieldShape

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onComplete: (apiKey: String) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var apiKeyInput by remember { mutableStateOf("") }

    BackHandler(enabled = currentStep > 0) {
        currentStep = 0
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("onboarding_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ShapeMorphingPatternBackground(
                currentStep = currentStep,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Top Navigation & Step Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    ExpressiveIconButton(
                        onClick = { currentStep = 0 },
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        size = 40.dp
                    )
                } else if (onClose != null) {
                    ExpressiveIconButton(
                        onClick = onClose,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        size = 40.dp
                    )
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                // Step Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(2) { index ->
                        val isSelected = index == currentStep
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .clip(PillShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                        )
                    }
                }

                // Skip button on Step 2
                if (currentStep == 1) {
                    TextButton(onClick = { onComplete("") }) {
                        Text(
                            text = stringResource(R.string.btn_skip_for_now),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // Screen Content Transitions
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "OnboardingStepTransition"
            ) { step ->
                when (step) {
                    0 -> WelcomeStep(
                        onNext = { currentStep = 1 }
                    )

                    1 -> SetupStep(
                        apiKey = apiKeyInput,
                        onApiKeyChanged = { apiKeyInput = it },
                        onDone = { onComplete(apiKeyInput) }
                    )
                }
            }
        }
    }
}
}

@Composable
private fun WelcomeStep(
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_welcome")
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Hero Visual & Animated Glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .scale(scaleAnim)
        ) {
            // Subtle aura ring
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Image(
                painter = painterResource(id = R.drawable.aicube),
                contentDescription = "Goo-goo ga-ga Mascot",
                modifier = Modifier.size(120.dp)
            )
        }

        // Title and Description
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 38.sp
            )

            Text(
                text = stringResource(R.string.onboarding_welcome_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // Next Button with Expressive Icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            ExpressiveIconButton(
                onClick = onNext,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = stringResource(R.string.cd_next_screen),
                size = 64.dp,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("onboarding_welcome_next_button")
            )
        }
    }
}

@Composable
private fun SetupStep(
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step Title and Subtitle
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.onboarding_setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.onboarding_setup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Guide on How to Get Gemini API Key
        Surface(
            shape = TextFieldShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.onboarding_guide_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                GuideStepItem(text = stringResource(R.string.onboarding_step_1))
                GuideStepItem(text = stringResource(R.string.onboarding_step_2))
                GuideStepItem(text = stringResource(R.string.onboarding_step_3))
                GuideStepItem(text = stringResource(R.string.onboarding_step_4))

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://aistudio.google.com/app/apikey".toUri()
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = PillShape
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_open_ai_studio))
                }
            }
        }

        // API Key Text Input (DIRECTLY on the screen surface, NOT inside a card!)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.hint_gemini_api_key),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_api_key_input"),
                placeholder = {
                    Text(
                        text = "AIzaSy...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (apiKey.isNotEmpty()) {
                        IconButton(onClick = { onApiKeyChanged("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = stringResource(R.string.cd_clear_text)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                                if (clip.isNotBlank()) {
                                    onApiKeyChanged(clip.trim())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = stringResource(R.string.cd_paste_text)
                            )
                        }
                    }
                },
                singleLine = true,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // All Done Button
        ExpressiveButton(
            onClick = onDone,
            text = stringResource(R.string.btn_all_done),
            icon = Icons.Rounded.Check,
            height = 52.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_all_done_button")
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GuideStepItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 18.sp
    )
}
