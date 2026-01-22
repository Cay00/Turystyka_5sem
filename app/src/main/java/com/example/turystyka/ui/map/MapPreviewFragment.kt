package com.example.turystyka.ui.map

import android.content.Context
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.turystyka.databinding.FragmentMapPreviewBinding
import com.example.turystyka.ui.routes.RoutesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.location.OverpassAPIProvider
import org.osmdroid.bonuspack.location.POI
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.io.IOException
import java.net.URLEncoder

class MapPreviewFragment : Fragment() {

    private var _binding: FragmentMapPreviewBinding? = null
    private val binding get() = _binding!!
    private val routesViewModel: RoutesViewModel by activityViewModels()
    private val args: MapPreviewFragmentArgs by navArgs()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Configuration.getInstance().userAgentValue = requireContext().packageName
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapPreviewBinding.inflate(inflater, container, false)
        setupMap()

        lifecycleScope.launch {
            val startPoint = geocodeAddress(args.startAddress)
            val endPoint = geocodeAddress(args.endAddress)

            if (startPoint != null && endPoint != null) {
                drawRoute(arrayListOf(startPoint, endPoint))
            } else {
                Toast.makeText(requireContext(), "Nie można znaleźć adresów", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }

        binding.savePreviewButton.setOnClickListener {
            val routeName = "Trasa z ${args.startAddress} do ${args.endAddress}"
            routesViewModel.saveRoute(routeName)
            Toast.makeText(requireContext(), "Trasa zapisana!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        return binding.root
    }

    private fun setupMap() {
        binding.mapPreviewView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        binding.mapPreviewView.setMultiTouchControls(true)
    }

    private suspend fun drawRoute(waypoints: ArrayList<GeoPoint>) {
        val road = withContext(Dispatchers.IO) {
            val roadManager = OSRMRoadManager(requireContext(), if (args.isCarRoute) OSRMRoadManager.MEAN_BY_CAR else OSRMRoadManager.MEAN_BY_FOOT)
            roadManager.getRoad(waypoints)
        }

        if (road.mStatus == Road.STATUS_OK) {
            val roadOverlay = RoadManager.buildRoadOverlay(road)
            binding.mapPreviewView.overlays.add(roadOverlay)
            binding.mapPreviewView.invalidate()

            val boundingBox = BoundingBox.fromGeoPoints(road.mRouteHigh)
            binding.mapPreviewView.zoomToBoundingBox(boundingBox, true, 100)

        } else {
            Toast.makeText(requireContext(), "Error retrieving route", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun geocodeAddress(address: String): GeoPoint? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(requireContext())
            val addresses = geocoder.getFromLocationName(address, 1)
            if (addresses?.isNotEmpty() == true) {
                val location = addresses[0]
                GeoPoint(location.latitude, location.longitude)
            } else null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
