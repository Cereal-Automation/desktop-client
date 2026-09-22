package com.cereal.client.presentation.authenticate.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.application
import com.cereal_automation.cereal_client.generated.resources.introduction_slogan
import com.cereal_automation.cereal_client.generated.resources.introduction_title
import com.cereal_automation.cereal_client.generated.resources.marketplace
import com.cereal_automation.cereal_client.generated.resources.privacy
import com.cereal_automation.cereal_client.generated.resources.status
import com.cereal_automation.cereal_client.generated.resources.support
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.get

/**
 * A reusable authentication layout component that provides the common structure
 * for login and registration screens.
 */
@Composable
fun AuthenticationLayout(
    footerLabel: String,
    footerAction: String,
    onFooterActionClick: () -> Unit,
    authenticationViewModel: AuthenticationViewModel =
        run {
            val scope = rememberCoroutineScope()
            remember { get(AuthenticationViewModel::class.java, parameters = { parametersOf(scope) }) }
        },
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo Section
        AuthLogoSection()

        // Card Content
        AuthCardContainer {
            content()
        }

        // Footer
        AuthFooterSection(
            footerLabel = footerLabel,
            footerAction = footerAction,
            onFooterActionClick = onFooterActionClick,
            onMarketplaceClick = { authenticationViewModel.marketplace() },
            onPrivacyClick = { authenticationViewModel.privacyPolicy() },
            onSupportClick = { authenticationViewModel.support() },
            onStatusClick = { authenticationViewModel.status() },
        )
    }
}

@Composable
private fun AuthLogoSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 40.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.background,
                                ),
                        ),
                    ).border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(24.dp))
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.application),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        CerealText(
            text = stringResource(Res.string.introduction_title),
            style = MaterialTheme.typography.displaySmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        CerealText(
            text = stringResource(Res.string.introduction_slogan),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun AuthCardContainer(
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(32.dp)),
    ) {
        // Subtle glow in the card
        BoxWithConstraints(
            modifier = Modifier.matchParentSize(),
        ) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        Color.Transparent,
                                    ),
                                start = Offset(width, 0f),
                                end = Offset(width / 2, height / 2),
                            ),
                        ),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun AuthFooterSection(
    footerLabel: String,
    footerAction: String,
    onFooterActionClick: () -> Unit,
    onMarketplaceClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onSupportClick: () -> Unit,
    onStatusClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CerealText(
                text = footerLabel,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = CerealTheme.colorScheme.contentTertiary,
                        fontWeight = FontWeight.Medium,
                    ),
            )
            CerealText(
                text = footerAction,
                modifier = Modifier.clickable { onFooterActionClick() },
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            FooterLink(stringResource(Res.string.marketplace), onMarketplaceClick)
            FooterLink(stringResource(Res.string.privacy), onPrivacyClick)
            FooterLink(stringResource(Res.string.support), onSupportClick)
            FooterLink(stringResource(Res.string.status), onStatusClick)
        }
    }
}

@Composable
private fun FooterLink(
    text: String,
    onClick: () -> Unit,
) {
    CerealText(
        text = text.uppercase(),
        modifier = Modifier.clickable { onClick() },
        style =
            MaterialTheme.typography.labelSmall.copy(
                color = CerealTheme.colorScheme.contentSubtle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            ),
    )
}

@Composable
fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            CerealText(
                text = label.uppercase(),
                modifier = Modifier.padding(horizontal = 4.dp),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        color = CerealTheme.colorScheme.contentTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    ),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            decorationBox = { innerTextField ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp),
                            ).border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CerealTheme.colorScheme.contentTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            CerealText(
                                text = placeholder,
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = CerealTheme.colorScheme.contentSubtle,
                                        fontSize = 16.sp,
                                    ),
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
fun AuthErrorMessage(
    errorMessage: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        CerealText(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
