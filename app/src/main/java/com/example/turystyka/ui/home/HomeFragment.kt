package com.example.turystyka.ui.home

import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.turystyka.databinding.FragmentHomeBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var geocodingJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupAddressAutoComplete(binding.startAddress)
        setupAddressAutoComplete(binding.endAddress)

        binding.generateRouteButton.setOnClickListener {
            val startAddress = binding.startAddress.text.toString()
            val endAddress = binding.endAddress.text.toString()

            if (startAddress.isBlank() || endAddress.isBlank()) {
                Toast.makeText(requireContext(), "Wprowadź adres początkowy i końcowy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isCarRoute = binding.homeRouteSwitch.isChecked

            val action = HomeFragmentDirections.actionNavigationHomeToMapPreviewFragment(
                startAddress = startAddress,
                endAddress = endAddress,
                isCarRoute = isCarRoute
            )
            findNavController().navigate(action)
        }

        return root
    }

    private fun setupAddressAutoComplete(autoCompleteTextView: AutoCompleteTextView) {
        autoCompleteTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                geocodingJob?.cancel()
                geocodingJob = lifecycleScope.launch {
                    delay(500) // Debounce to avoid too many requests
                    s?.let { query ->
                        if (query.length > 2) {
                            val suggestions = getAddressSuggestions(query.toString())
                            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, suggestions)
                            autoCompleteTextView.setAdapter(adapter)
                            autoCompleteTextView.showDropDown()
                        }
                    }
                }
            }
        })
    }

    private suspend fun getAddressSuggestions(query: String): List<String> {
        return try {
            val geocoder = Geocoder(requireContext())
            val addresses: List<Address>? = geocoder.getFromLocationName(query, 5)
            addresses?.mapNotNull { it.getAddressLine(0) } ?: emptyList()
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
