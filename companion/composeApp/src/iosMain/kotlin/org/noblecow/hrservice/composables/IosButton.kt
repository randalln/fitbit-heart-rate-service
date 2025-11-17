package org.noblecow.hrservice.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.noblecow.hrservice.ui.theme.Tokens

/**
 * iOS-style filled button.
 *
 * Applies iOS-specific styling:
 * - Standard iOS corner radius (10pt)
 * - iOS-appropriate minimum height (44pt touch target)
 * - iOS-style padding
 *
 * Uses Material3 Button as base but ensures iOS HIG compliance.
 *
 * @param onClick Callback when the button is clicked.
 * @param modifier Optional modifier for this composable.
 * @param enabled Whether the button is enabled.
 * @param shape Shape of the button (defaults to iOS standard corner radius).
 * @param content The content to display inside the button.
 */
@Composable
fun IosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Tokens.IosTokens.NavigationBar.Height),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(
            horizontal = Tokens.IosTokens.Spacing.Medium,
            vertical = Tokens.IosTokens.Spacing.Compact
        ),
        content = content
    )
}

/**
 * iOS-style bordered button (equivalent to .bordered button style in SwiftUI).
 *
 * Applies iOS-specific styling with a border and no fill.
 *
 * @param onClick Callback when the button is clicked.
 * @param modifier Optional modifier for this composable.
 * @param enabled Whether the button is enabled.
 * @param shape Shape of the button (defaults to iOS standard corner radius).
 * @param content The content to display inside the button.
 */
@Composable
fun IosBorderedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Tokens.IosTokens.NavigationBar.Height),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        contentPadding = PaddingValues(
            horizontal = Tokens.IosTokens.Spacing.Medium,
            vertical = Tokens.IosTokens.Spacing.Compact
        ),
        content = content
    )
}

/**
 * iOS-style plain text button (equivalent to .plain button style in SwiftUI).
 *
 * Applies iOS-specific styling with no background or border.
 *
 * @param onClick Callback when the button is clicked.
 * @param modifier Optional modifier for this composable.
 * @param enabled Whether the button is enabled.
 * @param content The content to display inside the button.
 */
@Composable
fun IosTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Tokens.IosTokens.NavigationBar.Height),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(
            horizontal = Tokens.IosTokens.Spacing.Medium,
            vertical = Tokens.IosTokens.Spacing.Compact
        ),
        content = content
    )
}
