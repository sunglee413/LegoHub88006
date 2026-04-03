package com.sung.mylegohub88006

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.sung.mylegohub88006.main.HubScreen
import com.sung.mylegohub88006.main.MainViewModel
import com.sung.mylegohub88006.ui.theme.MyLegoHub88006Theme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) vm.startScan()
        else Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyLegoHub88006Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HubScreen(
                        modifier = Modifier.padding(innerPadding),
                        vm = vm,
                        onScan = ::requestScan
                    )
                }
            }
        }
    }

    private fun requestScan() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) vm.startScan() else permLauncher.launch(missing.toTypedArray())
    }
}