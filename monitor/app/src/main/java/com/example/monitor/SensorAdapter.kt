package com.example.monitor

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.monitor.databinding.ItemSensorBinding

class SensorAdapter(
    private val recyclerView: RecyclerView,
    private val onIncreaseSensitivity: (SensorObject) -> Unit,
    private val onDecreaseSensitivity: (SensorObject) -> Unit,
    private val onStatusRequest: (SensorObject) -> Unit
) : ListAdapter<SensorObject, SensorAdapter.SensorViewHolder>(SensorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val binding = ItemSensorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.deviceStatus.setBackgroundColor(parent.context.getColor(android.R.color.white)) // Startup color
        return SensorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        val sensor = getItem(position)
        holder.bind(sensor, onIncreaseSensitivity, onDecreaseSensitivity, onStatusRequest)
    }

    class SensorViewHolder(private val binding: ItemSensorBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("MissingPermission")
        fun bind(sensor: SensorObject, onIncrease: (SensorObject) -> Unit, onDecrease: (SensorObject) -> Unit, onDeviceStatusClicked: (SensorObject) -> Unit) {
            binding.deviceName.text = sensor.device.name ?: "Unknown Device"
            binding.deviceAddress.text = sensor.device.address
            binding.characteristicCount.text = "Characteristics: ${sensor.characteristics.size}"

            binding.deviceStatus.setOnClickListener {
                onDeviceStatusClicked(sensor)
            }

            // Button listeners
            binding.buttonIncreaseSensitivity.setOnClickListener { onIncrease(sensor) }
            binding.buttonDecreaseSensitivity.setOnClickListener { onDecrease(sensor) }

            onDeviceStatusClicked(sensor)
        }

        fun updateDeviceStatusColor(color: Int) {
            binding.deviceStatus.setBackgroundColor(color)
        }
    }

    fun updateDeviceStatus(sensor: SensorObject, color: Int) {
        val position = currentList.indexOf(sensor)
        if (position != -1) {
            // Get the ViewHolder and update the color
            val viewHolder = (recyclerView.findViewHolderForAdapterPosition(position) as? SensorViewHolder)
            viewHolder?.updateDeviceStatusColor(color)

            // Optionally notify the item for any other updates
            notifyItemChanged(position)
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
