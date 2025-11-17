@file:OptIn(ExperimentalMaterial3Api::class)

package org.noblecow.hrservice.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * @param onOpenSourceClick Callback when "Open Source Libraries" is clicked.
 * @param onFakeBpmClick Callback when "Toggle Fake BPM" is clicked.
 * @param onDismiss Callback when the sheet is dismissed.
 * @param modifier Optional modifier for this composable.
 */
@Composable
internal fun IosActionMenu(
    visible: Boolean,
    onOpenSourceClick: () -> Unit,
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
                        onOpenSourceClick()
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
 * Uses IosTextButton for iOS-native button styling with:
 * - 44pt minimum touch target (iOS HIG compliance)
 * - iOS-appropriate padding
 * - Accessible button semantics
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
    IosTextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
