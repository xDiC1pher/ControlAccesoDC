package com.datacenter.controlacceso.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.datacenter.controlacceso.data.EstadoPuerta

/* Controlador desacoplado de la puerta IoT; simula el enlace HC-06 hasta la integracion fisica */
class ControladorPuerta {

    companion object {
        const val NODO_OBJETIVO = "DC-PUERTA-01 (HC-06)"
        const val TIEMPO_APERTURA_MS = 5000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tareaAutocierre: Runnable? = null

    fun tienePermisoBluetooth(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /* Simula envio One-Tap, cierre inmediato del socket y retorno Fail-Secure a 0 grados */
    fun ejecutarAperturaOneTap(
        nombreTecnico: String,
        onEstadoCambio: (EstadoPuerta) -> Unit,
        onMensaje: (String) -> Unit
    ) {
        tareaAutocierre?.let { handler.removeCallbacks(it) }

        val estadoAbierto = EstadoPuerta(
            bloqueada = false,
            enPanico = false,
            ultimoEvento = "Puerta abierta por $nombreTecnico via $NODO_OBJETIVO",
            ultimaActualizacion = System.currentTimeMillis()
        )
        onEstadoCambio(estadoAbierto)
        onMensaje("Enlace cerrado tras transaccion. Autocierre Fail-Secure en 5 s.")

        val autocierre = Runnable {
            val estadoCerrado = EstadoPuerta(
                bloqueada = true,
                enPanico = false,
                ultimoEvento = "Autocierre autonomo completado (0 grados - Fail-Secure)",
                ultimaActualizacion = System.currentTimeMillis()
            )
            onEstadoCambio(estadoCerrado)
        }
        tareaAutocierre = autocierre
        handler.postDelayed(autocierre, TIEMPO_APERTURA_MS)
    }

    /* Simula la transferencia del hash SHA-256 y Salt del PIN hacia la EEPROM del Arduino */
    fun sincronizarPinEeprom(pinHash: String): String {
        val prefijo = pinHash.take(10)
        return "Hash SHA-256 ($prefijo...) sincronizado con EEPROM en $NODO_OBJETIVO."
    }

    /* Activa bloqueo de emergencia inmediato y cancela cualquier apertura en curso */
    fun activarPanicoLockdown(
        origen: String,
        onEstadoCambio: (EstadoPuerta) -> Unit
    ) {
        tareaAutocierre?.let { handler.removeCallbacks(it) }
        onEstadoCambio(
            EstadoPuerta(
                bloqueada = true,
                enPanico = true,
                ultimoEvento = "LOCKDOWN activo por $origen (servomotor bloqueado a 0 grados)",
                ultimaActualizacion = System.currentTimeMillis()
            )
        )
    }

    /* Desarma la alerta de panico manteniendo la puerta cerrada bajo politica Fail-Secure */
    fun desarmarPanico(
        autorizadoPor: String,
        onEstadoCambio: (EstadoPuerta) -> Unit
    ) {
        onEstadoCambio(
            EstadoPuerta(
                bloqueada = true,
                enPanico = false,
                ultimoEvento = "Alarma desarmada por $autorizadoPor (puerta en reposo seguro)",
                ultimaActualizacion = System.currentTimeMillis()
            )
        )
    }
}