package com.cereal.client.presentation.authenticate.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.client.presentation.view.InputBorder
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.application
import com.cereal_automation.cereal_client.generated.resources.introduction_slogan
import com.cereal_automation.cereal_client.generated.resources.introduction_title
import com.cereal_automation.cereal_client.generated.resources.marketplace
import com.cereal_automation.cereal_client.generated.resources.password_hide
import com.cereal_automation.cereal_client.generated.resources.password_show
import com.cereal_automation.cereal_client.generated.resources.privacy
import com.cereal_automation.cereal_client.generated.resources.status
import com.cereal_automation.cereal_client.generated.resources.support
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.get

/**
 * A reusable authentication layout component that provides the common structure
 * for login and registration screens: a centred column of logo + [title], the card holding
 * [content], an optional [belowCard] slot, and the footer prompt; the site links sit pinned at the bottom.
 */
@Composable
fun AuthenticationLayout(
    footerLabel: String,
    footerAction: String,
    onFooterActionClick: () -> Unit,
    title: String = stringResource(Res.string.introduction_title),
    authenticationViewModel: AuthenticationViewModel =
        run {
            val scope = rememberCoroutineScope()
            remember { get(AuthenticationViewModel::class.java, parameters = { parametersOf(scope) }) }
        },
    belowCard: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    AuthLogoSection(title)
                    CerealCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(22.dp), content = content)
                    }
                    belowCard()
                    AuthFooterPrompt(footerLabel, footerAction, onFooterActionClick)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(start = 24.dp, end = 24.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FooterLink(stringResource(Res.string.marketplace)) { authenticationViewModel.marketplace() }
            FooterLink(stringResource(Res.string.privacy)) { authenticationViewModel.privacyPolicy() }
            FooterLink(stringResource(Res.string.support)) { authenticationViewModel.support() }
            FooterLink(stringResource(Res.string.status)) { authenticationViewModel.status() }
        }
    }
}

@Composable
private fun AuthLogoSection(title: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(bottom = 12.dp)
                    .size(52.dp)
                    .background(Color.White, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.application),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }
        CerealText(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        CerealText(
            text = stringResource(Res.string.introduction_slogan),
            modifier = Modifier.widthIn(max = 330.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
            color = CerealTheme.colorScheme.contentTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AuthFooterPrompt(
    label: String,
    action: String,
    onActionClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
        CerealText(text = label, style = style, color = CerealTheme.colorScheme.contentTertiary)
        AuthLink(text = action, onClick = onActionClick, style = style.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun FooterLink(
    text: String,
    onClick: () -> Unit,
) {
    CerealText(
        text = text,
        modifier = Modifier.clickable(role = Role.Button) { onClick() }.pointerHoverIcon(PointerIcon.Hand),
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = CerealTheme.colorScheme.contentTertiary,
    )
}

/** An inline text link in the link colour. */
@Composable
fun AuthLink(
    text: String,
    onClick: () -> Unit,
    style: TextStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
) {
    CerealText(
        text = text,
        modifier = Modifier.clickable(role = Role.Button) { onClick() }.pointerHoverIcon(PointerIcon.Hand),
        style = style,
        color = CerealTheme.colorScheme.link,
    )
}

/**
 * The design's labelled filled input: a sentence-case [label] (with an optional trailing [labelAction]
 * link), then a 38dp box with a leading [icon]. Password fields get a Show/Hide toggle once non-empty.
 */
@Composable
fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isError: Boolean = false,
    labelAction: String? = null,
    onLabelActionClick: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var revealed by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CerealText(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = CerealTheme.colorScheme.contentSecondary,
                )
                labelAction?.let { AuthLink(text = it, onClick = onLabelActionClick) }
            }
        }

        CerealTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            isError = isError,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CerealTheme.colorScheme.contentTertiary,
                    modifier = Modifier.size(15.dp),
                )
            },
            trailingIcon =
                if (isPassword && value.isNotEmpty()) {
                    {
                        CerealText(
                            text = stringResource(if (revealed) Res.string.password_hide else Res.string.password_show),
                            modifier = Modifier.clickable(role = Role.Button) { revealed = !revealed }.pointerHoverIcon(PointerIcon.Hand),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = CerealTheme.colorScheme.contentTertiary,
                        )
                    }
                } else {
                    null
                },
        )
    }
}

/** The design's error banner: a tinted red box with an info icon. */
@Composable
fun AuthErrorMessage(
    errorMessage: String,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val red = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(red.copy(alpha = 0.08f), shape)
                .border(1.dp, red.copy(alpha = 0.28f), shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = red,
            modifier = Modifier.padding(top = 2.dp).size(14.dp),
        )
        CerealText(
            text = errorMessage,
            color = ErrorText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
        )
    }
}

private val ErrorText = Color(0xFFF3B0B0)
