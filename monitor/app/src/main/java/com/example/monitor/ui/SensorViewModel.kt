package com.example.monitor.ui

import android.annotation.SuppressLint
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.monitor.SensorObject

class SensorViewModel : ViewModel() {
    private val _sensors = MutableLiveData<Map<String, SensorObject>>(emptyMap())
    val sensors: LiveData<Map<String, SensorObject>> get() = _sensors

    fun updateSensors(sensorMap: Map<String, SensorObject>) {
        _sensors.value = sensorMap
    }

    fun addSensor(sensor: SensorObject): Boolean {
        val currentMap = _sensors.value ?: emptyMap()
        if (!currentMap.containsKey(sensor.device.address)) {
            val updatedMap = currentMap.toMutableMap()
            updatedMap[sensor.device.address ?: "Unknown Device"] = sensor
            _sensors.value = updatedMap
            return false
        }
        return true
    }

    fun contains(sensor: SensorObject): Boolean {
        val currentMap = _sensors.value ?: emptyMap()
        return currentMap.containsKey(sensor.device.address)
    }

    @SuppressLint("MissingPermission")
    fun forget() {
        val currentMap = _sensors.value ?: emptyMap()

        // Disconnect and close all GATT connections
        currentMap.values.forEach { sensor ->
            sensor.gatt?.disconnect()
            sensor.gatt?.close()
        }

        // Clear the sensors map
        _sensors.value = emptyMap()
    }
}