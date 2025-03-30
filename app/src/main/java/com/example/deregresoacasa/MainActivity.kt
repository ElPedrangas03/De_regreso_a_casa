package com.example.deregresoacasa

import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.deregresoacasa.ui.theme.DeRegresoACasaTheme
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    private var locationPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            RouteMapScreen(locationPermissionGranted)
        }
    }
}

@Composable
fun RouteMapScreen(locationPermissionGranted: Boolean) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(19.432608, -99.133209), 12f) // Centro de la Ciudad de México, por ejemplo
    }
    var start by remember { mutableStateOf<LatLng?>(null) }
    var end by remember { mutableStateOf<LatLng?>(null) }
    var polyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    val mapUiSettings by remember { mutableStateOf(MapUiSettings(zoomControlsEnabled = false)) }
    val mapProperties by remember {
        mutableStateOf(MapProperties(isMyLocationEnabled = locationPermissionGranted))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = { point ->
                if (start == null) {
                    start = point
                } else if (end == null) {
                    end = point
                    fetchRoute(start!!, end!!) { route ->
                        polyline = route
                    }
                }
            }
        ) {
            start?.let { Marker(state = MarkerState(it), title = "Origen") }
            end?.let { Marker(state = MarkerState(it), title = "Destino") }
            if (polyline.isNotEmpty()) {
                Polyline(points = polyline)
            }
        }

        Button(
            onClick = {
                start = null
                end = null
                polyline = emptyList()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text("Selecciona punto de origen y final")
        }
    }
}

fun fetchRoute(start: LatLng, end: LatLng, onResult: (List<LatLng>) -> Unit) {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openrouteservice.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService = retrofit.create(ApiService::class.java)
    val startCoords = "${start.longitude},${start.latitude}"
    val endCoords = "${end.longitude},${end.latitude}"

    CoroutineScope(Dispatchers.IO).launch {
        val response = apiService.getRoute("ES MI KEY >:", startCoords, endCoords)
        if (response.isSuccessful) {
            val coordinates = response.body()?.features?.first()?.geometry?.coordinates?.map {
                LatLng(it[1], it[0])
            } ?: emptyList()
            onResult(coordinates)
        } else {
            Log.e("RouteError", "No se pudo obtener la ruta")
        }
    }
}