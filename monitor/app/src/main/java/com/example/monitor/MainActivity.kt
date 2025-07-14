package com.example.monitor

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.monitor.databinding.ActivityMainBinding
import com.example.monitor.ui.SensorViewModel
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG = "MonitorApp"
        val SENSOR_SERVICE_UUID: UUID = UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")
        val SENSOR_STATUS_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")
        val SENSOR_CALIBRATE_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-01805f9b34fb")
    }

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    private lateinit var sensorViewModel: SensorViewModel
    private lateinit var sensorAdapter: SensorAdapter
    @Volatile
    var pendingSensor: SensorObject? = null

    private var scanTimeoutHandler:Handler? = null

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

        val recyclerView = findViewById<RecyclerView>(R.id.sensorRecyclerView)
        sensorAdapter = SensorAdapter(
            recyclerView,
            onIncreaseSensitivity = { sensor ->
                writeSensitivityValue(sensor, 0.01f)
            },
            onDecreaseSensitivity = { sensor ->
                writeSensitivityValue(sensor, -0.01f)
            },
            onStatusRequest = { sensor ->
                writeStatusValue(sensor, intArrayOf(1))
            }
        )
        recyclerView.adapter = sensorAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        sensorViewModel = ViewModelProvider(this).get(SensorViewModel::class.java)

        // Observe sensors LiveData
        sensorViewModel.sensors.observe(this) { sensors ->
            sensorAdapter.submitList(sensors.values.toList())
        }

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

        lifecycleScope.launch {
            sensorViewModel.colorFlow.collect { (sensor, color) ->
                sensorAdapter.updateDeviceStatus(sensor, color)
            }
        }
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

    @SuppressLint("MissingPermission")
    fun startScanForSensor() {
        sensorViewModel.forget()
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SENSOR_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        Log.d(TAG, "Started BLE scan for Sensor")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            pendingSensor = SensorObject(device, null, mutableListOf())
            if (!sensorViewModel.addSensor(pendingSensor!!)) {
                connectToDevice(device)
            }

            Log.i(TAG, "Found device: ${device.address} (name=${device.name})")
            if (scanTimeoutHandler == null) {
                scanTimeoutHandler = Handler(Looper.getMainLooper())
                scanTimeoutHandler!!.postDelayed({
                    bluetoothLeScanner.stopScan(this)
                    scanTimeoutHandler = null
                    Log.d(TAG, "Stopped BLE scan after timeout")
                    toast("Stopped BLE scan after timeout")
                }, 5000)
            }
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
        val gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        pendingSensor!!.gatt = gatt
        Log.i(TAG, "Connecting to GATT server on device: ${device.address}")
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
//                toast("Disconnected from GATT server")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered")
                val service = gatt.getService(SENSOR_SERVICE_UUID)
                if (service != null) {
                    var characteristic = service.getCharacteristic(SENSOR_CALIBRATE_UUID)
                    if (characteristic != null) {
                        pendingSensor?.characteristics?.add(characteristic)

                        gatt.readCharacteristic(characteristic)

                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor =
                            characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                    characteristic = service.getCharacteristic(SENSOR_STATUS_UUID)
                    if (characteristic != null) {
                        pendingSensor?.characteristics?.add(characteristic)
                        gatt.readCharacteristic(characteristic)

                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor =
                            characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
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
//                val data = characteristic.value.toString(Charsets.UTF_8)
//                Log.i(TAG, "onCharacteristicRead: $data")
//                toast("onCharacteristicRead: $data")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic.uuid == SENSOR_CALIBRATE_UUID) {
                val data = byteArrayToFloats(characteristic.value)
                toast("Calibrating ${data[3]}")
            }
            if (characteristic.uuid == SENSOR_STATUS_UUID) {
                val data = byteArrayToInts(characteristic.value)[0]
                val sensor = sensorAdapter.currentList.find { it.gatt == gatt }
                if (sensor != null) {
                    val color = when (data) {
                        0 -> android.graphics.Color.WHITE
                        1 -> android.graphics.Color.RED
                        2 -> android.graphics.Color.GREEN
                        else -> android.graphics.Color.WHITE
                    }

                    sensorViewModel.emitSensorColor(sensor, color)
                }
            }
        }

        fun byteArrayToFloats(byteArray: ByteArray): FloatArray {
            val byteBuffer = ByteBuffer.wrap(byteArray)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN) // Ensure the same byte order is used
            val floats = FloatArray(byteArray.size / 4) // 4 bytes per float
            for (i in floats.indices) {
                floats[i] = byteBuffer.getFloat()
            }
            return floats
        }

        fun byteArrayToInts(byteArray: ByteArray): IntArray {
            val byteBuffer = ByteBuffer.wrap(byteArray)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN) // Ensure the same byte order is used
            val ints = IntArray(byteArray.size / 4) // 4 bytes per float
            for (i in ints.indices) {
                ints[i] = byteBuffer.getInt()
            }
            return ints
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
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

    @SuppressLint("MissingPermission")
    fun writeSensitivityValue(sensor: SensorObject, sensitivity: Float) {
        val gatt = sensor.gatt
        val service = gatt?.getService(SENSOR_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(SENSOR_CALIBRATE_UUID)

        if (characteristic != null) {
            val byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.putFloat(sensitivity)
            characteristic.value = byteBuffer.array()

            gatt.writeCharacteristic(characteristic)
            Log.i(TAG, "Sent sensitivity value: $sensitivity to device: ${sensor.device.address}")
        } else {
            Log.e(TAG, "Characteristic for sensitivity not found on device: ${sensor.device.address}")
        }
    }

    private fun intsToByteArray(ints: IntArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(ints.size * 4) // 4 bytes per float
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN) // BLE typically uses little-endian
        ints.forEach { byteBuffer.putInt(it) }
        return byteBuffer.array()
    }

    @SuppressLint("MissingPermission")
    fun writeStatusValue(sensor: SensorObject, status: IntArray) {
        val gatt = sensor.gatt
        val service = gatt?.getService(SENSOR_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(SENSOR_STATUS_UUID)

        if (characteristic != null) {
            characteristic.value = intsToByteArray(status)

            gatt.writeCharacteristic(characteristic)
        } else {
            Log.e(TAG, "Characteristic for status not found on device: ${sensor.device.address}")
        }
    }
}