package com.example.monitor.ui

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

    fun addSensor(sensor: SensorObject) {
        val currentMap = _sensors.value ?: emptyMap()
        if (!currentMap.containsKey(sensor.device.address)) {
            val updatedMap = currentMap.toMutableMap()
            updatedMap[sensor.device.address ?: "Unknown Device"] = sensor
            _sensors.value = updatedMap
        }
    }
}