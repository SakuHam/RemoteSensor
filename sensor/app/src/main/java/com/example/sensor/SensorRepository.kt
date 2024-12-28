package com.example.sensor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.sensor.ui.model.AccelerometerData

object SensorRepository {
    // Could be LiveData, Flow, or something else
    private val _sensorData = MutableLiveData<AccelerometerData>()
    val sensorData: LiveData<AccelerometerData> get() = _sensorData

    fun updateSensorData(newData: AccelerometerData) {
        _sensorData.value = newData
    }
}