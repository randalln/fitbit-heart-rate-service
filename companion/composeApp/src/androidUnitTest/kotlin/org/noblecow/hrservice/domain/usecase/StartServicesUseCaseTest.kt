package org.noblecow.hrservice.domain.usecase

import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.error_hardware
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.noblecow.hrservice.MainDispatcherRule
import org.noblecow.hrservice.data.repository.MainRepository
import org.noblecow.hrservice.data.repository.ServiceError
import org.noblecow.hrservice.data.repository.ServiceResult
import org.noblecow.hrservice.data.source.local.HardwareState
import org.noblecow.hrservice.data.util.PermissionsHelper
import org.noblecow.hrservice.data.util.ResourceHelper

class StartServicesUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var useCase: StartServicesUseCase
    private lateinit var mainRepository: MainRepository
    private lateinit var permissionsHelper: PermissionsHelper
    private lateinit var resourceHelper: ResourceHelper

    @Before
    fun setup() {
        mainRepository = mockk(relaxed = true)
        permissionsHelper = mockk(relaxed = true)
        resourceHelper = mockk(relaxed = true)

        // Default mocks
        every { permissionsHelper.getMissingNotificationsPermissions() } returns emptyArray()
        every { mainRepository.permissionsGranted() } returns true
        every { mainRepository.getMissingPermissions() } returns emptyArray()
        every { mainRepository.getHardwareState() } returns HardwareState.READY
        coEvery { mainRepository.startServices() } returns ServiceResult.Success(Unit)

        useCase = StartServicesUseCaseImpl(mainRepository, permissionsHelper, resourceHelper)
    }

    // ============================================================================
    // Happy Path Tests
    // ============================================================================

    @Test
    fun `invoke returns Starting when all checks pass and services start successfully`() = runTest {
        val result = useCase()

        assertTrue(result is StartServiceResult.Starting)
        coVerify { mainRepository.startServices() }
    }

    // ============================================================================
    // Permissions Tests
    // ============================================================================

    @Test
    fun `invoke returns PermissionsNeeded when notification permissions are missing`() = runTest {
        val missingPerms = arrayOf("android.permission.POST_NOTIFICATIONS")
        every { permissionsHelper.getMissingNotificationsPermissions() } returns missingPerms

        val result = useCase()

        assertTrue(result is StartServiceResult.PermissionsNeeded)
        assertEquals(
            missingPerms.toList(),
            (result as StartServiceResult.PermissionsNeeded).permissions
        )
        coVerify(exactly = 0) { mainRepository.startServices() }
    }

    @Test
    fun `invoke returns PermissionsNeeded when Bluetooth permissions are missing`() = runTest {
        val missingBtPerms = arrayOf(
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT"
        )
        every { mainRepository.permissionsGranted() } returns false
        every { mainRepository.getMissingPermissions() } returns missingBtPerms

        val result = useCase()

        assertTrue(result is StartServiceResult.PermissionsNeeded)
        val permissions = (result as StartServiceResult.PermissionsNeeded).permissions
        assertTrue(permissions.containsAll(missingBtPerms.toList()))
        coVerify(exactly = 0) { mainRepository.startServices() }
    }

    @Test
    fun `invoke combines notification and Bluetooth permissions when both are missing`() = runTest {
        val notificationPerms = arrayOf("android.permission.POST_NOTIFICATIONS")
        val bluetoothPerms = arrayOf("android.permission.BLUETOOTH_ADVERTISE")
        every { permissionsHelper.getMissingNotificationsPermissions() } returns notificationPerms
        every { mainRepository.permissionsGranted() } returns false
        every { mainRepository.getMissingPermissions() } returns bluetoothPerms

        val result = useCase()

        assertTrue(result is StartServiceResult.PermissionsNeeded)
        val permissions = (result as StartServiceResult.PermissionsNeeded).permissions
        assertEquals(2, permissions.size)
        assertTrue(permissions.containsAll(notificationPerms.toList()))
        assertTrue(permissions.containsAll(bluetoothPerms.toList()))
    }

    @Test
    fun `invoke returns PermissionsNeeded when repository returns PermissionError`() = runTest {
        val permissionName = "android.permission.BLUETOOTH_ADVERTISE"
        coEvery { mainRepository.startServices() } returns
            ServiceResult.Error(ServiceError.PermissionError(permissionName))

        val result = useCase()

        assertTrue(result is StartServiceResult.PermissionsNeeded)
        assertEquals(
            listOf(permissionName),
            (result as StartServiceResult.PermissionsNeeded).permissions
        )
    }

    // ============================================================================
    // Hardware State Tests
    // ============================================================================

    @Test
    fun `invoke returns HardwareError when hardware is unsuitable`() = runTest {
        val errorMessage = "Bluetooth hardware not available"
        every { mainRepository.getHardwareState() } returns HardwareState.HARDWARE_UNSUITABLE
        coEvery { resourceHelper.getString(Res.string.error_hardware) } returns errorMessage

        val result = useCase()

        assertTrue(result is StartServiceResult.HardwareError)
        assertEquals(errorMessage, (result as StartServiceResult.HardwareError).message)
        coVerify(exactly = 0) { mainRepository.startServices() }
    }

    @Test
    fun `invoke returns BluetoothDisabled when Bluetooth is disabled`() = runTest {
        every { mainRepository.getHardwareState() } returns HardwareState.DISABLED

        val result = useCase()

        assertTrue(result is StartServiceResult.BluetoothDisabled)
        coVerify(exactly = 0) { mainRepository.startServices() }
    }

    // ============================================================================
    // Service Error Handling Tests
    // ============================================================================

    @Test
    fun `invoke returns Starting when services are already in starting state`() = runTest {
        coEvery { mainRepository.startServices() } returns
            ServiceResult.Error(ServiceError.AlreadyInState("starting"))

        val result = useCase()

        assertTrue(result is StartServiceResult.Starting)
    }

    @Test
    fun `invoke returns HardwareError when BluetoothError occurs`() = runTest {
        val errorReason = "Bluetooth adapter not found"
        coEvery { mainRepository.startServices() } returns
            ServiceResult.Error(ServiceError.BluetoothError(errorReason))

        val result = useCase()

        assertTrue(result is StartServiceResult.HardwareError)
        assertTrue((result as StartServiceResult.HardwareError).message.contains("Bluetooth"))
    }

    @Test
    fun `invoke returns HardwareError when WebServerError occurs`() = runTest {
        val errorReason = "Port 12345 already in use"
        coEvery { mainRepository.startServices() } returns
            ServiceResult.Error(ServiceError.WebServerError(errorReason))

        val result = useCase()

        assertTrue(result is StartServiceResult.HardwareError)
        assertTrue((result as StartServiceResult.HardwareError).message.contains("Server"))
    }

    @Test
    fun `invoke returns HardwareError when UnknownError occurs`() = runTest {
        val errorMessage = "Unexpected error occurred"
        coEvery { mainRepository.startServices() } returns
            ServiceResult.Error(ServiceError.UnknownError(errorMessage))

        val result = useCase()

        assertTrue(result is StartServiceResult.HardwareError)
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    fun `invoke checks permissions before hardware state`() = runTest {
        every { permissionsHelper.getMissingNotificationsPermissions() } returns
            arrayOf("android.permission.POST_NOTIFICATIONS")
        every { mainRepository.getHardwareState() } returns HardwareState.HARDWARE_UNSUITABLE

        val result = useCase()

        // Should return PermissionsNeeded, not HardwareError
        assertTrue(result is StartServiceResult.PermissionsNeeded)
        // Should not check hardware state if permissions are missing
        coVerify(exactly = 0) { mainRepository.startServices() }
    }

    @Test
    fun `invoke checks hardware state before attempting to start services`() = runTest {
        every { mainRepository.getHardwareState() } returns HardwareState.DISABLED

        val result = useCase()

        assertTrue(result is StartServiceResult.BluetoothDisabled)
        coVerify(exactly = 0) { mainRepository.startServices() }
    }

    @Test
    fun `invoke handles empty permission lists correctly`() = runTest {
        every { permissionsHelper.getMissingNotificationsPermissions() } returns emptyArray()
        every { mainRepository.getMissingPermissions() } returns emptyArray()
        every { mainRepository.permissionsGranted() } returns true

        val result = useCase()

        assertTrue(result is StartServiceResult.Starting)
        coVerify { mainRepository.startServices() }
    }
}
