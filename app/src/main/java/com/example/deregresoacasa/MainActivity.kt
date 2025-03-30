package com.example.deregresoacasa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.*
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            RouteMapScreen()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RouteMapScreen() {
    val locationPermission = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(locationPermission.status) {
        if (locationPermission.status.isGranted) {
            getCurrentLocation(context) { location ->
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setMultiTouchControls(true)

                userLocation = GeoPoint(location.latitude, location.longitude)
                val mapController = mapView.controller
                mapController.setZoom(15.0)
                mapController.setCenter(userLocation)

                val drawable = ContextCompat.getDrawable(context, R.drawable.casa)
                val bitmap = (drawable as BitmapDrawable).bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 30, 30, false)
                val scaledDrawable = BitmapDrawable(context.resources, scaledBitmap)

                val startMarker = Marker(mapView).apply {
                    position = userLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = scaledDrawable
                    title = "Ubicación actual"
                }
                mapView.overlays.add(startMarker)
                mapView.invalidate()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!locationPermission.status.isGranted) {
            Button(onClick = { locationPermission.launchPermissionRequest() }) {
                Text("Permitir ubicación")
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { mapView }
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = { mapView.controller.zoomIn() }) {
                        Text("+")
                    }

                    Button(onClick = { mapView.controller.zoomOut() }) {
                        Text("-")
                    }
                }

                /*Button(
                    onClick = {
                        userLocation?.let {
                            mapView.controller.setCenter(it)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text("Centrar en mi ubicación")
                }*/
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun getCurrentLocation(context: Context, onLocationReceived: (Location) -> Unit) {
    val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationProviderClient.lastLocation
        .addOnSuccessListener { location: Location? ->
            location?.let { onLocationReceived(it) }
        }
        .addOnFailureListener { e ->
            Log.e("LocationError", "No se pudo obtener la ubicación", e)
        }
}