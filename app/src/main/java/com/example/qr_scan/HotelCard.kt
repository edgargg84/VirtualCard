package com.example.qr_scan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HotelCard(
    hotelInfo: HotelInfo,
    onDismiss: () -> Unit,
    nfcManager: NFCManager
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Información del Hotel",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Huésped: ${hotelInfo.guestName}")
            Text("Email: ${hotelInfo.guestEmail}")
            Text("Check-in: ${hotelInfo.checkIn}")
            Text("Check-out: ${hotelInfo.checkOut}")
            Text("Habitación: ${hotelInfo.roomNumber}")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { nfcManager.readNFCCard() }
                ) {
                    Text("Leer por NFC")
                }
                
                Button(
                    onClick = { nfcManager.shareHotelInfo(hotelInfo) }
                ) {
                    Text("Compartir por NFC")
                }
                
                Button(
                    onClick = onDismiss
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}