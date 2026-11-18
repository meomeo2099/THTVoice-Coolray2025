package com.voicegeely.app.voice

/** One reply from THT Split (API v2 callback) or a local outcome when no callback arrived. */
data class SplitReply(
    val status: String,
    val code: String? = null,
    val message: String? = null,
    val verified: Boolean = false
) {
    companion object {
        const val SUCCESS = "success"
        const val REQUESTED = "requested"
        const val ERROR = "error"
        /** Local: no callback before the wait ended (token sai / API tắt / ADB lần đầu chậm). */
        const val TIMEOUT = "timeout"
        /** Local: neither the signature permission nor a saved token is available. */
        const val NO_AUTH = "no_auth"
        /** Local: sendBroadcast itself threw. */
        const val SEND_FAILED = "send_failed"
    }
}

/** Transport boundary to com.tht.split.coolray / com.tht.split; the Android implementation is [ThtSplitClient]. */
interface SplitScreenClient {
    fun isSplitInstalled(): Boolean
    fun isPackageInstalled(packageName: String): Boolean
    /** Blocks until the callback arrives or the client's wait ends. Never retries a start by itself. */
    fun send(command: String, extras: Map<String, Any>): SplitReply
}

/**
 * Pair the voice side last sent with a success/requested reply. Split API v2 does not report
 * the current pair, so "YouTube toàn màn hình" can only resolve a side from this memory.
 */
class SplitPairMemory {
    @Volatile var left: String? = null
    @Volatile var right: String? = null

    fun sideOf(packages: List<String>): SplitSide? = when {
        left != null && left in packages -> SplitSide.LEFT
        right != null && right in packages -> SplitSide.RIGHT
        else -> null
    }

    fun swap() {
        val l = left
        left = right
        right = l
    }

    fun clear() {
        left = null
        right = null
    }
}

/** Typed split-screen handler; never claims success unless THT Split answered `success`. */
class SplitScreenExecutor(
    private val client: SplitScreenClient,
    private val memory: SplitPairMemory = SplitPairMemory()
) {
    fun execute(command: VoiceCommand.SplitScreen): CommandExecutionResult {
        if (!client.isSplitInstalled()) return CommandExecutionResult.Rejected(NOT_INSTALLED)
        return when (command.action) {
            SplitAction.START -> start(command)
            SplitAction.SWAP -> send("swap", emptyMap()) { memory.swap() }
            SplitAction.RATIO -> {
                val ratio = command.ratio ?: 50
                if (ratio !in 30..70) CommandExecutionResult.Rejected(RATIO_RANGE)
                else send("ratio", mapOf("ratio" to ratio))
            }
            SplitAction.REPLACE -> replace(command)
            SplitAction.FULLSCREEN -> fullscreen(command)
            SplitAction.RESTORE -> send("restore", emptyMap())
            SplitAction.HIDE -> send("hide", emptyMap())
            SplitAction.SHOW -> send("show", emptyMap())
            SplitAction.STOP -> send("stop", emptyMap())
        }
    }

    private fun start(command: VoiceCommand.SplitScreen): CommandExecutionResult {
        val leftTarget = command.left
        val rightTarget = command.right
        if (leftTarget == null || rightTarget == null) return send("start", emptyMap())
        val left = resolve(leftTarget) ?: return CommandExecutionResult.Rejected(VoiceResponseFormatter.APP_NOT_FOUND)
        val right = resolve(rightTarget) ?: return CommandExecutionResult.Rejected(VoiceResponseFormatter.APP_NOT_FOUND)
        if (left == right) return CommandExecutionResult.Rejected(SAME_APP)
        return send("start", mapOf("left" to left, "right" to right, "ratio" to 50)) {
            memory.left = left
            memory.right = right
        }
    }

    private fun replace(command: VoiceCommand.SplitScreen): CommandExecutionResult {
        val side = command.side ?: return CommandExecutionResult.Rejected(SIDE_UNKNOWN)
        val target = command.app ?: return CommandExecutionResult.Rejected(VoiceResponseFormatter.APP_NOT_FOUND)
        val pkg = resolve(target) ?: return CommandExecutionResult.Rejected(VoiceResponseFormatter.APP_NOT_FOUND)
        return send("replace", mapOf("side" to sideExtra(side), "package" to pkg)) {
            if (side == SplitSide.LEFT) memory.left = pkg else memory.right = pkg
        }
    }

    private fun fullscreen(command: VoiceCommand.SplitScreen): CommandExecutionResult {
        val side = command.side ?: command.app?.let { memory.sideOf(candidates(it)) }
            ?: return CommandExecutionResult.Rejected(SIDE_UNKNOWN)
        return send("fullscreen", mapOf("side" to sideExtra(side)))
    }

    private fun send(command: String, extras: Map<String, Any>, onLayoutSent: () -> Unit = {}): CommandExecutionResult {
        val reply = try {
            client.send(command, extras)
        } catch (t: Throwable) {
            return CommandExecutionResult.Failed(t.message ?: SEND_FAILED, t::class.java.name)
        }
        return when (reply.status) {
            SplitReply.SUCCESS -> {
                onLayoutSent()
                if (command == "stop") memory.clear()
                CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE, reply.message)
            }
            SplitReply.REQUESTED -> {
                onLayoutSent()
                CommandExecutionResult.AcceptedUnverified(reply.message ?: REQUESTED)
            }
            SplitReply.TIMEOUT -> CommandExecutionResult.AcceptedUnverified(TIMEOUT)
            SplitReply.NO_AUTH -> CommandExecutionResult.Rejected(NO_AUTH)
            SplitReply.SEND_FAILED -> CommandExecutionResult.Failed(reply.message ?: SEND_FAILED)
            else -> when (reply.code) {
                "INVALID_COMMAND", "INVALID_ARGUMENT", "API_DISABLED", "UNSUPPORTED_OPERATION",
                "OVERLAY_REQUIRED", "ACCESS_BLOCKED" ->
                    CommandExecutionResult.Rejected(reply.message ?: errorText(reply.code))
                else -> CommandExecutionResult.Failed(reply.message ?: errorText(reply.code))
            }
        }
    }

    /** Installed package for a catalog target; YouTube follows the owner's client order. */
    private fun resolve(target: AppTarget): String? = candidates(target).firstOrNull { client.isPackageInstalled(it) }

    private fun candidates(target: AppTarget): List<String> =
        if (target == AppTarget.YOUTUBE) AndroidAppCatalog.youtubeFamily.flatMap { AndroidAppCatalog.packagesFor(it) }
        else AndroidAppCatalog.packagesFor(target)

    private fun sideExtra(side: SplitSide) = if (side == SplitSide.LEFT) "left" else "right"

    private fun errorText(code: String?): String = when (code) {
        "BUSY" -> "THT Split đang bận, thử lại sau giây lát."
        "API_DISABLED" -> "Hãy bật cho phép nhận lệnh từ THT Voice trong THT Split."
        "OVERLAY_REQUIRED" -> "THT Split cần quyền hiển thị trên ứng dụng khác."
        "UNSUPPORTED_OPERATION" -> "Phương thức chia màn hình đang chọn không hỗ trợ thao tác này."
        else -> "THT Split không chia được màn hình."
    }

    companion object {
        const val NOT_INSTALLED = "Chưa cài THT Split nên chưa chia được màn hình."
        const val NO_AUTH = "Chưa có mã kết nối THT Split. Vào cài đặt THT Voice, mục chia màn hình, dán mã từ THT Split."
        const val TIMEOUT = "THT Split chưa phản hồi. Kiểm tra mục cho phép nhận lệnh từ THT Voice và mã kết nối."
        const val REQUESTED = "Đã gửi yêu cầu, THT Split chưa xác nhận bố cục."
        const val SEND_FAILED = "Không gửi được lệnh sang THT Split."
        const val RATIO_RANGE = "Tỷ lệ chia chỉ từ 30 đến 70 phần trăm."
        const val SIDE_UNKNOWN = "Chưa rõ ứng dụng đang ở bên nào. Hãy nói bên trái hoặc bên phải toàn màn hình."
        const val SAME_APP = "Hai bên cần hai ứng dụng khác nhau."
    }
}
