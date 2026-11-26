package com.datacenter.controlacceso.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/* Almacenamiento local cifrado respaldado por Android Keystore */
class AlmacenamientoSeguro(context: Context) {

    private val prefs: SharedPreferences

    init {
        val llaveMaestra = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "dc_prefs_seguras",
            llaveMaestra,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun guardarSesionLocal(uid: String, rol: String) {
        prefs.edit()
            .putString("ultimo_uid", uid)
            .putString("ultimo_rol", rol)
            .apply()
    }

    fun obtenerUltimoRol(): String {
        return prefs.getString("ultimo_rol", "tecnico") ?: "tecnico"
    }

    fun guardarPinLocal(pinHash: String, pinSalt: String) {
        prefs.edit()
            .putString("pin_hash", pinHash)
            .putString("pin_salt", pinSalt)
            .apply()
    }

    fun tienePinConfigurado(): Boolean {
        return !prefs.getString("pin_hash", null).isNullOrEmpty()
    }

    fun limpiarSesion() {
        prefs.edit().clear().apply()
    }
}