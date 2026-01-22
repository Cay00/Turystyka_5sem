package com.example.turystyka.ui.map

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.turystyka.databinding.FragmentMapBinding
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
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.net.URLEncoder

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val selectedPoints = mutableListOf<GeoPoint>()
    private val MAX_POIS = 5 // Limit the number of POIs to visit
    private val routesViewModel: RoutesViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Configuration.getInstance().userAgentValue = requireContext().packageName
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupMap()
        setupButtons()

        return root
    }

    private fun setupMap() {
        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val controller = mapView.controller
        val warsaw = GeoPoint(52.2297, 21.0122)
        controller.setZoom(12.0)
        controller.setCenter(warsaw)

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                addPointToMap(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(0, mapEventsOverlay)
    }

    private fun setupButtons() {
        binding.clearButton.setOnClickListener { clearMap() }
        binding.saveRouteButton.setOnClickListener { saveRoute() }
        binding.routeSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.routeSwitch.text = if (isChecked) "Trasa samochodowa" else "Trasa piesza"
        }
    }

    private fun addPointToMap(geoPoint: GeoPoint) {
        if (selectedPoints.size >= 2) {
            Toast.makeText(requireContext(), "Można wybrać tylko dwa punkty.", Toast.LENGTH_SHORT).show()
            return
        }

        val marker = Marker(binding.mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()

        selectedPoints.add(geoPoint)

        if (selectedPoints.size == 2) {
            lifecycleScope.launch {
                drawRoute(ArrayList(selectedPoints))
            }
        }
    }

    private suspend fun drawRoute(waypoints: ArrayList<GeoPoint>) {
        val startPoint = waypoints.first()
        val endPoint = waypoints.last()

        val roadManager: RoadManager = if (binding.routeSwitch.isChecked) {
            OSRMRoadManager(requireContext(), OSRMRoadManager.MEAN_BY_CAR)
        } else {
            OSRMRoadManager(requireContext(), OSRMRoadManager.MEAN_BY_FOOT)
        }

        // Step 1: Get the initial, direct route
        val initialRoad = withContext(Dispatchers.IO) {
            try {
                roadManager.getRoad(waypoints)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (initialRoad == null || initialRoad.mStatus != Road.STATUS_OK) {
            Toast.makeText(requireContext(), "Could not calculate initial route", Toast.LENGTH_SHORT).show()
            clearMap()
            return
        }

        // Step 2: Find POIs in the bounding box of the initial route
        val (finalRoad, pois) = withContext(Dispatchers.IO) {
            val poiProvider = OverpassAPIProvider()
            
            // Create a search area from the initial route's bounding box, expanded slightly
            val searchArea = initialRoad.mBoundingBox.increaseByScale(1.5f)
            val poiQuery = buildPoisQuery(searchArea)
            val url = "http://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(poiQuery, "UTF-8")

            val foundPois: ArrayList<POI> = try {
                poiProvider.getPOIsFromUrl(url)
            } catch (e: Exception) {
                e.printStackTrace()
                ArrayList()
            }

            val sortedPois = foundPois.sortedBy { it.mLocation.distanceToAsDouble(startPoint) }.take(MAX_POIS)

            val newWaypoints = arrayListOf(startPoint)
            var currentLocation = startPoint
            val remainingPois = sortedPois.toMutableList()

            while (remainingPois.isNotEmpty()) {
                val nearestPoi = remainingPois.minByOrNull { it.mLocation.distanceToAsDouble(currentLocation) }
                if (nearestPoi != null) {
                    newWaypoints.add(nearestPoi.mLocation)
                    currentLocation = nearestPoi.mLocation
                    remainingPois.remove(nearestPoi)
                }
            }
            newWaypoints.add(endPoint)

            val finalRoad = try {
                roadManager.getRoad(newWaypoints)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            Pair(finalRoad, sortedPois)
        }

        if (finalRoad != null && finalRoad.mStatus == Road.STATUS_OK) {
            pois.forEach { poi ->
                val poiMarker = Marker(binding.mapView)
                poiMarker.position = poi.mLocation
                poiMarker.title = poi.mDescription // Correctly gets the 'name' tag
                poiMarker.snippet = poi.mType
                binding.mapView.overlays.add(poiMarker)
            }

            val roadOverlay = RoadManager.buildRoadOverlay(finalRoad)
            binding.mapView.overlays.add(roadOverlay)
            binding.mapView.invalidate()

            val boundingBox = finalRoad.mBoundingBox
            binding.mapView.zoomToBoundingBox(boundingBox, true, 100)

            binding.clearButton.visibility = View.VISIBLE
            binding.clearButton.setBackgroundColor(Color.RED)
            binding.saveRouteButton.visibility = View.VISIBLE
            binding.saveRouteButton.setBackgroundColor(Color.GREEN)
        } else {
            Toast.makeText(requireContext(), "Error retrieving final route", Toast.LENGTH_SHORT).show()
            clearMap()
        }
    }

    private fun buildPoisQuery(box: BoundingBox): String {
        val bboxStr = "(${box.latSouth},${box.lonWest},${box.latNorth},${box.lonEast})"
        val query = "[out:json][timeout:25];" +
                "(" +
                "node[\"historic\"~\"castle|ruins|building\"]" + bboxStr + ";" +
                "way[\"historic\"~\"castle|ruins|building\"]" + bboxStr + ";" +
                "node[\"building\"=\"church\"]" + bboxStr + ";" +
                "way[\"building\"=\"church\"]" + bboxStr + ";" +
                ");" +
                "out body;>;out skel qt;"
        return query
    }

    private fun saveRoute() {
        val routeName = "Trasa z mapy"
        routesViewModel.saveRoute(routeName)
        Toast.makeText(requireContext(), "Trasa zapisana!", Toast.LENGTH_SHORT).show()
        binding.saveRouteButton.visibility = View.GONE
    }

    private fun clearMap() {
        binding.mapView.overlays.clear()
        selectedPoints.clear()
        binding.clearButton.visibility = View.GONE
        binding.saveRouteButton.visibility = View.GONE

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                addPointToMap(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }
        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        binding.mapView.overlays.add(0, mapEventsOverlay)
        binding.mapView.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
