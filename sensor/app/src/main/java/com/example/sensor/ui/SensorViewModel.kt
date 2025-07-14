package com.example.sensor.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.sensor.SensorRepository
import com.example.sensor.ui.model.AccelerometerData
import com.example.sensor.ui.model.CalibrationData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

class SensorViewModel : ViewModel() {
    // Internal flow (mutable)
    private val _sensorStatusFlow = MutableStateFlow<AccelerometerData?>(null)

    // Public flow (read-only, filtered and distinct)
    val sensorStatusFlow: StateFlow<AccelerometerData?> = _sensorStatusFlow

    val distinctSensorStatusFlow = sensorStatusFlow
        .filterNotNull()
        .distinctUntilChanged()

    fun updateSensorStatus(newStatus: AccelerometerData) {
        _sensorStatusFlow.value = newStatus
    }

    val sensorData: LiveData<AccelerometerData> = SensorRepository.sensorData
    val sensorCalibrationData: LiveData<CalibrationData> = SensorRepository.sensorCalibrationData
}