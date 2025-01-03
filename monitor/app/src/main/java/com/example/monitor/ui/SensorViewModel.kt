package com.example.monitor.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.monitor.SensorObject

class SensorViewModel : ViewModel() {
    private val _sensors = MutableLiveData<List<SensorObject>>(emptyList())
    val sensors: LiveData<List<SensorObject>> get() = _sensors

    fun updateSensors(sensorList: List<SensorObject>) {
        _sensors.value = sensorList
    }

    fun addSensor(sensor: SensorObject) {
        val updatedList = _sensors.value?.toMutableList() ?: mutableListOf()
        updatedList.add(sensor)
        _sensors.value = updatedList
    }
}