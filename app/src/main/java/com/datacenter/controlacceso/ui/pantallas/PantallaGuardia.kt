package com.datacenter.controlacceso.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.datacenter.controlacceso.bluetooth.ControladorPuerta
import com.datacenter.controlacceso.data.ControlAccesoRepository
import com.datacenter.controlacceso.data.EstadoPuerta
import com.datacenter.controlacceso.data.LogAcceso
import com.datacenter.controlacceso.data.Usuario
import com.datacenter.controlacceso.security.SeguridadUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* Panel del guardia: monitoreo Wi-Fi en vivo, gestion de accesos y auditoria SHA-256 */
@Composable
fun PantallaGuardia(
    usuario: Usuario,
    repo: ControlAccesoRepository,
    controladorPuerta: ControladorPuerta,
    onCerrarSesion: () -> Unit
) {
    var estadoPuerta by remember { mutableStateOf(EstadoPuerta()) }
    var listaUsuarios by remember { mutableStateOf<List<Usuario>>(emptyList()) }
    var logsGlobales by remember { mutableStateOf<List<LogAcceso>>(emptyList()) }
    var mensajeAdmin by remember { mutableStateOf("") }

    /* Escucha en tiempo real la puerta, los perfiles de tecnicos y los logs globales */
    DisposableEffect(usuario.uid) {
        val regPuerta = repo.observarEstadoPuerta { estadoPuerta = it }
        val regUsuarios = repo.observarUsuarios { listaUsuarios = it }
        val regLogs = repo.observarLogsGlobales { logsGlobales = it }
        onDispose {
            regPuerta.remove()
            regUsuarios.remove()
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
                    text = "Rol: Guardia de Seguridad (Wi-Fi)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onCerrarSesion) {
                Text("Cerrar sesion")
            }
        }

        /* Monitoreo en vivo de la puerta y control maestro de panico */
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
                    estadoPuerta.enPanico -> "ALERTA: LOCKDOWN ACTIVO"
                    estadoPuerta.bloqueada -> "PUERTA CERRADA (FAIL-SECURE)"
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

                if (estadoPuerta.enPanico) {
                    Button(
                        onClick = {
                            controladorPuerta.desarmarPanico(
                                autorizadoPor = usuario.nombre,
                                onEstadoCambio = { nuevoEstado ->
                                    repo.actualizarEstadoPuerta(nuevoEstado)
                                }
                            )
                            repo.registrarLog(
                                usuario = usuario,
                                metodo = "Panel Guardia",
                                resultado = "DESARMADO",
                                detalle = "Alarma de panico desarmada por guardia"
                            )
                            mensajeAdmin = "Lockdown desarmado. Puerta en reposo seguro."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Desarmar Alarma (Restaurar Acceso)")
                    }
                } else {
                    Button(
                        onClick = {
                            controladorPuerta.activarPanicoLockdown(
                                origen = "Guardia ${usuario.nombre}",
                                onEstadoCambio = { nuevoEstado ->
                                    repo.actualizarEstadoPuerta(nuevoEstado)
                                }
                            )
                            repo.registrarLog(
                                usuario = usuario,
                                metodo = "Panel Guardia",
                                resultado = "ALERTA",
                                detalle = "Lockdown remoto activado desde consola de guardia"
                            )
                            mensajeAdmin = "Lockdown remoto activado."
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Bloquear Puerta (Lockdown)")
                    }
                }
            }
        }

        if (mensajeAdmin.isNotEmpty()) {
            Text(
                text = mensajeAdmin,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        /* Administracion RBAC: habilitacion y revocacion inmediata de cuentas */
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Control de Personal (${listaUsuarios.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Desactivar una cuenta revoca su sesion activa al instante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                listaUsuarios.forEach { item ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.nombre} (${item.rol})",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = item.correo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = item.activo,
                            enabled = item.uid != usuario.uid,
                            onCheckedChange = { nuevoEstado ->
                                repo.cambiarEstadoUsuario(
                                    uidObjetivo = item.uid,
                                    activo = nuevoEstado,
                                    onError = { mensajeAdmin = it }
                                )
                                val accion = if (nuevoEstado) "HABILITADO" else "REVOCADO"
                                repo.registrarLog(
                                    usuario = usuario,
                                    metodo = "Gestion RBAC",
                                    resultado = accion,
                                    detalle = "Acceso $accion para ${item.correo}"
                                )
                                mensajeAdmin = "Usuario ${item.nombre}: $accion."
                            }
                        )
                    }
                }
            }
        }

        /* Auditoria global en tiempo real con verificacion de firma SHA-256 */
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Registro de Auditoria Global (${logsGlobales.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (logsGlobales.isEmpty()) {
                    Text(
                        text = "Sin registros de acceso todavia.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    logsGlobales.forEach { log ->
                        val firmaEsperada = SeguridadUtils.generarFirmaLog(
                            uid = log.uid,
                            metodo = log.metodo,
                            resultado = log.resultado,
                            fechaHora = log.fechaHora
                        )
                        val integro = firmaEsperada == log.hashIntegridad

                        HorizontalDivider()
                        Text(
                            text = "${log.nombreUsuario} (${log.rol}) - ${log.resultado}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${log.metodo}: ${log.detalle}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${formatearFechaGuardia(log.fechaHora)} | SHA-256: ${if (integro) "OK" else "ALTERADO"} (${log.hashIntegridad.take(10)}...)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (integro) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatearFechaGuardia(timestamp: Long): String {
    if (timestamp == 0L) return "--"
    val formato = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    return formato.format(Date(timestamp))
}