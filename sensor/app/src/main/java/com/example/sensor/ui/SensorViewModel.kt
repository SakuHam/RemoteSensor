package com.example.sensor.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.sensor.SensorRepository
import com.example.sensor.ui.model.AccelerometerData
import com.example.sensor.ui.model.CalibrationData

class SensorViewModel : ViewModel() {
    val sensorData: LiveData<AccelerometerData> = SensorRepository.sensorData
    val sensorCalibrationData: LiveData<CalibrationData> = SensorRepository.sensorCalibrationData
    val sensorStatusData: LiveData<Int> = SensorRepository.sensorStatusData
}