package com.example.qr_scan

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.*
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import android.media.MediaScannerConnection
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.IsoDep
import android.nfc.tech.NdefFormatable

class NFCManager(private val context: Context) {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    
    var isNfcEnabled = false
        private set
    
    private val _nfcStatus = MutableStateFlow<NFCStatus>(NFCStatus.Idle)
    val nfcStatus: StateFlow<NFCStatus> = _nfcStatus.asStateFlow()
    
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techLists: Array<Array<String>>? = null
    private var currentNdefMessage: NdefMessage? = null

    init {
        checkNfcStatus()
        setupForegroundDispatch()
    }

    private fun checkNfcStatus() {
        isNfcEnabled = nfcAdapter?.isEnabled == true
        if (!isNfcEnabled) {
            _nfcStatus.value = NFCStatus.Error("NFC no está habilitado en el dispositivo")
        }
    }
    
    private fun setupForegroundDispatch() {
        try {
            val intent = Intent(context, context.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
            
            intentFilters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                    try {
                        addDataType("*/*")
                    } catch (e: IntentFilter.MalformedMimeTypeException) {
                        throw RuntimeException(e)
                    }
                },
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            )
            
            techLists = arrayOf(
                arrayOf(
                    NfcA::class.java.name,
                    NfcB::class.java.name,
                    NfcF::class.java.name,
                    NfcV::class.java.name,
                    IsoDep::class.java.name,
                    MifareClassic::class.java.name,
                    MifareUltralight::class.java.name,
                    Ndef::class.java.name,
                    NdefFormatable::class.java.name
                )
            )
        } catch (e: Exception) {
            _nfcStatus.value = NFCStatus.Error("Error al configurar NFC: ${e.message}")
        }
    }

    fun shareHotelInfo(hotelInfo: HotelInfo) {
        if (!isNfcEnabled) {
            _nfcStatus.value = NFCStatus.Error("NFC no está habilitado")
            return
        }

        try {
            val jsonObject = JSONObject().apply {
                put("guestId", hotelInfo.guestId)
                put("guestName", hotelInfo.guestName)
                put("guestEmail", hotelInfo.guestEmail)
                put("checkIn", hotelInfo.checkIn)
                put("checkOut", hotelInfo.checkOut)
                put("hotelId", hotelInfo.hotelId)
                put("roomNumber", hotelInfo.roomNumber)
                put("lockId", hotelInfo.lockId)
            }

            val mimeRecord = NdefRecord.createMime(
                "application/json",
                jsonObject.toString().toByteArray(Charset.forName("UTF-8"))
            )

            val ndefMessage = NdefMessage(arrayOf(mimeRecord))
            currentNdefMessage = ndefMessage
            
            // We'll just store the message and handle it when a tag is detected
            Toast.makeText(
                context,
                "Acerca el dispositivo a una etiqueta NFC para escribir los datos",
                Toast.LENGTH_LONG
            ).show()
            
            _nfcStatus.value = NFCStatus.Ready
        } catch (e: Exception) {
            _nfcStatus.value = NFCStatus.Error("Error al preparar datos NFC: ${e.message}")
        }
    }
    
    fun processNfcTag(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        
        if (tag == null) {
            _nfcStatus.value = NFCStatus.Error("No se detectó ninguna etiqueta NFC")
            return
        }
        
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            _nfcStatus.value = NFCStatus.Error("La etiqueta NFC no es compatible con NDEF")
            return
        }
        
        try {
            ndef.connect()
            
            // Si hay un mensaje pendiente para escribir, intentamos escribirlo
            if (currentNdefMessage != null) {
                _nfcStatus.value = NFCStatus.Writing
                
                if (!ndef.isWritable) {
                    _nfcStatus.value = NFCStatus.Error("La etiqueta NFC no es escribible")
                    return
                }
                
                if (ndef.maxSize < currentNdefMessage!!.byteArrayLength) {
                    _nfcStatus.value = NFCStatus.Error("La etiqueta NFC no tiene suficiente espacio")
                    return
                }
                
                ndef.writeNdefMessage(currentNdefMessage)
                _nfcStatus.value = NFCStatus.Success("Datos escritos exitosamente")
                currentNdefMessage = null
            } else {
                // Si no hay mensaje pendiente, intentamos leer la etiqueta
                _nfcStatus.value = NFCStatus.Reading
                val ndefMessage = ndef.ndefMessage
                
                if (ndefMessage == null) {
                    _nfcStatus.value = NFCStatus.Error("La etiqueta NFC está vacía")
                    return
                }
                
                val hotelInfo = readNdefMessage(ndefMessage)
                if (hotelInfo != null) {
                    // Enviamos la información a la API REST
                    sendHotelInfoToApi(hotelInfo)
                } else {
                    _nfcStatus.value = NFCStatus.Error("No se pudo leer la información del hotel")
                }
            }
            
        } catch (e: IOException) {
            _nfcStatus.value = NFCStatus.Error("Error de E/S al acceder a la etiqueta: ${e.message}")
        } catch (e: Exception) {
            _nfcStatus.value = NFCStatus.Error("Error al procesar la etiqueta: ${e.message}")
        } finally {
            try {
                ndef.close()
            } catch (e: Exception) {
                // Ignorar errores al cerrar
            }
        }
    }

    private fun readNdefMessage(ndefMessage: NdefMessage): HotelInfo? {
        for (record in ndefMessage.records) {
            if (record.type.toString() == "application/json") {
                val jsonString = String(record.payload, Charset.forName("UTF-8"))
                return parseHotelInfo(jsonString)
            }
        }
        return null
    }

    private fun parseHotelInfo(jsonString: String): HotelInfo? {
        return try {
            val jsonObject = JSONObject(jsonString)
            HotelInfo(
                guestId = jsonObject.getString("guestId"),
                guestName = jsonObject.getString("guestName"),
                guestEmail = jsonObject.getString("guestEmail"),
                checkIn = jsonObject.getString("checkIn"),
                checkOut = jsonObject.getString("checkOut"),
                hotelId = jsonObject.getString("hotelId"),
                roomNumber = jsonObject.getString("roomNumber"),
                lockId = jsonObject.getString("lockId")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun sendHotelInfoToApi(hotelInfo: HotelInfo) {
        // Aquí implementaremos la llamada a la API REST
        // Por ahora solo actualizamos el estado
        _nfcStatus.value = NFCStatus.Validating(hotelInfo)
        
        // TODO: Implementar la llamada real a la API REST
        // La API debería validar la información y controlar la cerradura
        // Ejemplo de implementación:
        /*
        viewModelScope.launch {
            try {
                val response = apiService.validateAndOpenLock(hotelInfo)
                if (response.isSuccessful) {
                    _nfcStatus.value = NFCStatus.Success("Cerradura abierta exitosamente")
                } else {
                    _nfcStatus.value = NFCStatus.Error("Error al abrir la cerradura: ${response.message()}")
                }
            } catch (e: Exception) {
                _nfcStatus.value = NFCStatus.Error("Error de red: ${e.message}")
            }
        }
        */
    }

    fun onResume() {
        nfcAdapter?.enableForegroundDispatch(
            context as Activity,
            pendingIntent,
            intentFilters,
            techLists
        )
    }

    fun onPause() {
        nfcAdapter?.disableForegroundDispatch(context as Activity)
    }
    
    fun readNFCCard() {
        if (!isNfcEnabled) {
            _nfcStatus.value = NFCStatus.Error("NFC no está habilitado")
            return
        }

        _nfcStatus.value = NFCStatus.Reading
        Toast.makeText(
            context,
            "Acerca el dispositivo a una tarjeta NFC para leer los datos",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun handleNFCRead(tag: Tag) {
        try {
            val sb = StringBuilder()
            sb.append("ID de la tarjeta: ${bytesToHexString(tag.id)}\n\n")

            // Intentar leer como NDEF
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                sb.append("Tipo de tarjeta: NDEF\n")
                sb.append("Tipo NDEF: ${ndef.type}\n")
                sb.append("Capacidad máxima: ${ndef.maxSize} bytes\n")
                
                try {
                    ndef.connect()
                    val ndefMessage = ndef.ndefMessage
                    if (ndefMessage != null) {
                        sb.append("\nContenido NDEF:\n")
                        for (record in ndefMessage.records) {
                            sb.append("Tipo de registro: ${String(record.type)}\n")
                            sb.append("Payload: ${String(record.payload)}\n")
                        }
                    } else {
                        sb.append("\nLa tarjeta NDEF está vacía\n")
                    }
                } catch (e: Exception) {
                    sb.append("\nError al leer NDEF: ${e.message}\n")
                } finally {
                    try {
                        ndef.close()
                    } catch (e: Exception) {}
                }
            }

            // Intentar leer como MifareClassic
            val mifareClassic = MifareClassic.get(tag)
            if (mifareClassic != null) {
                sb.append("\nTipo de tarjeta: Mifare Classic\n")
                sb.append("Tamaño: ${mifareClassic.size} bloques\n")
                sb.append("Sectores: ${mifareClassic.sectorCount}\n")
                
                try {
                    mifareClassic.connect()
                    // Intentar leer el primer sector (si está desbloqueado)
                    if (mifareClassic.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT)) {
                        val block = mifareClassic.readBlock(0)
                        sb.append("Bloque 0: ${bytesToHexString(block)}\n")
                    }
                } catch (e: Exception) {
                    sb.append("Error al leer Mifare Classic: ${e.message}\n")
                } finally {
                    try {
                        mifareClassic.close()
                    } catch (e: Exception) {}
                }
            }

            // Intentar leer como MifareUltralight
            val mifareUltralight = MifareUltralight.get(tag)
            if (mifareUltralight != null) {
                sb.append("\nTipo de tarjeta: Mifare Ultralight\n")
                try {
                    mifareUltralight.connect()
                    // Leer las primeras páginas
                    val page0 = mifareUltralight.readPages(0)
                    sb.append("Páginas 0-3: ${bytesToHexString(page0)}\n")
                } catch (e: Exception) {
                    sb.append("Error al leer Mifare Ultralight: ${e.message}\n")
                } finally {
                    try {
                        mifareUltralight.close()
                    } catch (e: Exception) {}
                }
            }

            // Listar todas las tecnologías soportadas
            sb.append("\nTecnologías soportadas:\n")
            for (tech in tag.techList) {
                sb.append("- ${tech.substring(tech.lastIndexOf('.') + 1)}\n")
            }

            // Guardar los datos en un archivo
            saveNFCDataToFile(sb.toString())
            
            _nfcStatus.value = NFCStatus.Success("Datos leídos y guardados correctamente")
            
        } catch (e: Exception) {
            _nfcStatus.value = NFCStatus.Error("Error al leer la tarjeta: ${e.message}")
        }
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }

    private fun saveNFCDataToFile(data: String) {
        try {
            // Crear nombre del archivo con fecha y hora
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "nfc_data_$timeStamp.txt"
            
            // Obtener el directorio de Descargas
            val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            }

            // Crear el archivo en el directorio de Descargas
            val file = File(downloadsDir, fileName)
            
            // Escribir los datos en el archivo
            FileOutputStream(file).use { fos ->
                fos.write(data.toByteArray())
            }

            // Hacer visible el archivo en la galería/explorador de archivos
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.toString()),
                null
            ) { path, uri -> 
                Log.d("NFCManager", "Archivo escaneado con exito: $path")
            }

            _nfcStatus.value = NFCStatus.Success("Datos guardados en: ${file.absolutePath}")
        } catch (e: Exception) {
            _nfcStatus.value = NFCStatus.Error("Error al guardar los datos en archivo: ${e.message}")
        }
    }

    fun onNewIntent(intent: Intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                when (_nfcStatus.value) {
                    is NFCStatus.Reading -> handleNFCRead(tag)
                    else -> _nfcStatus.value = NFCStatus.Error("Estado NFC no válido")
                }
            }
        }
    }
    
    sealed class NFCStatus {
        object Idle : NFCStatus()
        object Ready : NFCStatus()
        object Writing : NFCStatus()
        object Reading : NFCStatus()
        data class Validating(val hotelInfo: HotelInfo) : NFCStatus()
        data class Success(val message: String) : NFCStatus()
        data class Error(val message: String) : NFCStatus()
    }
} 