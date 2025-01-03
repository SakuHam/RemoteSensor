package com.example.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.monitor.R
import com.example.monitor.databinding.ItemSensorBinding

class SensorAdapter : ListAdapter<SensorObject, SensorAdapter.SensorViewHolder>(SensorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val binding = ItemSensorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SensorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        val sensor = getItem(position)
        holder.bind(sensor)
    }

    class SensorViewHolder(private val binding: ItemSensorBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("MissingPermission")
        fun bind(sensor: SensorObject) {
            binding.deviceName.text = sensor.device.name ?: "Unknown Device"
            binding.deviceAddress.text = sensor.device.address
            binding.characteristicCount.text = "Characteristics: ${sensor.characteristics.size}"
        }
    }

    class SensorDiffCallback : DiffUtil.ItemCallback<SensorObject>() {
        override fun areItemsTheSame(oldItem: SensorObject, newItem: SensorObject): Boolean {
            return oldItem.device.address == newItem.device.address
        }

        override fun areContentsTheSame(oldItem: SensorObject, newItem: SensorObject): Boolean {
            return oldItem == newItem
        }
    }
}
