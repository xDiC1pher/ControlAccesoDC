package com.datacenter.controlacceso

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.datacenter.controlacceso.bluetooth.ControladorPuerta
import com.datacenter.controlacceso.data.ControlAccesoRepository
import com.datacenter.controlacceso.data.Usuario
import com.datacenter.controlacceso.security.AlmacenamientoSeguro
import com.datacenter.controlacceso.ui.pantallas.PantallaAuth
import com.datacenter.controlacceso.ui.pantallas.PantallaGuardia
import com.datacenter.controlacceso.ui.pantallas.PantallaTecnico
import com.datacenter.controlacceso.ui.theme.ControlAccesoDCTheme

/* Punto de entrada: gestiona sesion persistente, permisos y navegacion por rol */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ControlAccesoDCTheme {
                AplicacionControlAcceso()
            }
        }
    }
}

@Composable
private fun AplicacionControlAcceso() {
    val context = LocalContext.current
    val repo = remember { ControlAccesoRepository() }
    val controladorPuerta = remember { ControladorPuerta() }
    val almacenamiento = remember { AlmacenamientoSeguro(context) }

    var usuarioActual by remember { mutableStateOf<Usuario?>(null) }
    var cargandoSesion by remember { mutableStateOf(true) }
    var mensajeSesion by remember { mutableStateOf<String?>(null) }
    var permisoBtOtorgado by remember {
        mutableStateOf(controladorPuerta.tienePermisoBluetooth(context))
    }

    val lanzadorPermisoBt = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido ->
        permisoBtOtorgado = concedido
    }

    val solicitarPermisoBt: () -> Unit = {
        val permiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        lanzadorPermisoBt.launch(permiso)
    }

    /* Recupera la sesion persistente de Firebase al abrir la app */
    LaunchedEffect(Unit) {
        val uid = repo.obtenerUidActual()
        if (uid == null) {
            cargandoSesion = false
        } else {
            repo.obtenerPerfil(
                uid = uid,
                onExito = { perfil ->
                    almacenamiento.guardarSesionLocal(perfil.uid, perfil.rol)
                    usuarioActual = perfil
                    cargandoSesion = false
                },
                onError = { error ->
                    almacenamiento.limpiarSesion()
                    mensajeSesion = error
                    usuarioActual = null
                    cargandoSesion = false
                }
            )
        }
    }

    /* Revoca la sesion al instante si el guardia deshabilita la cuenta en Firestore */
    DisposableEffect(usuarioActual?.uid) {
        val uid = usuarioActual?.uid
        if (uid == null) {
            onDispose { }
        } else {
            val reg = repo.observarPerfil(uid) { perfilActualizado ->
                if (perfilActualizado != null) {
                    if (!perfilActualizado.activo) {
                        repo.cerrarSesion()
                        almacenamiento.limpiarSesion()
                        usuarioActual = null
                        mensajeSesion = "Su acceso fue revocado por el guardia de seguridad."
                    } else {
                        usuarioActual = perfilActualizado
                    }
                }
            }
            onDispose { reg.remove() }
        }
    }

    val cerrarSesion: () -> Unit = {
        repo.cerrarSesion()
        almacenamiento.limpiarSesion()
        usuarioActual = null
        mensajeSesion = null
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                cargandoSesion -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                usuarioActual == null -> {
                    PantallaAuth(
                        repo = repo,
                        mensajeExterno = mensajeSesion,
                        onAutenticado = { perfil ->
                            almacenamiento.guardarSesionLocal(perfil.uid, perfil.rol)
                            mensajeSesion = null
                            usuarioActual = perfil
                        }
                    )
                }
                usuarioActual?.rol == "guardia" -> {
                    PantallaGuardia(
                        usuario = usuarioActual!!,
                        repo = repo,
                        controladorPuerta = controladorPuerta,
                        onCerrarSesion = cerrarSesion
                    )
                }
                else -> {
                    PantallaTecnico(
                        usuario = usuarioActual!!,
                        repo = repo,
                        controladorPuerta = controladorPuerta,
                        almacenamiento = almacenamiento,
                        permisoBtOtorgado = permisoBtOtorgado,
                        onSolicitarPermisoBt = solicitarPermisoBt,
                        onCerrarSesion = cerrarSesion
                    )
                }
            }
        }
    }
}