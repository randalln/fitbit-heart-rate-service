package org.noblecow.hrservice.ui

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.noblecow.hrservice.MainWorker
import org.noblecow.hrservice.NavRoutes
import org.noblecow.hrservice.R
import org.noblecow.hrservice.data.repository.ServicesState
import org.noblecow.hrservice.databinding.FragmentMainBinding
import org.slf4j.LoggerFactory

private const val TAG = "MainFragment"
private const val WORKER_NAME = "mainWorker"

@AndroidEntryPoint
internal class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()

    private lateinit var bpmText: TextView
    private lateinit var startButton: Button
    private lateinit var clientStatus: TextView
    private lateinit var progressBar: ProgressBar

    private val logger = LoggerFactory.getLogger(TAG)
    private var workOperation: Operation? = null

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
                viewModel.start()
            }
        }
        clientStatus = binding.status
        progressBar = binding.progressBar

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mainUiState.collect {
                onStateChange(it)
            }
        }

        return binding.root
    }

    private fun onStateChange(mainUiState: MainUiState) {
        if (mainUiState.servicesState == ServicesState.Started) {
            workOperation = null
        }

        // UI updates
        startButton.toggleVisibilityAsNeeded {
            mainUiState.servicesState == ServicesState.Stopped &&
                !mainUiState.startAndroidService
        }
        clientStatus.toggleVisibilityAsNeeded {
            mainUiState.servicesState == ServicesState.Started && !mainUiState.isClientConnected
        }
        progressBar.toggleVisibilityAsNeeded {
            mainUiState.servicesState == ServicesState.Starting
        }
        if (bpmText.text != mainUiState.bpm.toString()) {
            bpmText.text = mainUiState.bpm.toString()
        }

        // Actions
        mainUiState.permissionsRequested?.let {
            logger.debug("Requesting permissions: $it")
            requestMultiplePermissions.launch(it.toTypedArray())
        }
        if (!mainUiState.isBluetoothEnabled) {
            logger.debug("Need to enable bluetooth")
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        }
        if (mainUiState.startAndroidService) {
            startAndroidService()
        }
        mainUiState.userMessage?.let {
            Snackbar.make(startButton, it, Snackbar.LENGTH_LONG).show()
            viewModel.userMessageShown()
        }
    }

    private fun View.toggleVisibilityAsNeeded(visible: () -> Boolean) {
        val newState = if (visible.invoke()) View.VISIBLE else View.GONE
        if (newState != this.visibility) {
            this.visibility = newState
        }
    }

    private fun startAndroidService() {
        if (workOperation == null) {
            logger.debug("Starting Android Service")
            val workRequest = OneTimeWorkRequestBuilder<MainWorker>().build()
            workOperation = WorkManager.getInstance(requireContext())
                .enqueueUniqueWork(WORKER_NAME, ExistingWorkPolicy.KEEP, workRequest)
            viewModel.androidServiceStarted()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.main_menu, menu)
                }

                @SuppressLint("MissingPermission")
                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_libraries -> {
                            findNavController().navigate(NavRoutes.LIBRARIES)
                            true
                        }
                        R.id.action_fake_bpm -> {
                            viewModel.toggleFakeBPM()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner
        )
    }
}
