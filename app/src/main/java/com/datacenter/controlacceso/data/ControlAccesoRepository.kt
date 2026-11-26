package com.datacenter.controlacceso.data

import com.datacenter.controlacceso.security.SeguridadUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/* Gestiona autenticacion, perfiles RBAC, logs firmados y estado de la puerta */
class ControlAccesoRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun obtenerUidActual(): String? = auth.currentUser?.uid

    fun cerrarSesion() {
        auth.signOut()
    }

    /* Registra cuenta nueva con rol tecnico por defecto para evitar escalada de privilegios */
    fun registrarUsuario(
        nombre: String,
        correo: String,
        clave: String,
        onExito: (Usuario) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(correo.trim(), clave)
            .addOnSuccessListener { resultado ->
                val uid = resultado.user?.uid
                if (uid == null) {
                    onError("No se pudo obtener el identificador del usuario.")
                    return@addOnSuccessListener
                }
                val nuevoUsuario = Usuario(
                    uid = uid,
                    nombre = nombre.trim(),
                    correo = correo.trim(),
                    rol = "tecnico",
                    activo = true
                )
                db.collection("usuarios").document(uid)
                    .set(nuevoUsuario)
                    .addOnSuccessListener { onExito(nuevoUsuario) }
                    .addOnFailureListener { e ->
                        onError(e.localizedMessage ?: "Error al guardar el perfil.")
                    }
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Error al registrar la cuenta.")
            }
    }

    fun iniciarSesion(
        correo: String,
        clave: String,
        onExito: (Usuario) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(correo.trim(), clave)
            .addOnSuccessListener { resultado ->
                val uid = resultado.user?.uid
                if (uid == null) {
                    onError("Sesion no valida.")
                    return@addOnSuccessListener
                }
                obtenerPerfil(uid, onExito, onError)
            }
            .addOnFailureListener {
                onError("Correo o contrasena incorrectos.")
            }
    }

    fun enviarRecuperacionClave(
        correo: String,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.sendPasswordResetEmail(correo.trim())
            .addOnSuccessListener { onExito() }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "No se pudo enviar el correo de recuperacion.")
            }
    }

    fun obtenerPerfil(
        uid: String,
        onExito: (Usuario) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                val usuario = doc.toObject(Usuario::class.java)
                if (usuario == null) {
                    onError("Perfil de usuario no encontrado.")
                } else if (!usuario.activo) {
                    auth.signOut()
                    onError("Su cuenta fue deshabilitada por seguridad.")
                } else {
                    onExito(usuario)
                }
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Error al consultar el perfil.")
            }
    }

    /* Escucha cambios en el perfil para revocar sesiones activas si el guardia deshabilita la cuenta */
    fun observarPerfil(
        uid: String,
        onCambio: (Usuario?) -> Unit
    ): ListenerRegistration {
        return db.collection("usuarios").document(uid)
            .addSnapshotListener { snapshot, _ ->
                onCambio(snapshot?.toObject(Usuario::class.java))
            }
    }

    fun actualizarPinUsuario(
        uid: String,
        pinHash: String,
        pinSalt: String,
        onExito: () -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("usuarios").document(uid)
            .update(mapOf("pinHash" to pinHash, "pinSalt" to pinSalt))
            .addOnSuccessListener { onExito() }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "No se pudo actualizar el PIN.")
            }
    }

    /* Crea un registro de auditoria firmado con SHA-256 (Firestore guarda en cache si no hay Wi-Fi) */
    fun registrarLog(
        usuario: Usuario,
        metodo: String,
        resultado: String,
        detalle: String
    ) {
        val ahora = System.currentTimeMillis()
        val firma = SeguridadUtils.generarFirmaLog(usuario.uid, metodo, resultado, ahora)
        val docRef = db.collection("logs_acceso").document()
        val log = LogAcceso(
            id = docRef.id,
            uid = usuario.uid,
            nombreUsuario = usuario.nombre,
            rol = usuario.rol,
            metodo = metodo,
            resultado = resultado,
            detalle = detalle,
            fechaHora = ahora,
            hashIntegridad = firma
        )
        docRef.set(log)
    }

    fun observarLogsGlobales(
        onCambio: (List<LogAcceso>) -> Unit
    ): ListenerRegistration {
        return db.collection("logs_acceso")
            .orderBy("fechaHora", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val lista = snapshot?.documents?.mapNotNull {
                    it.toObject(LogAcceso::class.java)
                } ?: emptyList()
                onCambio(lista)
            }
    }

    fun observarLogsPropios(
        uid: String,
        onCambio: (List<LogAcceso>) -> Unit
    ): ListenerRegistration {
        return db.collection("logs_acceso")
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, _ ->
                val lista = snapshot?.documents?.mapNotNull {
                    it.toObject(LogAcceso::class.java)
                }?.sortedByDescending { it.fechaHora } ?: emptyList()
                onCambio(lista)
            }
    }

    fun observarUsuarios(
        onCambio: (List<Usuario>) -> Unit
    ): ListenerRegistration {
        return db.collection("usuarios")
            .addSnapshotListener { snapshot, _ ->
                val lista = snapshot?.documents?.mapNotNull {
                    it.toObject(Usuario::class.java)
                }?.sortedBy { it.nombre } ?: emptyList()
                onCambio(lista)
            }
    }

    fun cambiarEstadoUsuario(
        uidObjetivo: String,
        activo: Boolean,
        onError: (String) -> Unit
    ) {
        db.collection("usuarios").document(uidObjetivo)
            .update("activo", activo)
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "No se pudo cambiar el estado del usuario.")
            }
    }

    fun observarEstadoPuerta(
        onCambio: (EstadoPuerta) -> Unit
    ): ListenerRegistration {
        return db.collection("estado_puerta").document("puerta_principal")
            .addSnapshotListener { snapshot, _ ->
                val estado = snapshot?.toObject(EstadoPuerta::class.java) ?: EstadoPuerta()
                onCambio(estado)
            }
    }

    fun actualizarEstadoPuerta(estado: EstadoPuerta) {
        db.collection("estado_puerta").document("puerta_principal").set(estado)
    }
}