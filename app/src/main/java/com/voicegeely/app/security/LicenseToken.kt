package com.voicegeely.app.security

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Signed license from the license server: base64url(JSON payload) "." base64url(r||s).
 * ECDSA P-256 / SHA-256, verified offline against the public key(s) built into the APK
 * (config/license-server.properties). A copied or edited license fails verification.
 */
data class LicenseTokenPayload(
    val keyId: String,
    val licenseId: Long,
    val requestCode: String,
    val licenseType: LicenseType,
    /** 0 = lifetime. */
    val expiresAt: Long,
    val issuedAt: Long
)

object LicenseToken {
    /** "k1:BASE64SPKI,k2:BASE64SPKI" → key id → public key. Bad entries are skipped. */
    fun parseKeys(spec: String): Map<String, PublicKey> {
        val factory = KeyFactory.getInstance("EC")
        return spec.split(',').mapNotNull { entry ->
            val id = entry.substringBefore(':', "").trim()
            val key = entry.substringAfter(':', "").trim()
            if (id.isEmpty() || key.isEmpty()) return@mapNotNull null
            runCatching { id to factory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key))) }.getOrNull()
        }.toMap()
    }

    fun verify(token: String, keys: Map<String, PublicKey>): LicenseTokenPayload? {
        val parts = token.trim().split('.')
        if (parts.size != 2 || parts.any { it.isEmpty() } || keys.isEmpty()) return null
        return try {
            val json = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
            val fields = parseFlatJson(json) ?: return null
            val kid = fields["kid"] as? String ?: return null
            val key = keys[kid] ?: return null
            val raw = Base64.getUrlDecoder().decode(parts[1])
            if (raw.size != 64) return null
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(parts[0].toByteArray(Charsets.US_ASCII))
            if (!verifier.verify(rawToDer(raw))) return null
            if ((fields["v"] as? Long) != 1L) return null
            LicenseTokenPayload(
                keyId = kid,
                licenseId = fields["lid"] as? Long ?: return null,
                requestCode = fields["req"] as? String ?: return null,
                licenseType = LicenseType.fromWire(fields["typ"] as? String),
                expiresAt = fields["exp"] as? Long ?: return null,
                issuedAt = fields["iat"] as? Long ?: 0L
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** WebCrypto signs as raw r||s; Java verifies ASN.1 DER SEQUENCE { INTEGER r, INTEGER s }. */
    fun rawToDer(raw: ByteArray): ByteArray {
        val half = raw.size / 2
        fun integer(bytes: ByteArray): ByteArray {
            val value = BigInteger(1, bytes).toByteArray() // adds a leading 0 when the top bit is set
            return byteArrayOf(0x02, value.size.toByte()) + value
        }
        val body = integer(raw.copyOfRange(0, half)) + integer(raw.copyOfRange(half, raw.size))
        val out = ByteArrayOutputStream()
        out.write(0x30)
        out.write(body.size)
        out.write(body)
        return out.toByteArray()
    }

    /**
     * Minimal parser for the flat payload object (string / integer / boolean values only),
     * so verification does not depend on org.json and is unit-testable on the JVM.
     */
    internal fun parseFlatJson(text: String): Map<String, Any?>? {
        val s = text.trim()
        if (!s.startsWith("{") || !s.endsWith("}")) return null
        val out = LinkedHashMap<String, Any?>()
        var i = 1
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun readString(): String? {
            if (s.getOrNull(i) != '"') return null
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val ch = s[i++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s.getOrNull(i++) ?: return null
                        when (e) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { if (i + 4 > s.length) return null; sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            return null
        }
        skipWs()
        if (s.getOrNull(i) == '}') return out
        while (i < s.length) {
            skipWs()
            val key = readString() ?: return null
            skipWs()
            if (s.getOrNull(i++) != ':') return null
            skipWs()
            val value: Any? = when (val c = s.getOrNull(i)) {
                '"' -> readString() ?: return null
                't' -> if (s.startsWith("true", i)) { i += 4; true } else return null
                'f' -> if (s.startsWith("false", i)) { i += 5; false } else return null
                'n' -> if (s.startsWith("null", i)) { i += 4; null } else return null
                else -> {
                    if (c == null || !(c == '-' || c.isDigit())) return null
                    val start = i
                    i++
                    while (i < s.length && s[i].isDigit()) i++
                    s.substring(start, i).toLongOrNull() ?: return null
                }
            }
            out[key] = value
            skipWs()
            when (s.getOrNull(i++)) {
                ',' -> continue
                '}' -> return if (i == s.length) out else null
                else -> return null
            }
        }
        return null
    }
}
