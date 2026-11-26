package com.datacenter.controlacceso.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.datacenter.controlacceso.data.ControlAccesoRepository
import com.datacenter.controlacceso.data.Usuario
import com.datacenter.controlacceso.security.SeguridadUtils

/* Pantalla de inicio de sesion, registro con clave robusta y recuperacion */
@Composable
fun PantallaAuth(
    repo: ControlAccesoRepository,
    mensajeExterno: String?,
    onAutenticado: (Usuario) -> Unit
) {
    var modoRegistro by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmarClave by remember { mutableStateOf("") }
    var mostrarClave by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf(mensajeExterno ?: "") }
    var mensajeInfo by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Control de Acceso Data Center",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (modoRegistro) "Registro de personal tecnico" else "Acceso autorizado",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (modoRegistro) {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre completo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo institucional") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    label = { Text("Contrasena") },
                    singleLine = true,
                    visualTransformation = if (mostrarClave) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { mostrarClave = !mostrarClave }) {
                            Text(if (mostrarClave) "Ocultar" else "Ver")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (modoRegistro) {
                    OutlinedTextField(
                        value = confirmarClave,
                        onValueChange = { confirmarClave = it },
                        label = { Text("Confirmar contrasena") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Minimo 8 caracteres, mayuscula, minuscula, numero y simbolo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (mensajeError.isNotEmpty()) {
                    Text(
                        text = mensajeError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (mensajeInfo.isNotEmpty()) {
                    Text(
                        text = mensajeInfo,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        mensajeError = ""
                        mensajeInfo = ""

                        if (correo.isBlank() || clave.isBlank()) {
                            mensajeError = "Complete correo y contrasena."
                            return@Button
                        }

                        if (modoRegistro) {
                            if (nombre.isBlank()) {
                                mensajeError = "Ingrese su nombre completo."
                                return@Button
                            }
                            val errorPolitica = SeguridadUtils.validarContrasenaRobusta(clave)
                            if (errorPolitica != null) {
                                mensajeError = errorPolitica
                                return@Button
                            }
                            if (clave != confirmarClave) {
                                mensajeError = "Las contrasenas no coinciden."
                                return@Button
                            }
                            cargando = true
                            repo.registrarUsuario(
                                nombre = nombre,
                                correo = correo,
                                clave = clave,
                                onExito = { usuario ->
                                    cargando = false
                                    onAutenticado(usuario)
                                },
                                onError = { error ->
                                    cargando = false
                                    mensajeError = error
                                }
                            )
                        } else {
                            cargando = true
                            repo.iniciarSesion(
                                correo = correo,
                                clave = clave,
                                onExito = { usuario ->
                                    cargando = false
                                    onAutenticado(usuario)
                                },
                                onError = { error ->
                                    cargando = false
                                    mensajeError = error
                                }
                            )
                        }
                    },
                    enabled = !cargando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (cargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (modoRegistro) "Crear cuenta" else "Iniciar sesion")
                    }
                }

                if (!modoRegistro) {
                    TextButton(
                        onClick = {
                            mensajeError = ""
                            mensajeInfo = ""
                            if (correo.isBlank()) {
                                mensajeError = "Ingrese su correo para recuperar la contrasena."
                                return@TextButton
                            }
                            repo.enviarRecuperacionClave(
                                correo = correo,
                                onExito = {
                                    mensajeInfo = "Correo de recuperacion enviado."
                                },
                                onError = { error ->
                                    mensajeError = error
                                }
                            )
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Recuperar contrasena")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                modoRegistro = !modoRegistro
                mensajeError = ""
                mensajeInfo = ""
            }
        ) {
            Text(
                if (modoRegistro) {
                    "Ya tiene cuenta? Iniciar sesion"
                } else {
                    "No tiene cuenta? Registrarse como tecnico"
                }
            )
        }
    }
}