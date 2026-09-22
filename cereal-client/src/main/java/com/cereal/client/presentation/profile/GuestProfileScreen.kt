package com.cereal.client.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonSize
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealOutlinedButton
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.create_account
import com.cereal_automation.cereal_client.generated.resources.feature_cloud_sync
import com.cereal_automation.cereal_client.generated.resources.feature_cloud_sync_desc
import com.cereal_automation.cereal_client.generated.resources.feature_community
import com.cereal_automation.cereal_client.generated.resources.feature_community_desc
import com.cereal_automation.cereal_client.generated.resources.feature_premium_scripts
import com.cereal_automation.cereal_client.generated.resources.feature_premium_scripts_desc
import com.cereal_automation.cereal_client.generated.resources.guest_join_hint
import com.cereal_automation.cereal_client.generated.resources.guest_profile_description
import com.cereal_automation.cereal_client.generated.resources.guest_profile_title
import com.cereal_automation.cereal_client.generated.resources.locked_features
import com.cereal_automation.cereal_client.generated.resources.login
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuestProfileScreen(
    onLoginClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header Section
                GuestProfileHeader()

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CerealButton(
                        onClick = onCreateAccountClick,
                        type = CerealButtonType.Primary,
                        size = CerealButtonSize.Small,
                        text = stringResource(Res.string.create_account),
                        modifier = Modifier.width(180.dp),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    CerealOutlinedButton(
                        onClick = onLoginClick,
                        type = CerealButtonType.Surface,
                        size = CerealButtonSize.Small,
                        modifier = Modifier.width(180.dp),
                    ) {
                        CerealText(
                            text = stringResource(Res.string.login),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Locked Features Card
                LockedFeaturesCard()
            }
        }
    }
}

@Composable
private fun GuestProfileHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar
        Box(
            modifier =
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 4.dp,
                        color = CerealTheme.colorScheme.backgroundDark,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = CerealTheme.colorScheme.contentTertiary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        CerealText(
            text = stringResource(Res.string.guest_profile_title),
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        CerealText(
            text = stringResource(Res.string.guest_profile_description),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = CerealTheme.colorScheme.contentTertiary,
                    lineHeight = 24.sp,
                ),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 450.dp),
        )
    }
}

@Composable
private fun LockedFeaturesCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CerealTheme.colorScheme.cardDark)
                .border(
                    width = 1.dp,
                    color = CerealTheme.colorScheme.borderDark,
                    shape = RoundedCornerShape(16.dp),
                ),
    ) {
        // Card Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CerealText(
                text = stringResource(Res.string.locked_features).uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = CerealTheme.colorScheme.contentTertiary,
                    ),
            )
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        // Feature Items
        LockedFeatureItem(
            icon = Icons.Default.Star,
            iconBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            iconTint = MaterialTheme.colorScheme.primary,
            title = stringResource(Res.string.feature_premium_scripts),
            description = stringResource(Res.string.feature_premium_scripts_desc),
        )

        HorizontalDivider(
            color = CerealTheme.colorScheme.borderDark,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        LockedFeatureItem(
            icon = Icons.Default.Cloud,
            iconBackgroundColor = CerealTheme.colorScheme.info.copy(alpha = 0.1f),
            iconTint = CerealTheme.colorScheme.info,
            title = stringResource(Res.string.feature_cloud_sync),
            description = stringResource(Res.string.feature_cloud_sync_desc),
        )

        HorizontalDivider(
            color = CerealTheme.colorScheme.borderDark,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        LockedFeatureItem(
            icon = Icons.Default.Group,
            iconBackgroundColor = CerealTheme.colorScheme.success.copy(alpha = 0.1f),
            iconTint = CerealTheme.colorScheme.success,
            title = stringResource(Res.string.feature_community),
            description = stringResource(Res.string.feature_community_desc),
        )

        // Footer
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                    .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CerealText(
                text = stringResource(Res.string.guest_join_hint),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        color = CerealTheme.colorScheme.contentTertiary,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    ),
            )
        }
    }
}

@Composable
private fun LockedFeatureItem(
    icon: ImageVector,
    iconBackgroundColor: Color,
    iconTint: Color,
    title: String,
    description: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = title,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
            )

            Spacer(modifier = Modifier.height(4.dp))

            CerealText(
                text = description,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        color = CerealTheme.colorScheme.contentTertiary,
                        lineHeight = 18.sp,
                    ),
            )
        }
    }
}
