package com.voicegeely.app.security

import android.content.Context
import android.util.Log
import com.tht.voice.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LicenseManager(
    context: Context,
    private val vinProvider: VinProvider = PlatformVinProviders.create(context)
) {
    data class ActivationResult(val ok: Boolean, val message: String, val locked: Boolean = false, val unlocked: Boolean = false)
    data class RemoteStatus(val checked: Boolean, val active: Boolean, val blocked: Boolean, val message: String)

    private val appContext = context.applicationContext
    private val store = LicenseStore(appContext)
    private val noticeStore = CustomerNoticeStore(appContext)
    private val modeStore = LicenseModeStore(appContext)

    fun requestCode(): String = DeviceIdentity.compactRequestCode(appContext)
    fun formattedRequestCode(): String = DeviceIdentity.formattedRequestCode(appContext)

    /**
     * With signed licenses on, a record must carry a token signed by the server for this device.
     * A record from the old server (no token yet) keeps working until the first successful
     * server check swaps it for a signed one.
     */
    private fun tokenPayload(record: LicenseStore.Record): LicenseTokenPayload? {
        if (!LicenseConfig.signedLicenses || record.token.isBlank()) return null
        return LicenseToken.verify(record.token, LicenseConfig.PUBLIC_KEYS)?.takeIf { it.requestCode == record.requestCode }
    }

    private fun tokenOk(record: LicenseStore.Record): Boolean =
        !LicenseConfig.signedLicenses || record.token.isBlank() || tokenPayload(record) != null

    fun gateState(now: Long = System.currentTimeMillis()): LicenseGateState {
        // Activation temporarily disabled for car testing (BuildConfig.LICENSE_GATE_ENABLED = false).
        if (!com.tht.voice.BuildConfig.LICENSE_GATE_ENABLED) return LicenseGateState.ACTIVE
        val record = store.load()
        val payload = record?.let { tokenPayload(it) }
        return LicenseGateEvaluator.evaluate(
            hasRecord = record != null,
            requestMatches = record?.requestCode == requestCode(),
            vinBindingOk = vinBindingOk(record),
            activationPresent = !record?.activationCode.isNullOrBlank() && record != null && tokenOk(record),
            expiresAt = payload?.expiresAt ?: record?.expiresAt ?: 0L,
            lastSeenAt = record?.lastSeenAt ?: 0L,
            remoteBlocked = record?.remoteBlocked == true,
            now = now,
            rollbackToleranceMs = CLOCK_ROLLBACK_TOLERANCE_MS,
            genuineApp = AppSignature.isGenuine(appContext)
        )
    }

    /** Only reads the car VIN when VIN-required binding is switched on and the license is not VIP. */
    private fun vinBindingOk(record: LicenseStore.Record?): Boolean {
        if (record == null || !VinBindingPolicy.VIN_REQUIRED_ENABLED || record.licenseType == LicenseType.VIP) return true
        return VinBindingPolicy.bindingOk(true, record.licenseType, record.vinHash, currentVinHash())
    }

    private fun currentVin(): String? =
        (runCatching { vinProvider.readVin() }.getOrNull() as? VinReadResult.Available)?.vin

    private fun currentVinHash(): String? = currentVin()?.let { VinBindingPolicy.vinHash(it, requestCode()) }

    /** VIN read once for a server call: the VIN (or null) and the status the server counts. */
    private fun vinReport(): Pair<String?, String> {
        val result = runCatching { vinProvider.readVin() }
            .getOrElse { VinReadResult.Unavailable(VinUnavailableReason.READ_FAILED) }
        return VinReportPolicy.report(result)
    }

    fun licenseType(): LicenseType = store.load()?.licenseType ?: LicenseType.STANDARD

    /** True when the voice core may run: an active key, or no key needed while the server is in free mode. */
    fun isLicensed(): Boolean = LicenseAccessPolicy.allows(gateState(), keyRequired())

    /** True when the server's signed policy for this device requires a key. */
    fun keyRequired(now: Long = System.currentTimeMillis()): Boolean = LicenseAccessPolicy.keyRequired(
        modeStore.token?.let { LicenseToken.verifyPolicy(it, LicenseConfig.PUBLIC_KEYS) }, requestCode(), now
    )

    /** Running without a key because the server does not require one. */
    fun usingFreeMode(): Boolean = gateState() != LicenseGateState.ACTIVE && isLicensed()

    /** Reads free / key-required from the server; false when the server could not be read. */
    fun refreshLicenseMode(now: Long = System.currentTimeMillis()): Boolean {
        if (!LicenseConfig.isConfigured()) return false
        return try {
            // The server also records this car (admin: "Chưa có key"), with VIN when readable.
            val (vin, vinStatus) = vinReport()
            val headers = mapOf("X-THT-Request" to requestCode(), "X-THT-App-Version" to BuildConfig.VERSION_NAME, "X-THT-Vin-Status" to vinStatus) +
                (vin?.let { mapOf("X-THT-Vin" to it) } ?: emptyMap())
            val reply = call("GET", "api/policy", null, headers)
            val token = reply.json.optString("token", "")
            val policy = LicenseToken.verifyPolicy(token, LicenseConfig.PUBLIC_KEYS)
            if (reply.status != 200 || policy == null || policy.requestCode != requestCode()) {
                Log.w(TAG, "license_mode_rejected status=${reply.status} signed=${policy != null}")
                return false
            }
            modeStore.save(token, now)
            Log.i(TAG, "license_mode required=${policy.keyRequired}")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "license_mode_failed type=${t.javaClass.simpleName}")
            false
        }
    }

    /** Release builds tell the server which certificate signed them; it refuses re-signed copies. */
    private fun certHeader(): Map<String, String> =
        if (AppSignature.enforced()) mapOf("X-THT-Cert" to AppSignature.primary(appContext)) else emptyMap()

    fun hasStoredLicense(): Boolean = store.load()?.let { it.requestCode == requestCode() && it.activationCode.isNotBlank() } == true

    fun remoteRefreshDue(now: Long = System.currentTimeMillis()): Boolean {
        if (LicenseAccessPolicy.refreshDue(modeStore.checkedAt, now)) return true
        val record = store.load() ?: return false
        if (LicenseConfig.signedLicenses && record.token.isBlank()) {
            // Old-server record on a new-server build: fetch the signed license soon, but not on every trigger.
            return now - lastUnsignedRefreshAttempt >= UNSIGNED_RETRY_MS
        }
        return LicenseSessionPolicy.remoteRefreshDue(record.remoteCheckedAt, now)
    }

    fun hasLocalLicense(): Boolean {
        val record = store.load() ?: return false
        return record.requestCode == requestCode() && record.activationCode.isNotBlank()
    }

    private fun localRecordValid(ignoreRemoteBlocked: Boolean): LicenseStore.Record? {
        val record = store.load() ?: return null
        if (record.requestCode != requestCode() || !tokenOk(record)) return null
        val now = System.currentTimeMillis()
        if (record.expiresAt > 0L && now > record.expiresAt) return null
        if (record.lastSeenAt > 0L && now + CLOCK_ROLLBACK_TOLERANCE_MS < record.lastSeenAt) return null
        if (!ignoreRemoteBlocked && record.remoteBlocked) return null
        if (now - record.lastSeenAt >= LAST_SEEN_UPDATE_MS) {
            store.save(record.copy(lastSeenAt = now))
        }
        return record
    }

    fun statusText(): String {
        val state = gateState()
        val record = store.load()
        return when (state) {
            LicenseGateState.UNLICENSED -> if (isLicensed()) "Đang dùng miễn phí" else "Chưa kích hoạt"
            LicenseGateState.BINDING_MISMATCH -> "License không thuộc thiết bị này"
            LicenseGateState.REMOTE_BLOCKED -> "License đã bị khóa từ xa"
            LicenseGateState.EXPIRED -> if (isLicensed()) "Key đã hết hạn • đang dùng miễn phí" else "License đã hết hạn"
            LicenseGateState.CLOCK_INVALID -> "Thời gian thiết bị không hợp lệ"
            LicenseGateState.TAMPERED -> "Bản cài không phải bản chính thức"
            LicenseGateState.ACTIVE -> {
                val expiresAt = record?.expiresAt ?: 0L
                val kind = if (record?.licenseType == LicenseType.VIP) "Đã kích hoạt VIP" else "Đã kích hoạt"
                if (expiresAt == 0L) {
                    "$kind • Vĩnh viễn"
                } else {
                    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
                    "$kind • đến ${fmt.format(Date(expiresAt))}"
                }
            }
        }
    }

    /** Headers for update / voice pack calls: a valid local license, or the car code in free mode. */
    fun authHeaders(): Map<String, String> {
        val record = localRecordValid(ignoreRemoteBlocked = true)?.takeIf { it.activationCode.isNotBlank() }
        if (record != null) return storedAuthHeaders(record)
        // Free mode without a key: car code only; the server allows a few downloads a day (admin can turn it off).
        if (!usingFreeMode()) return emptyMap()
        return mapOf("X-THT-Request" to requestCode(), "X-THT-App-Version" to BuildConfig.VERSION_NAME) + certHeader()
    }

    /** New header names plus the old ones, so the same build works against both servers. */
    private fun storedAuthHeaders(record: LicenseStore.Record): Map<String, String> = mapOf(
        "X-THT-Request" to requestCode(),
        "X-THT-License" to record.activationCode,
        "X-THT-App-Version" to BuildConfig.VERSION_NAME,
        "X-Geely-Request" to requestCode(),
        "X-Geely-License" to record.activationCode
    ) + certHeader()

    private data class Reply(val status: Int, val json: JSONObject)

    private fun call(method: String, path: String, body: JSONObject?, headers: Map<String, String> = emptyMap()): Reply {
        val conn = (URL(LicenseConfig.endpoint(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 7000
            readTimeout = 9000
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "THTVoice/${BuildConfig.VERSION_NAME}")
            (certHeader() + headers).forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Reply(status, runCatching { JSONObject(text) }.getOrElse { JSONObject() })
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Stores a successful verify/restore/status reply. In signed mode the reply must carry a
     * token signed for this device; otherwise nothing is saved.
     */
    private fun saveGranted(json: JSONObject, fallbackCode: String, previous: LicenseStore.Record?): Boolean {
        val now = json.optLong("server_time", System.currentTimeMillis())
        val code = LicenseCodes.compact(json.optString("activation_code", fallbackCode)).ifBlank { fallbackCode }
        val token = json.optString("token", "")
        var expiresAt = json.optLong("expires_at", previous?.expiresAt ?: 0L)
        var licenseType = if (json.has("license_type")) LicenseType.fromWire(json.optString("license_type")) else previous?.licenseType ?: LicenseType.STANDARD
        if (LicenseConfig.signedLicenses) {
            val payload = LicenseToken.verify(token, LicenseConfig.PUBLIC_KEYS)
            if (payload == null || payload.requestCode != requestCode()) {
                Log.w(TAG, "license_reply_rejected reason=BAD_TOKEN")
                return false
            }
            expiresAt = payload.expiresAt
            licenseType = payload.licenseType
        }
        // VIP never needs a VIN; a standard license remembers a VIN hash (if readable) for the
        // future VIN-required binding.
        val vinHash = if (licenseType == LicenseType.VIP) "" else previous?.vinHash?.ifBlank { null } ?: currentVinHash().orEmpty()
        store.save(LicenseStore.Record(
            requestCode = requestCode(),
            activationCode = code,
            expiresAt = expiresAt,
            activatedAt = previous?.activatedAt?.takeIf { it > 0 } ?: now,
            lastSeenAt = maxOf(previous?.lastSeenAt ?: 0L, now),
            remoteBlocked = false,
            remoteCheckedAt = now,
            licenseType = licenseType,
            vinHash = vinHash,
            token = token
        ))
        runCatching { noticeStore.saveFromReply(json) }
        return true
    }

    private fun lockedMessage(json: JSONObject): String =
        json.optString("message", "").ifBlank { "Nhập sai quá nhiều lần, thử lại sau hoặc nhập mã mở khóa." }

    fun refreshRemoteStatus(): RemoteStatus {
        if (!LicenseConfig.isConfigured()) return RemoteStatus(false, isLicensed(), false, "Chưa cấu hình máy chủ license")
        val modeChecked = refreshLicenseMode()
        val record = store.load()?.takeIf { it.requestCode == requestCode() && it.activationCode.isNotBlank() }
            ?: return RemoteStatus(modeChecked, isLicensed(), false,
                if (isLicensed()) "Đang dùng miễn phí, chưa cần key" else "Cần nhập key để dùng THT Voice")
        if (record.token.isBlank()) lastUnsignedRefreshAttempt = System.currentTimeMillis()
        return try {
            val (vin, vinStatus) = vinReport()
            val headers = storedAuthHeaders(record) + mapOf("X-THT-Vin-Status" to vinStatus) +
                (vin?.let { mapOf("X-THT-Vin" to it) } ?: emptyMap()) +
                // Lets the admin see "Đã xem" for the customer notice.
                (runCatching { noticeStore.seenId }.getOrNull()?.let { mapOf("X-THT-Notice-Seen" to it) } ?: emptyMap())
            val reply = call("GET", "api/status", null, headers)
            val json = reply.json
            val now = json.optLong("server_time", System.currentTimeMillis())
            val code = json.optString("code", "")
            val message = json.optString("message", "")
            when {
                reply.status in 200..299 && json.optBoolean("ok", false) -> {
                    val before = record.expiresAt to record.licenseType
                    if (!saveGranted(json, record.activationCode, record)) {
                        return RemoteStatus(true, isLicensed(), false, "Phản hồi máy chủ không có chữ ký hợp lệ")
                    }
                    val after = store.load()
                    val changed = after != null && (after.expiresAt to after.licenseType) != before
                    RemoteStatus(true, true, false, if (changed) "Đã đồng bộ gia hạn license" else "License đang hoạt động")
                }
                reply.status == 403 && (code == "device_blocked" || code == "license_revoked") -> {
                    store.save(record.copy(remoteBlocked = true, remoteCheckedAt = now))
                    RemoteStatus(true, false, true, message.ifBlank { "Thiết bị đã bị khóa từ xa" })
                }
                reply.status == 403 && code == "expired" -> {
                    store.save(record.copy(expiresAt = json.optLong("expires_at", record.expiresAt).takeIf { it > 0 } ?: record.expiresAt, remoteCheckedAt = now))
                    RemoteStatus(true, false, false, message.ifBlank { "License đã hết hạn" })
                }
                LicenseConfig.signedLicenses &&
                    ((reply.status == 403 && code == "license_moved") || (reply.status == 401 && code == "license_not_found")) -> {
                    Log.i(TAG, "license_removed_by_server code=$code")
                    store.clear()
                    RemoteStatus(true, false, false, message.ifBlank { "License không còn dùng cho thiết bị này" })
                }
                reply.status == 429 -> RemoteStatus(true, isLicensed(), false, lockedMessage(json))
                reply.status == 401 || reply.status == 403 -> RemoteStatus(true, isLicensed(), false, message.ifBlank { "License cloud bị từ chối" })
                else -> RemoteStatus(true, isLicensed(), false, "Máy chủ HTTP ${reply.status}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote status unavailable", e)
            RemoteStatus(false, isLicensed(), store.load()?.remoteBlocked == true, "Không kiểm tra được máy chủ")
        }
    }

    fun activate(rawActivationCode: String): ActivationResult {
        if (!LicenseConfig.isConfigured()) return ActivationResult(false, "Chưa cấu hình máy chủ kích hoạt")
        val activation = when (val parsed = LicenseCodes.parse(rawActivationCode)) {
            is LicenseCodes.Parsed.Numeric -> parsed.code
            is LicenseCodes.Parsed.Hex -> parsed.code
            is LicenseCodes.Parsed.Unlock -> return unlock(parsed.code)
            LicenseCodes.Parsed.Typo -> return ActivationResult(false, "Mã có một chữ số bị sai hoặc bị đảo chỗ. Kiểm tra lại từng nhóm 4 số.")
            LicenseCodes.Parsed.Invalid -> return ActivationResult(false, "Mã kích hoạt gồm 16 ký tự (0-9, A-F) hoặc 20 chữ số.")
        }
        return try {
            val payload = JSONObject().apply {
                put("request_code", requestCode())
                put("activation_code", activation)
                put("app_version", BuildConfig.VERSION_NAME)
                val (vin, vinStatus) = vinReport()
                vin?.let { put("vin", it) }
                put("vin_status", vinStatus)
            }
            val reply = call("POST", "api/verify", payload)
            val json = reply.json
            if (reply.status == 429) return ActivationResult(false, lockedMessage(json), locked = true)
            if (reply.status !in 200..299 || !json.optBoolean("ok", false)) {
                return ActivationResult(false, json.optString("message", "Mã kích hoạt không hợp lệ"))
            }
            if (!saveGranted(json, activation, null)) return ActivationResult(false, "Phản hồi máy chủ không có chữ ký hợp lệ.")
            val record = store.load()
            val base = if (record?.licenseType == LicenseType.VIP) "Kích hoạt VIP thành công" else "Kích hoạt thành công"
            ActivationResult(true, if (record?.expiresAt == 0L) "$base • Vĩnh viễn" else base)
        } catch (e: Exception) {
            Log.e(TAG, "Activation failed", e)
            ActivationResult(false, "Không kết nối được máy chủ kích hoạt")
        }
    }

    /**
     * "Khôi phục bản quyền": after reinstalling the app on the same device the device code is
     * unchanged, so the server hands back the license bound to it. With a stored license this is
     * a normal status check (e.g. after a renewal).
     */
    fun restore(): ActivationResult {
        if (!LicenseConfig.isConfigured()) return ActivationResult(false, "Chưa cấu hình máy chủ kích hoạt")
        if (hasStoredLicense()) {
            // Renewal or a reissued code ("Xoay key / cấp lại") arrives through the status check.
            val remote = refreshRemoteStatus()
            if (isLicensed()) return ActivationResult(true, remote.message)
            // Otherwise (revoked, expired, moved) still ask for the license bound to this device.
        }
        return try {
            val payload = JSONObject().apply {
                put("request_code", requestCode())
                put("app_version", BuildConfig.VERSION_NAME)
                val (vin, vinStatus) = vinReport()
                vin?.let { put("vin", it) }
                put("vin_status", vinStatus)
            }
            val reply = call("POST", "api/restore", payload)
            val json = reply.json
            if (reply.status == 429) return ActivationResult(false, lockedMessage(json), locked = true)
            if (reply.status == 404 && json.optString("code").isBlank()) {
                return ActivationResult(false, "Máy chủ này chưa hỗ trợ khôi phục. Nhập lại mã kích hoạt.")
            }
            if (reply.status !in 200..299 || !json.optBoolean("ok", false)) {
                return ActivationResult(false, json.optString("message", "Không tìm thấy bản quyền cho thiết bị này."))
            }
            if (!saveGranted(json, "", null)) return ActivationResult(false, "Phản hồi máy chủ không có chữ ký hợp lệ.")
            ActivationResult(true, json.optString("message", "Đã khôi phục bản quyền"))
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            ActivationResult(false, "Không kết nối được máy chủ kích hoạt")
        }
    }

    /** One-time 8-digit unlock code from the seller after too many wrong tries. */
    fun unlock(code: String): ActivationResult = try {
        val reply = call("POST", "api/unlock", JSONObject().apply {
            put("request_code", requestCode())
            put("unlock_code", code)
        })
        val json = reply.json
        when {
            reply.status in 200..299 && json.optBoolean("ok", false) ->
                ActivationResult(false, json.optString("message", "Đã mở khóa. Nhập lại mã kích hoạt."), unlocked = true)
            reply.status == 429 -> ActivationResult(false, lockedMessage(json), locked = true)
            else -> ActivationResult(false, json.optString("message", "Mã mở khóa không đúng hoặc đã hết hạn."))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Unlock failed", e)
        ActivationResult(false, "Không kết nối được máy chủ kích hoạt")
    }

    fun clearLicense() = store.clear()

    companion object {
        private const val TAG = "LicenseManager"
        private const val CLOCK_ROLLBACK_TOLERANCE_MS = 6L * 60L * 60L * 1000L
        private const val LAST_SEEN_UPDATE_MS = 5L * 60L * 1000L
        private const val UNSIGNED_RETRY_MS = 10L * 60L * 1000L

        @Volatile private var lastUnsignedRefreshAttempt = 0L

        fun normalizeCode(value: String): String = LicenseCodes.compact(value)

        fun formatCode(value: String): String = LicenseCodes.formatForInput(value)
    }
}
