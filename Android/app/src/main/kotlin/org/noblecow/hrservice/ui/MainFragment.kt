package org.noblecow.hrservice.ui

import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.noblecow.hrservice.BuildConfig
import org.noblecow.hrservice.R
import org.noblecow.hrservice.databinding.FragmentMainBinding

private const val TAG = "MainFragment"

@AndroidEntryPoint
class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val viewModel: HRViewModel by viewModels()

    private lateinit var bpmText: TextView
    private lateinit var startButton: Button
    private lateinit var clientStatus: TextView

    private val requestMultiplePermissions = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions ->
        viewModel.receivePermissions(permissions)
    }

    private val enableBluetoothLauncher =
        registerForActivityResult(StartActivityForResult()) {
            if (it.resultCode != RESULT_OK) {
                viewModel.userDeclinedBluetoothEnable()
            } else {
                viewModel.start()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        bpmText = binding.textBpm
        startButton = binding.start.apply {
            setOnClickListener {
                // viewModel.confirmPermissions()
                viewModel.start()
            }
        }
        clientStatus = binding.status

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect {
                onStateChange(it)
            }
        }

        return binding.root
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
        clientStatus.visibility = if (uiState.showClientStatus) View.VISIBLE else View.GONE
    }

    private fun displayError(error: GeneralError) {
        getErrorString(error).run {
            if (error.fatal) {
                FatalErrorDialogFragment.newInstance(this)
                    .apply {
                        isCancelable = false
                    }
                    .show(childFragmentManager, "error")
            } else {
                Toast.makeText(
                    activity,
                    this,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getErrorString(error: GeneralError): String {
        return when (error) {
            GeneralError.BleHardware -> getString(R.string.error_hardware)
            GeneralError.BtAdvertise -> getString(R.string.error_advertise)
            is GeneralError.Ktor -> getString(R.string.error_ktor, error.message)
            GeneralError.PermissionsDenied() -> getString(R.string.permissions_denied)
            else -> error.message ?: getString(R.string.error_unknown)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.main_menu, menu)
                    if (BuildConfig.DEBUG) {
                        menu.findItem(R.id.action_fake_bpm).isVisible = true
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_libraries -> {
                            findNavController().navigate(NavRoutes.LIBRARIES)
                            true
                        }
                        R.id.action_fake_bpm -> {
                            viewModel.uiState.value.let { uiState ->
                                if (uiState is UiState.AwaitingClient ||
                                    uiState is UiState.ClientConnected
                                ) {
                                    viewModel.toggleFakeBPM()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner
        )
    }

    override fun onStop() {
        super.onStop()
        viewModel.stop()
    }
}
