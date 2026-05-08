package com.example.ar

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.ar.antenna.AntennaType
import com.example.ar.satellite.Satellite
import com.example.ar.tdt.TdtTransmitter
import com.example.ar.wifi.WifiNetwork

data class TargetPoint(
    val lat: Double,
    val lon: Double,
    val name: String = "Objetivo"
)

class SharedViewModel : ViewModel() {
    val target            = MutableLiveData<TargetPoint?>(null)
    val selectedSatellite = MutableLiveData<Satellite?>(null)
    val antennaType       = MutableLiveData<AntennaType>(AntennaType.SATELLITE)
    val trackedWifi       = MutableLiveData<WifiNetwork?>(null)
    val liveWifiRssi      = MutableLiveData<Int?>(null)
    val selectedTdt       = MutableLiveData<TdtTransmitter?>(null)
    val isPro             = MutableLiveData<Boolean>(false)
}
