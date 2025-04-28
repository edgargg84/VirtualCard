package com.example.qr_scan

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class HotelViewModel : ViewModel() {
    private val _hotelInfo = MutableStateFlow<HotelInfo?>(null)
    val hotelInfo: StateFlow<HotelInfo?> = _hotelInfo.asStateFlow()

    fun processQRCode(qrContent: String) {
        try {
            val json = JSONObject(qrContent)
            val hotelInfo = HotelInfo(
                guestId = json.getString("guestId"),
                guestName = json.getString("guestName"),
                guestEmail = json.getString("guestEmail"),
                checkIn = json.getString("checkInDate"),
                checkOut = json.getString("checkOutDate"),
                hotelId = json.getString("hotelId"),
                roomNumber = json.getString("roomNumber"),
                lockId = json.getString("lockId")
            )
            _hotelInfo.value = hotelInfo
        } catch (e: Exception) {
            // Manejar el error si el JSON no es válido
            _hotelInfo.value = null
        }
    }

    fun clearHotelInfo() {
        _hotelInfo.value = null
    }
}