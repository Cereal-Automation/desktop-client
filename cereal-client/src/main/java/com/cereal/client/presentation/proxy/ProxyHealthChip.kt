package com.cereal.client.presentation.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.proxies_health_failed
import com.cereal_automation.cereal_client.generated.resources.proxies_health_latency
import com.cereal_automation.cereal_client.generated.resources.proxies_health_ok
import com.cereal_automation.cereal_client.generated.resources.proxies_health_unknown
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProxyHealthChip(
    health: ProxyHealth,
    inFlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = CerealTheme.colorScheme
    if (inFlight) {
        Box(modifier = modifier.size(14.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                strokeWidth = 1.5.dp,
                color = colors.contentSecondary,
                modifier = Modifier.size(12.dp),
            )
        }
        return
    }

    when (health.status) {
        ProxyHealthStatus.HEALTHY -> {
            val label =
                health.latencyMs?.let { stringResource(Res.string.proxies_health_latency, it.toInt()) }
                    ?: stringResource(Res.string.proxies_health_ok)
            HealthBadge(
                modifier = modifier,
                dotColor = colors.success,
                label = label,
            )
        }

        ProxyHealthStatus.FAILED -> {
            HealthBadge(
                modifier = modifier,
                dotColor = MaterialTheme.colorScheme.primary,
                label = stringResource(Res.string.proxies_health_failed),
            )
        }

        ProxyHealthStatus.UNKNOWN -> {
            CerealText(
                text = stringResource(Res.string.proxies_health_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentTertiary,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun HealthBadge(
    dotColor: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(
                    color = CerealTheme.colorScheme.cardDark,
                    shape = RoundedCornerShape(6.dp),
                ).padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(7.dp)
                    .background(color = dotColor, shape = CircleShape),
        )
        CerealText(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = CerealTheme.colorScheme.contentSecondary,
        )
    }
}
