package com.datacenter.controlacceso.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.datacenter.controlacceso.bluetooth.ControladorPuerta
import com.datacenter.controlacceso.data.ControlAccesoRepository
import com.datacenter.controlacceso.data.EstadoPuerta
import com.datacenter.controlacceso.data.LogAcceso
import com.datacenter.controlacceso.data.Usuario
import com.datacenter.controlacceso.security.AlmacenamientoSeguro
import com.datacenter.controlacceso.security.SeguridadUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* Panel del tecnico autorizado: apertura One-Tap, panico, PIN y trazabilidad propia */
@Composable
fun PantallaTecnico(
    usuario: Usuario,
    repo: ControlAccesoRepository,
    controladorPuerta: ControladorPuerta,
    almacenamiento: AlmacenamientoSeguro,
    permisoBtOtorgado: Boolean,
    onSolicitarPermisoBt: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    var estadoPuerta by remember { mutableStateOf(EstadoPuerta()) }
    var misLogs by remember { mutableStateOf<List<LogAcceso>>(emptyList()) }
    var pinNuevo by remember { mutableStateOf("") }
    var mensajeOperacion by remember { mutableStateOf("") }
    var mensajePin by remember { mutableStateOf("") }

    /* Escucha en tiempo real el estado de la puerta y los registros del tecnico */
    DisposableEffect(usuario.uid) {
        val regPuerta = repo.observarEstadoPuerta { estadoPuerta = it }
        val regLogs = repo.observarLogsPropios(usuario.uid) { misLogs = it }
        onDispose {
            regPuerta.remove()
            regLogs.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = usuario.nombre,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Rol: Tecnico Autorizado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onCerrarSesion) {
                Text("Cerrar sesion")
            }
        }

        /* Estado del permiso Bluetooth requerido para el enlace HC-06 */
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Permiso Bluetooth (HC-06)",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (permisoBtOtorgado) {
                            "Concedido - Nodo filtrado: ${ControladorPuerta.NODO_OBJETIVO}"
                        } else {
                            "Pendiente - Se requiere permiso para operar la cerradura"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!permisoBtOtorgado) {
                    Button(onClick = onSolicitarPermisoBt) {
                        Text("Permitir")
                    }
                }
            }
        }

        /* Estado actual de la puerta y controles fisicos simulados */
        val colorTarjeta = when {
            estadoPuerta.enPanico -> MaterialTheme.colorScheme.errorContainer
            !estadoPuerta.bloqueada -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorTarjeta)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tituloEstado = when {
                    estadoPuerta.enPanico -> "LOCKDOWN ACTIVO (PANICO)"
                    estadoPuerta.bloqueada -> "PUERTA BLOQUEADA (FAIL-SECURE)"
                    else -> "PUERTA ABIERTA"
                }
                Text(
                    text = tituloEstado,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = estadoPuerta.ultimoEvento,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (!permisoBtOtorgado) {
                            onSolicitarPermisoBt()
                            mensajeOperacion = "Autorice el permiso Bluetooth antes de abrir."
                            return@Button
                        }
                        controladorPuerta.ejecutarAperturaOneTap(
                            nombreTecnico = usuario.nombre,
                            onEstadoCambio = { nuevoEstado ->
                                repo.actualizarEstadoPuerta(nuevoEstado)
                            },
                            onMensaje = { aviso ->
                                mensajeOperacion = aviso
                            }
                        )
                        repo.registrarLog(
                            usuario = usuario,
                            metodo = "Bluetooth One-Tap",
                            resultado = "AUTORIZADO",
                            detalle = "Apertura ejecutada en ${ControladorPuerta.NODO_OBJETIVO}"
                        )
                    },
                    enabled = !estadoPuerta.enPanico,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir Puerta (One-Tap)")
                }

                Button(
                    onClick = {
                        controladorPuerta.activarPanicoLockdown(
                            origen = usuario.nombre,
                            onEstadoCambio = { nuevoEstado ->
                                repo.actualizarEstadoPuerta(nuevoEstado)
                            }
                        )
                        repo.registrarLog(
                            usuario = usuario,
                            metodo = "Boton Panico",
                            resultado = "ALERTA",
                            detalle = "Lockdown activado manualmente desde app tecnica"
                        )
                        mensajeOperacion = "Lockdown activado. Solo un guardia puede desarmar."
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Activar Panico (Lockdown)")
                }

                if (mensajeOperacion.isNotEmpty()) {
                    Text(
                        text = mensajeOperacion,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        /* Configuracion de PIN complejo de 8 caracteres para contingencia offline */
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PIN Personal de Contingencia (Teclado 4x4)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Exactamente 8 caracteres usando 0-9 y A-D. Se guarda con SHA-256 + Salt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pinNuevo,
                    onValueChange = { if (it.length <= 8) pinNuevo = it.uppercase() },
                    label = { Text("Nuevo PIN (ej. 48A2C91D)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (!SeguridadUtils.validarPinAcceso(pinNuevo)) {
                            mensajePin = "PIN invalido: debe tener 8 caracteres (solo 0-9 y A-D)."
                            return@Button
                        }
                        val salt = SeguridadUtils.generarSalt()
                        val hash = SeguridadUtils.generarHashSha256(pinNuevo, salt)

                        repo.actualizarPinUsuario(
                            uid = usuario.uid,
                            pinHash = hash,
                            pinSalt = salt,
                            onExito = {
                                almacenamiento.guardarPinLocal(hash, salt)
                                val avisoEeprom = controladorPuerta.sincronizarPinEeprom(hash)
                                repo.registrarLog(
                                    usuario = usuario,
                                    metodo = "Sincronizacion PIN",
                                    resultado = "EXITO",
                                    detalle = "PIN actualizado y respaldado con SHA-256 + Salt"
                                )
                                mensajePin = avisoEeprom
                                pinNuevo = ""
                            },
                            onError = { error ->
                                mensajePin = error
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar y Sincronizar PIN")
                }

                if (mensajePin.isNotEmpty()) {
                    Text(
                        text = mensajePin,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        /* Historial reciente de eventos propios */
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Mis registros recientes (${misLogs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (misLogs.isEmpty()) {
                    Text(
                        text = "Sin eventos registrados.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    misLogs.take(5).forEach { log ->
                        HorizontalDivider()
                        Text(
                            text = "${log.metodo} - ${log.resultado}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${formatearFecha(log.fechaHora)} | SHA-256: ${log.hashIntegridad.take(12)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatearFecha(timestamp: Long): String {
    if (timestamp == 0L) return "--"
    val formato = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    return formato.format(Date(timestamp))
}