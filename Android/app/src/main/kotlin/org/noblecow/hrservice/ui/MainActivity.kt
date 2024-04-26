package org.noblecow.hrservice.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.createGraph
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.mikepenz.aboutlibraries.ui.LibsSupportFragment
import dagger.hilt.android.AndroidEntryPoint
import org.noblecow.hrservice.R
import org.noblecow.hrservice.databinding.ActivityMainBinding

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        val navHostFragment = supportFragmentManager.findFragmentById(
            binding.navHostFragment.id
        ) as NavHostFragment
        val navController = navHostFragment.navController.apply {
            addOnDestinationChangedListener { _, destination, _ ->
                binding.topAppBar.isTitleCentered = destination.route == graph.startDestinationRoute
            }
        }
        navController.graph = navController.createGraph(
            startDestination = "main"
        ) {
            fragment<MainFragment>(NavRoutes.MAIN) {
                label = resources.getString(R.string.app_name)
            }
            fragment<LibsSupportFragment>(NavRoutes.LIBRARIES) {
                label = resources.getString(R.string.title_libraries)
            }
        }
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Devices with a display should not go to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(binding.navHostFragment.id)

        return navController.navigateUp(
            appBarConfiguration
        ) || super.onSupportNavigateUp()
    }
}