package org.noblecow.hrservice.composables

import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.app_name
import heartratemonitor.composeapp.generated.resources.open_source
import org.jetbrains.compose.resources.StringResource

enum class HeartRateScreen(val titleRes: StringResource) {
    Home(titleRes = Res.string.app_name),
    OpenSource(titleRes = Res.string.open_source)
}
