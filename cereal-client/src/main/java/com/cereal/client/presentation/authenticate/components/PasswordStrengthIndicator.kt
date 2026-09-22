package com.cereal.client.presentation.authenticate.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.auth.PasswordStrength
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.password_requirement_length
import com.cereal_automation.cereal_client.generated.resources.password_requirement_lowercase
import com.cereal_automation.cereal_client.generated.resources.password_requirement_number
import com.cereal_automation.cereal_client.generated.resources.password_requirement_uppercase
import com.cereal_automation.cereal_client.generated.resources.password_strength
import com.cereal_automation.cereal_client.generated.resources.password_strength_fair
import com.cereal_automation.cereal_client.generated.resources.password_strength_good
import com.cereal_automation.cereal_client.generated.resources.password_strength_strong
import com.cereal_automation.cereal_client.generated.resources.password_strength_weak
import org.jetbrains.compose.resources.stringResource

@Composable
fun PasswordStrengthIndicator(
    passwordStrength: PasswordStrength,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Strength label and progress bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CerealText(
                text = stringResource(Res.string.password_strength),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        color = CerealTheme.colorScheme.contentTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
            )

            val strengthText =
                when (passwordStrength.strength) {
                    PasswordStrength.Strength.WEAK -> stringResource(Res.string.password_strength_weak)
                    PasswordStrength.Strength.FAIR -> stringResource(Res.string.password_strength_fair)
                    PasswordStrength.Strength.GOOD -> stringResource(Res.string.password_strength_good)
                    PasswordStrength.Strength.STRONG -> stringResource(Res.string.password_strength_strong)
                }

            val strengthColor =
                when (passwordStrength.strength) {
                    PasswordStrength.Strength.WEAK -> CerealTheme.colorScheme.securityWeak
                    PasswordStrength.Strength.FAIR -> CerealTheme.colorScheme.securityFair
                    PasswordStrength.Strength.GOOD -> CerealTheme.colorScheme.securityGood
                    PasswordStrength.Strength.STRONG -> CerealTheme.colorScheme.success
                }

            CerealText(
                text = strengthText,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        color = strengthColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar
        val progress =
            when (passwordStrength.strength) {
                PasswordStrength.Strength.WEAK -> 0.25f
                PasswordStrength.Strength.FAIR -> 0.5f
                PasswordStrength.Strength.GOOD -> 0.75f
                PasswordStrength.Strength.STRONG -> 1f
            }

        val progressColor =
            when (passwordStrength.strength) {
                PasswordStrength.Strength.WEAK -> CerealTheme.colorScheme.securityWeak
                PasswordStrength.Strength.FAIR -> CerealTheme.colorScheme.securityFair
                PasswordStrength.Strength.GOOD -> CerealTheme.colorScheme.securityGood
                PasswordStrength.Strength.STRONG -> CerealTheme.colorScheme.success
            }

        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Requirements checklist
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PasswordRequirement(
                text = stringResource(Res.string.password_requirement_length),
                isMet = passwordStrength.hasMinimumLength,
            )
            PasswordRequirement(
                text = stringResource(Res.string.password_requirement_uppercase),
                isMet = passwordStrength.hasUppercase,
            )
            PasswordRequirement(
                text = stringResource(Res.string.password_requirement_lowercase),
                isMet = passwordStrength.hasLowercase,
            )
            PasswordRequirement(
                text = stringResource(Res.string.password_requirement_number),
                isMet = passwordStrength.hasNumber,
            )
        }
    }
}

@Composable
private fun PasswordRequirement(
    text: String,
    isMet: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isMet) CerealTheme.colorScheme.success else CerealTheme.colorScheme.contentTertiary.copy(alpha = 0.5f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        CerealText(
            text = text,
            style =
                CerealTheme.typography.labelSmall.copy(
                    color = if (isMet) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f) else CerealTheme.colorScheme.contentTertiary,
                    fontSize = 12.sp,
                ),
        )
    }
}
