package com.example.sensor.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.sensor.BroadcastActions
import com.example.sensor.SensorRepository
import com.example.sensor.databinding.FragmentHomeBinding
import com.example.sensor.ui.SensorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var viewModel: SensorViewModel

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewModel = ViewModelProvider(requireActivity())[SensorViewModel::class.java]

        SensorRepository.sensorData.observe(viewLifecycleOwner) {
            SensorRepository.sensorData.value?.let { it1 -> viewModel.updateSensorStatus(it1) }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.distinctSensorStatusFlow.collectLatest { data ->
                if (data != null) {
                    binding.textViewX.text = "X: %.2f m/s²".format(data.x)
                    binding.textViewY.text = "Y: %.2f m/s²".format(data.y)
                    binding.textViewZ.text = "Z: %.2f m/s²".format(data.z)
                }

                binding.root.setBackgroundColor(requireContext().getColor(android.R.color.holo_red_dark))

                val requestIntent = Intent(BroadcastActions.ACTION_STATUS_REQUEST)
                requestIntent.putExtra("STATUS", 1)
                requireActivity().sendBroadcast(requestIntent)

                delay(500)

                // Check if view is still valid (e.g., avoid crash after rotation)
                if (_binding != null) {
                    binding.root.setBackgroundColor(requireContext().getColor(android.R.color.white))

                    val resetIntent = Intent(BroadcastActions.ACTION_STATUS_REQUEST)
                    resetIntent.putExtra("STATUS", 0)
                    requireActivity().sendBroadcast(resetIntent)
                }
            }
        }

        viewModel.sensorCalibrationData.observe(viewLifecycleOwner) { data ->
            // Update your TextViews with the accelerometer data
            binding.textViewCX.text = "CX: %.2f m/s²".format(data.x)
            binding.textViewCY.text = "CY: %.2f m/s²".format(data.y)
            binding.textViewCZ.text = "CZ: %.2f m/s²".format(data.z)
            binding.textViewCD.text = "CD: %.2f m/s²".format(data.d)
        }

        /*
        viewModel.sensorStatusFlow.observe(viewLifecycleOwner) { data ->
            binding.root.setBackgroundColor(requireContext().getColor(android.R.color.holo_green_dark))

            val requestIntent = Intent(BroadcastActions.ACTION_STATUS_REQUEST)
            requestIntent.putExtra("STATUS", 2)
            requireActivity().sendBroadcast(requestIntent)

            binding.root.postDelayed({
                // Reset to original background (e.g., white or your default)
                binding.root.setBackgroundColor(requireContext().getColor(android.R.color.white))

                val requestIntent = Intent(BroadcastActions.ACTION_STATUS_REQUEST)
                requestIntent.putExtra("STATUS", 0)
                requireActivity().sendBroadcast(requestIntent)
            }, 500)
        }

         */

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}