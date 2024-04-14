package org.noblecow.hrservice

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.noblecow.hrservice.databinding.ActivityServerBinding

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: HRViewModel by viewModels()

    private lateinit var bpmText: TextView
    private lateinit var startButton: Button
    private lateinit var status: TextView
    private lateinit var fakeBPM: Button

    private val requestMultiplePermissions = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions ->
        viewModel.receivePermissions(permissions)
    }

    private val enableBluetoothLauncher =
        registerForActivityResult(StartActivityForResult()) {
            if (it.resultCode != RESULT_OK) {
                viewModel.userDeclinedBluetoothEnable()
            }
            // BroadcastReceiver that we register elsewhere handles BT being turned on
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityServerBinding.inflate(layoutInflater)
        bpmText = binding.textBpm
        startButton = binding.start.apply {
            setOnClickListener {
                viewModel.confirmPermissions()
            }
        }
        status = binding.status
        fakeBPM = binding.fakeBpm.apply {
            if (BuildConfig.DEBUG) {
                visibility = View.VISIBLE
                setOnClickListener {
                    viewModel.toggleFakeBPM()
                }
            }
        }
        setContentView(binding.root)

        // Devices with a display should not go to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    onStateChange(it)
                }
            }
        }
    }

    private fun onStateChange(uiState: UiState) {
        when (uiState) {
            is UiState.Idle -> {
            }
            is UiState.RequestPermissions -> {
                requestMultiplePermissions.launch(uiState.permissions.toTypedArray())
            }
            UiState.RequestEnableBluetooth -> {
                Log.d(TAG, "Need to enable bluetooth")
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(intent)
            }
            is UiState.AwaitingClient -> {
            }
            is UiState.ClientConnected -> {
            }
            is UiState.Error -> {
                displayError(uiState.errorType)
            }
        }

        bpmText.text = uiState.bpm.toString()
        startButton.visibility = if (uiState.showStart) View.VISIBLE else View.GONE
        status.visibility = if (uiState.showClientStatus) View.VISIBLE else View.GONE
    }

    private fun displayError(error: HeartRateError) {
        getErrorString(error).run {
            if (error.fatal) {
                FatalErrorDialogFragment.newInstance(this)
                    .apply {
                        isCancelable = false
                    }
                    .show(supportFragmentManager, "error")
            } else {
                Toast.makeText(
                    this@MainActivity,
                    this,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getErrorString(error: HeartRateError): String {
        return when (error) {
            HeartRateError.BleHardware -> getString(R.string.error_hardware)
            HeartRateError.BtAdvertise -> getString(R.string.error_advertise)
            HeartRateError.BtGatt -> getString(R.string.error_gatt)
            HeartRateError.PermissionsDenied() -> getString(R.string.permissions_denied)
            else -> error.message ?: getString(R.string.error_unknown)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopServices()
    }
}
