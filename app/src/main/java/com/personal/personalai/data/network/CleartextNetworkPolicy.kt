package com.personal.personalai.data.network

import android.util.Log
import okhttp3.internal.platform.Platform
import java.net.InetAddress

private const val TAG = "CleartextNetworkPolicy"

/**
 * Installs a custom OkHttp [Platform] that permits cleartext (plain HTTP) traffic
 * to private/loopback/link-local addresses (RFC 1918 LAN addresses such as those
 * used for a local Ollama server).  All other hosts fall back to the system
 * Network Security Config policy, which blocks cleartext globally.
 *
 * Must be called once during [android.app.Application.onCreate].
 */
object CleartextNetworkPolicy {

    fun installPrivateNetworkOverride() {
        try {
            val original = Platform.get()
            if (original is PrivateNetworkCleartextPlatform) return // already installed
            val replacement = PrivateNetworkCleartextPlatform(original)

            // OkHttp stores the platform instance differently across versions.
            // Try all known locations in order until one succeeds.
            if (tryViaStaticField(replacement)) return
            if (tryViaCompanionField(replacement)) return
            if (tryViaResetMethod(replacement)) return

            Log.w(TAG, "Could not install cleartext override: no suitable OkHttp " +
                    "Platform storage found (static fields: " +
                    "${Platform::class.java.declaredFields.map { it.name }})")
        } catch (e: Exception) {
            Log.w(TAG, "Could not install cleartext override: $e")
        }
    }

    /** Path 1: platform stored as a static field directly on the Platform class. */
    private fun tryViaStaticField(replacement: Platform): Boolean {
        val field = Platform::class.java.declaredFields
            .firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers)
                    && Platform::class.java.isAssignableFrom(it.type) }
            ?.also { it.isAccessible = true }
            ?: return false
        field.set(null, replacement)
        Log.d(TAG, "Cleartext override installed via static field '${field.name}'")
        return true
    }

    /** Path 2: platform stored in a Kotlin companion-object field. */
    private fun tryViaCompanionField(replacement: Platform): Boolean {
        return try {
            val companion = Platform::class.java
                .getDeclaredField("Companion")
                .also { it.isAccessible = true }
                .get(null) ?: return false
            val field = companion.javaClass.declaredFields
                .firstOrNull { Platform::class.java.isAssignableFrom(it.type) }
                ?.also { it.isAccessible = true }
                ?: return false
            field.set(companion, replacement)
            Log.d(TAG, "Cleartext override installed via companion field '${field.name}'")
            true
        } catch (_: NoSuchFieldException) { false }
    }

    /** Path 3: OkHttp exposes a resetForTests() static method (may be name-mangled). */
    private fun tryViaResetMethod(replacement: Platform): Boolean {
        val method = Platform::class.java.declaredMethods
            .firstOrNull { it.name.startsWith("resetForTests")
                    && it.parameterCount == 1
                    && Platform::class.java.isAssignableFrom(it.parameterTypes[0]) }
            ?.also { it.isAccessible = true }
            ?: return false
        method.invoke(null, replacement)
        Log.d(TAG, "Cleartext override installed via method '${method.name}'")
        return true
    }
}

/**
 * OkHttp Platform subclass that allows cleartext for private/loopback/link-local
 * addresses and delegates everything else to the original platform (NSC policy).
 */
private class PrivateNetworkCleartextPlatform(
    private val delegate: Platform
) : Platform() {

    override fun isCleartextTrafficPermitted(hostname: String): Boolean =
        isLocalOrPrivate(hostname) || delegate.isCleartextTrafficPermitted(hostname)

    private fun isLocalOrPrivate(hostname: String): Boolean = try {
        val addr = InetAddress.getByName(hostname)
        addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress
    } catch (_: Exception) {
        false // if address resolution fails, deny cleartext
    }
}
