package com.example.sensor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.sensor.ui.model.AccelerometerData
import com.example.sensor.ui.model.CalibrationData

object SensorRepository {
    // Could be LiveData, Flow, or something else
    private val _sensorData = MutableLiveData<AccelerometerData>()
    val sensorData: LiveData<AccelerometerData> get() = _sensorData

    fun updateSensorData(newData: AccelerometerData) {
        _sensorData.value = newData
    }

    private val _sensorCalibrationData = MutableLiveData<CalibrationData>()
    val sensorCalibrationData: LiveData<CalibrationData> get() = _sensorCalibrationData

    fun updateSensorCalibrationData(newData: CalibrationData) {
        _sensorCalibrationData.postValue(newData)
    }

    private val _sensorStatusData = MutableLiveData<Int>()
    val sensorStatusData: LiveData<Int> get() = _sensorStatusData

    fun updateSensorStatusData(newData: Int) {
        _sensorStatusData.value = newData
    }
}