@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.open_source
import heartratemonitor.composeapp.generated.resources.toggle_fake_bpm
import org.jetbrains.compose.resources.stringResource
import org.noblecow.hrservice.ui.theme.Tokens

/**
 * iOS-style action sheet menu.
 *
 * Displays menu options in a modal bottom sheet with iOS styling:
 * - Rounded top corners
 * - Clear background
 * - Centered text items
 * - Dividers between items
 * - iOS-appropriate spacing and typography
 *
 * @param visible Whether the action sheet is currently visible.
 * @param sheetState State of the modal bottom sheet.
 * @param navController Navigation controller for navigating to other screens.
 * @param onFakeBpmClick Callback when "Toggle Fake BPM" is clicked.
 * @param onDismiss Callback when the sheet is dismissed.
 * @param modifier Optional modifier for this composable.
 */
@Composable
internal fun IosActionMenu(
    visible: Boolean,
    navController: NavHostController,
    onFakeBpmClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(
                topStart = Tokens.IosTokens.CornerRadius.Large,
                topEnd = Tokens.IosTokens.CornerRadius.Large
            ),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Tokens.IosTokens.Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Open Source Libraries option
                IosActionMenuItem(
                    text = stringResource(Res.string.open_source),
                    onClick = {
                        navController.navigate(HeartRateScreen.OpenSource.name)
                        onDismiss()
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(
                        horizontal = Tokens.IosTokens.Spacing.Medium
                    )
                )

                // Toggle Fake BPM option
                IosActionMenuItem(
                    text = stringResource(Res.string.toggle_fake_bpm),
                    onClick = {
                        onFakeBpmClick()
                        onDismiss()
                    }
                )

                // Add spacing at bottom for gesture handle
                Spacer(modifier = Modifier.height(Tokens.IosTokens.Spacing.Large))
            }
        }
    }
}

/**
 * Individual menu item in the iOS action sheet.
 *
 * @param text The text to display for this menu item.
 * @param onClick Callback when this menu item is clicked.
 * @param modifier Optional modifier for this composable.
 */
@Composable
private fun IosActionMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = Tokens.IosTokens.Spacing.Medium,
                vertical = Tokens.IosTokens.Spacing.Medium
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}
