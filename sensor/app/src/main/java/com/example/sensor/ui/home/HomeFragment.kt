package com.example.sensor.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.sensor.databinding.FragmentHomeBinding
import com.example.sensor.ui.SensorViewModel

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

        // Observe the LiveData for changes
        viewModel.sensorData.observe(viewLifecycleOwner) { data ->
            // Update your TextViews with the accelerometer data
            binding.textViewX.text = "X: %.2f m/s²".format(data.x)
            binding.textViewY.text = "Y: %.2f m/s²".format(data.y)
            binding.textViewZ.text = "Z: %.2f m/s²".format(data.z)

            binding.root.setBackgroundColor(requireContext().getColor(android.R.color.holo_red_dark))

            binding.root.postDelayed({
                // Reset to original background (e.g., white or your default)
                binding.root.setBackgroundColor(requireContext().getColor(android.R.color.white))
            }, 500)
        }

        viewModel.sensorCalibrationData.observe(viewLifecycleOwner) { data ->
            // Update your TextViews with the accelerometer data
            binding.textViewCX.text = "CX: %.2f m/s²".format(data.x)
            binding.textViewCY.text = "CY: %.2f m/s²".format(data.y)
            binding.textViewCZ.text = "CZ: %.2f m/s²".format(data.z)
            binding.textViewCD.text = "CD: %.2f m/s²".format(data.d)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}