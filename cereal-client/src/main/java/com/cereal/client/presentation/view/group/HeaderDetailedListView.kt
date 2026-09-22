package com.cereal.client.presentation.view.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.view.CerealText

@Composable
fun HeaderDetailedListView(
    headerTitle: String,
    headerSubtitle: String? = null,
    modifier: Modifier = Modifier,
    subMenu: @Composable RowScope.() -> Unit = { },
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = 25.dp)
                .padding(top = 25.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                CerealText(
                    text = headerTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                headerSubtitle?.let {
                    CerealText(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))

            subMenu()
        }
    }
}
