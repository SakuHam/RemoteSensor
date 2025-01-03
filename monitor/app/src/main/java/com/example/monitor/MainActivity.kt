package com.example.monitor

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.monitor.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG = "MonitorApp"
        val SENSOR_SERVICE_UUID: UUID = UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")
        val SENSOR_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")
        val SENSOR_CALIBRATE_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-01805f9b34fb")
    }

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        val hasBLE = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!hasBLE) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show()
            finish()
        }

        checkBlePermissions()

//        startScanForSensor()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    fun startScanForSensor() {
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SENSOR_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
//        bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
        Log.d(TAG, "Started BLE scan for Sensor")
        Toast.makeText(this, "Started BLE scan for Sensor", Toast.LENGTH_SHORT).show()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            Log.i(TAG, "Found device: ${device.address} (name=${device.name})")
            Toast.makeText(this@MainActivity, "Found device: ${device.address} (name=${device.name})", Toast.LENGTH_SHORT).show()

            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    android.Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothLeScanner.stopScan(this)
            connectToDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE scan failed with code $errorCode")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.i(TAG, "Connecting to GATT server on device: ${device.address}")
        toast("Connecting to GATT server on device: ${device.address}")
    }

    private fun toast(text: String) {
        runOnUiThread {
            Toast.makeText(
                this@MainActivity,
                text,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server, discovering services...")
                toast("Connected to GATT server, discovering services...")
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server")
                toast("Disconnected from GATT server")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered")
                toast("Services discovered")
                val service = gatt.getService(SENSOR_SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(SENSOR_CALIBRATE_UUID)
                    if (characteristic != null) {
                        // Read it once
                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        gatt.readCharacteristic(characteristic)

                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed, status=$status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic.value.toString(Charsets.UTF_8)
                Log.i(TAG, "onCharacteristicRead: $data?")
                toast("onCharacteristicRead: $data?")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val data = characteristic.value.toString(Charsets.UTF_8)
            Log.i(TAG, "onCharacteristicChanged: $data?")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothGatt?.close()
    }

    private fun ByteArray?.toHexString() = this?.joinToString(" ") {
        String.format("%02X", it)
    } ?: "null"

    private fun checkBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val neededPermissions = mutableListOf<String>()
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            }
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (neededPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 4321)
            }
        } else {
            // Possibly request location if < Android 12
        }
    }
}