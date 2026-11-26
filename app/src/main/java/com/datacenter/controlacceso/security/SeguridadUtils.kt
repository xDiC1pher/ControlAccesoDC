package com.datacenter.controlacceso.security

import java.security.MessageDigest
import java.security.SecureRandom

/* Utilidades de validacion de credenciales y hashing SHA-256 */
object SeguridadUtils {

    private val patronPin = Regex("^[0-9A-D]{8}$")

    fun validarContrasenaRobusta(clave: String): String? {
        if (clave.length < 8) return "Debe tener al menos 8 caracteres."
        if (!clave.any { it.isUpperCase() }) return "Debe incluir al menos una mayuscula."
        if (!clave.any { it.isLowerCase() }) return "Debe incluir al menos una minuscula."
        if (!clave.any { it.isDigit() }) return "Debe incluir al menos un numero."
        if (!clave.any { !it.isLetterOrDigit() }) return "Debe incluir al menos un simbolo."
        return null
    }

    fun validarPinAcceso(pin: String): Boolean {
        return patronPin.matches(pin.uppercase())
    }

    fun generarSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generarHashSha256(texto: String, salt: String = ""): String {
        val entrada = "$salt:$texto".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(entrada)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /* Genera firma de integridad para detectar alteraciones en los logs */
    fun generarFirmaLog(
        uid: String,
        metodo: String,
        resultado: String,
        fechaHora: Long
    ): String {
        val cadena = "$uid|$metodo|$resultado|$fechaHora"
        return generarHashSha256(cadena)
    }
}