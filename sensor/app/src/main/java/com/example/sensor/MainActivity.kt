package com.example.sensor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.widget.Toast
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
import androidx.lifecycle.ViewModelProvider
import com.example.sensor.databinding.ActivityMainBinding
import com.example.sensor.ui.SensorViewModel
import com.example.sensor.ui.model.AccelerometerData
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var viewModel: SensorViewModel

    companion object {
        private const val TAG = "SensorApp"

        // Example UUIDs (replace with your own)
        val SENSOR_SERVICE_UUID: UUID = UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")
        val SENSOR_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")
        val CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothGattServer: BluetoothGattServer? = null

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

        viewModel = ViewModelProvider(this)[SensorViewModel::class.java]

        val intent = Intent(this, SensorForegroundService::class.java)
        startForegroundService(intent)

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val hasBLE = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!hasBLE) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show()
            finish()
        }

        checkBlePermissions()

        // Set up the GATT Server
        setupGattServer()

        // Start advertising
        startAdvertising()
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // You can handle sensor accuracy changes here if needed
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                // Get the accelerometer values
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                val data = AccelerometerData(x, y, z)
                SensorRepository.updateSensorData(data)
            }
        }
    }

    private fun setupGattServer() {
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
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        val service = BluetoothGattService(
            SENSOR_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val sensorCharacteristic = BluetoothGattCharacteristic(
            SENSOR_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // Descriptor for notifications
        val configDescriptor = BluetoothGattDescriptor(
            CONFIG_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        sensorCharacteristic.addDescriptor(configDescriptor)

        // Add the characteristic to the service
        service.addCharacteristic(sensorCharacteristic)

        // Add the service to the GATT Server
        bluetoothGattServer?.addService(service)
        Log.d(TAG, "GATT Server set up")
        Toast.makeText(this, "GATT Server set up", Toast.LENGTH_SHORT).show()
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to device: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from device: ${device.address}")
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            Log.i(TAG, "Service added: ${service.uuid}, status=$status")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (characteristic.uuid == SENSOR_CHARACTERISTIC_UUID) {
                // Example: return a simple byte
                val data = byteArrayOf(42)
                characteristic.value = data
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
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
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    data
                )
                Log.i(TAG, "Read request from ${device.address}, data=42")
            }
        }
    }

    private fun startAdvertising() {
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SENSOR_SERVICE_UUID))
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
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
        bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.d(TAG, "startAdvertising() called")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.i(TAG, "BLE Advertise Started")
            Toast.makeText(this@MainActivity, "BLE Advertise Started", Toast.LENGTH_SHORT).show()
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "BLE Advertise Failed: $errorCode")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
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
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        bluetoothGattServer?.close()
    }

    private fun checkBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val neededPermissions = mutableListOf<String>()
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (neededPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 1234)
            }
        } else {
            // For older versions, might need location permission for BLE scans
        }
    }
}