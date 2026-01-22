package com.example.turystyka.ui.routes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.turystyka.databinding.FragmentRoutesBinding

class RoutesFragment : Fragment() {

    private var _binding: FragmentRoutesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val routesViewModel = ViewModelProvider(this).get(RoutesViewModel::class.java)

        _binding = FragmentRoutesBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // TODO: Observe saved routes from RoutesViewModel and update RecyclerView

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
