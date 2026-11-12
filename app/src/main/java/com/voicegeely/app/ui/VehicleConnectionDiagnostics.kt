package com.voicegeely.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.tht.voice.BuildConfig
import com.tht.voice.R
import com.voicegeely.app.car.CoolrayHeadUnit
import com.voicegeely.app.car.VehicleTelemetryRegistry

/** Read-only diagnostics for a car that cannot be attached to a computer. */
internal object VehicleConnectionDiagnostics {
    fun show(context: Context) {
        val status = VehicleTelemetryRegistry.snapshot()
        val permissions = listOf(
            "android.car.permission.CONTROL_CAR_CLIMATE",
            "android.car.permission.CONTROL_CAR_WINDOWS",
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_POWERTRAIN",
            "android.car.permission.CAR_VENDOR_EXTENSION"
        )
        val report = buildString {
            appendLine("THT Voice ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package ${context.packageName}")
            appendLine("Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}")
            appendLine("Model ${Build.MODEL}, device ${Build.DEVICE}, UID ${android.os.Process.myUid()}")
            appendLine("Coolray profile ${CoolrayHeadUnit.detect() ?: "UNRECOGNIZED"}")
            appendLine("VHAL ${status.state}: ${status.detail}")
            appendLine("Database: ${com.voicegeely.app.history.VoiceHistory.journal?.storageStatus ?: "NOT_INITIALIZED"}")
            appendLine("Lịch sử đã lưu chỉ để tra cứu; trạng thái VHAL phía trên thuộc phiên hiện tại.")
            appendLine()
            permissions.forEach { permission ->
                val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                appendLine("${permission.substringAfterLast('.')}=${if (granted) "GRANTED" else "DENIED"}")
            }
            appendLine()
            appendLine("Lịch sử API xe gần đây")
            appendLine("ACCEPTED_UNVERIFIED: API nhận lệnh, chưa xác nhận xe thực hiện.")
            val failures = VehicleTelemetryRegistry.diagnosticFailures()
            if (failures.isEmpty()) appendLine("Chưa ghi nhận lỗi. Thử một lệnh đang lỗi rồi mở lại mục này.")
            else failures.forEach { appendLine(it) }
        }
        ThtUi.showDialog(context, R.drawable.ic_ui_car, "Chẩn đoán kết nối xe", report,
            secondaryLabel = "Sao chép", onSecondary = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("THT Voice - kết nối xe", report))
                Toast.makeText(context, "Đã sao chép chẩn đoán kết nối xe.", Toast.LENGTH_SHORT).show()
            })
    }
}
