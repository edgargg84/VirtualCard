package com.example.qr_scan

data class HotelInfo(
    val guestId: String,
    val guestName: String,
    val guestEmail: String,
    val hotelId: String,
    val roomNumber: String,
    val lockId: String,
    val checkIn: String,
    val checkOut: String
)