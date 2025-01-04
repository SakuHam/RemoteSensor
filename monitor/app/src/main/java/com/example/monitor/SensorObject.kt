package com.example.monitor

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

data class SensorObject(val device: BluetoothDevice, var gatt: BluetoothGatt?, val characteristics: MutableList<BluetoothGattCharacteristic>)
