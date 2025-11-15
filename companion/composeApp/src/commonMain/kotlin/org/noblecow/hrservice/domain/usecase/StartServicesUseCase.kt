package org.noblecow.hrservice.domain.usecase

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.error_hardware
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServiceError
import org.noblecow.hrservice.data.repository.ServiceResult
import org.noblecow.hrservice.data.repository.toMessage
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.util.PermissionsHelper
import org.noblecow.hrservice.data.util.ResourceHelper

sealed class StartServiceResult {
    data object Starting : StartServiceResult()
    data object BluetoothDisabled : StartServiceResult()
    data class PermissionsNeeded(val permissions: List<String>) : StartServiceResult()
    data class HardwareError(val message: String) : StartServiceResult()
}

interface StartServicesUseCase {
    suspend operator fun invoke(): StartServiceResult
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
internal class StartServicesUseCaseImpl(
    private val mainRepository: MainRepository,
    private val permissionsHelper: PermissionsHelper,
    private val resourceHelper: ResourceHelper
) : StartServicesUseCase {

    override suspend operator fun invoke(): StartServiceResult {
        // Check permissions first
        val missingNotificationPerms = permissionsHelper.getMissingNotificationsPermissions()
        if (missingNotificationPerms.isNotEmpty() || !mainRepository.permissionsGranted()) {
            val permissionsNeeded = missingNotificationPerms + mainRepository.getMissingPermissions()
            return StartServiceResult.PermissionsNeeded(permissionsNeeded.toList())
        }

        // Check Bluetooth hardware state
        return when (mainRepository.getHardwareState()) {
            HardwareState.HARDWARE_UNSUITABLE -> StartServiceResult.HardwareError(
                resourceHelper.getString(Res.string.error_hardware)
            )

            HardwareState.DISABLED -> StartServiceResult.BluetoothDisabled

            HardwareState.READY -> {
                when (val result = mainRepository.startServices()) {
                    is ServiceResult.Success -> StartServiceResult.Starting

                    is ServiceResult.Error -> when (result.error) {
                        is ServiceError.PermissionError -> StartServiceResult.PermissionsNeeded(
                            listOf(result.error.permission)
                        )

                        is ServiceError.AlreadyInState -> StartServiceResult.Starting

                        // Already started is OK
                        else -> StartServiceResult.HardwareError(result.error.toMessage())
                    }
                }
            }
        }
    }
}
