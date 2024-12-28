package com.example.sensor.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.sensor.SensorRepository
import com.example.sensor.ui.model.AccelerometerData

class SensorViewModel : ViewModel() {
    val sensorData: LiveData<AccelerometerData> = SensorRepository.sensorData
}