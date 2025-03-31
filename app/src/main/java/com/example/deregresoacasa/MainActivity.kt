package com.example.deregresoacasa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver

class MainActivity : ComponentActivity() {

    private val LOCATION_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName

        // Solicitar permisos directamente al iniciar
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        }

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
    var homeLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val selectedMarker = remember { mutableStateOf<Marker?>(null) }
    val selectedCoordinates = remember { mutableStateOf<GeoPoint?>(null) }

    // Función para cargar ubicación guardada
    fun getSavedHomeLocation(context: Context): GeoPoint? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("home_lat", 0f)
        val lon = prefs.getFloat("home_lon", 0f)
        return if (lat != 0f && lon != 0f) GeoPoint(lat.toDouble(), lon.toDouble()) else null
    }

    // Función para guardar ubicación
    fun saveHomeLocation(context: Context, location: GeoPoint) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("home_lat", location.latitude.toFloat())
            .putFloat("home_lon", location.longitude.toFloat())
            .apply()
    }

    LaunchedEffect(locationPermission.status) {
        if (locationPermission.status.isGranted) {
            getCurrentLocation(context) { location ->
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setMultiTouchControls(true)

                userLocation = GeoPoint(location.latitude, location.longitude)
                val mapController = mapView.controller
                mapController.setZoom(15.0)
                mapController.setCenter(userLocation)

                // Agrega marcador de ubicación actual
                val drawable = ContextCompat.getDrawable(context, R.drawable.alfiler)
                val bitmap = (drawable as BitmapDrawable).bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 20, 20, false)
                val scaledDrawable = BitmapDrawable(context.resources, scaledBitmap)

                // Agregar marcador pa la casa
                val drawableCasa = ContextCompat.getDrawable(context, R.drawable.casa)
                val bitmapCasa = (drawableCasa as BitmapDrawable).bitmap
                val scaledBitmapCasa = Bitmap.createScaledBitmap(bitmapCasa, 20, 20, false)
                val scaledDrawableCasa = BitmapDrawable(context.resources, scaledBitmapCasa)

                // La ubicacion actual pibe xd
                val startMarker = Marker(mapView).apply {
                    position = userLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = scaledDrawable
                    title = "Ubicación actual"
                }
                mapView.overlays.add(startMarker)

                // Cargar ubicación de casa si existe
                homeLocation = getSavedHomeLocation(context)
                homeLocation?.let {
                    val homeMarker = Marker(mapView).apply {
                        position = it
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = scaledDrawableCasa
                        title = "Mi casa"
                    }
                    mapView.overlays.add(homeMarker)
                }

                // Evento al tocar el mapa y aparezca un icono
                val receiver = object : org.osmdroid.events.MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let {
                            // Eliminar marcador anterior si existe
                            selectedMarker.value?.let { mapView.overlays.remove(it) }

                            val touchMarker = Marker(mapView).apply {
                                position = it
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                icon = scaledDrawable
                                title = "Marcador"
                            }
                            selectedMarker.value = touchMarker
                            selectedCoordinates.value = it
                            Log.d("Las coordenadas we", selectedCoordinates.toString())

                            mapView.overlays.add(touchMarker)
                            mapView.invalidate()
                        }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }

                val overlayEventos = org.osmdroid.views.overlay.MapEventsOverlay(receiver)
                mapView.overlays.add(overlayEventos)

                mapView.invalidate()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView }
            )

            // Botón para acercar y alejar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { mapView.controller.zoomIn() }) {
                    Text("+")
                }

                Button(onClick = { mapView.controller.zoomOut() }) {
                    Text("-")
                }
            }

            // Botón centrar
            Button(
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
            }

            // Botón para establecer ubicación de casa
            Button(
                onClick = {
                    selectedCoordinates.value?.let { selectedPoint ->
                        // Esto si elimina la anterior casita y carga la nueva
                        mapView.overlays.removeAll {
                            it is Marker && it.title == "Mi casa"
                        }

                        val drawableCasa = ContextCompat.getDrawable(context, R.drawable.casa)
                        val bitmapCasa = (drawableCasa as BitmapDrawable).bitmap
                        val scaledBitmapCasa = Bitmap.createScaledBitmap(bitmapCasa, 20, 20, false)
                        val scaledDrawableCasa = BitmapDrawable(context.resources, scaledBitmapCasa)

                        // Guardar coordenadas seleccionadas como casa
                        saveHomeLocation(context, selectedPoint)

                        val homeMarker = Marker(mapView).apply {
                            position = selectedPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = scaledDrawableCasa
                            title = "Mi casa"
                        }
                        mapView.overlays.add(homeMarker)
                        mapView.invalidate()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text("Establecer casa")
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