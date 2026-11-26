package com.datacenter.controlacceso.data

/* Perfil de usuario almacenado en la coleccion usuarios de Firestore */
data class Usuario(
    val uid: String = "",
    val nombre: String = "",
    val correo: String = "",
    val rol: String = "tecnico",
    val activo: Boolean = true,
    val pinHash: String = "",
    val pinSalt: String = ""
)

/* Registro de auditoria firmado con SHA-256 */
data class LogAcceso(
    val id: String = "",
    val uid: String = "",
    val nombreUsuario: String = "",
    val rol: String = "",
    val metodo: String = "",
    val resultado: String = "",
    val detalle: String = "",
    val fechaHora: Long = 0L,
    val hashIntegridad: String = ""
)

/* Estado operativo compartido de la puerta de la sala de servidores */
data class EstadoPuerta(
    val bloqueada: Boolean = true,
    val enPanico: Boolean = false,
    val ultimoEvento: String = "Puerta bloqueada (Fail-Secure)",
    val ultimaActualizacion: Long = 0L
)