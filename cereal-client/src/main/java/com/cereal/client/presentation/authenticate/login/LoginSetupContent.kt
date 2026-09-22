package com.cereal.client.presentation.authenticate.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.signing_you_in
import com.cereal_automation.cereal_client.generated.resources.syncing_scripts_progress
import org.jetbrains.compose.resources.stringResource

/**
 * Shown in place of the login form once authentication is accepted and post-auth setup is taking long
 * enough to be worth surfacing (see [LoginViewModel.setupState]). A steady headline reframes the wait
 * as setup progress; the count line appears only while scripts are actively syncing.
 */
@Composable
fun LoginSetupContent(setupProgress: SetupProgress) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CerealCircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        CerealText(
            text = stringResource(Res.string.signing_you_in),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )

        val current = setupProgress.current
        val total = setupProgress.total
        if (current != null && total != null) {
            Spacer(modifier = Modifier.height(8.dp))
            CerealText(
                text = stringResource(Res.string.syncing_scripts_progress, current, total),
                textAlign = TextAlign.Center,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = CerealTheme.colorScheme.contentTertiary,
                    ),
            )
        }
    }
}
