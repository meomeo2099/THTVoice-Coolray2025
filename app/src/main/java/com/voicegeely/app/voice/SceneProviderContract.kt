package com.voicegeely.app.voice

/**
 * Read-only description of the base-platform scene provider surface (not verified on Coolray).
 *
 * This type intentionally does not contain any write operation. Provider/action names discovered
 * from reference material are not treated as a writable contract until the head unit exposes a
 * stable schema and state semantics at runtime.
 */
data class SceneProviderSnapshot(
    val providerVisible: Boolean,
    val querySucceeded: Boolean,
    val columns: List<String> = emptyList(),
    val firstRow: Map<String, String?> = emptyMap(),
    val detail: String? = null
) {
    val sceneModeColumn: String?
        get() = columns.firstOrNull { it.equals(SCENE_MODE_COLUMN, ignoreCase = true) }

    val hasReadableSceneMode: Boolean
        get() {
            val column = sceneModeColumn ?: return false
            return querySucceeded && firstRow.containsKey(column)
        }

    fun sceneModeValueOrNull(): String? {
        val column = sceneModeColumn ?: return null
        return firstRow[column]?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val PROVIDER_URI = "content://com.flyme.auto.scenedirector/scene"
        const val SCENE_MODE_COLUMN = "scene_mode"
    }
}

/** Fail-closed classifier used by JVM tests and Android provider probing. */
object SceneProviderContractClassifier {
    enum class State {
        PROVIDER_NOT_VISIBLE,
        QUERY_DENIED,
        SCHEMA_UNKNOWN,
        MODE_READABLE
    }

    fun classify(snapshot: SceneProviderSnapshot): State = when {
        !snapshot.providerVisible -> State.PROVIDER_NOT_VISIBLE
        !snapshot.querySucceeded -> State.QUERY_DENIED
        !snapshot.hasReadableSceneMode -> State.SCHEMA_UNKNOWN
        else -> State.MODE_READABLE
    }

    /**
     * Diagnostic text only. A readable raw value is not mapped to REST/CAMPING here because the
     * exact mode-value semantics are not yet proven for THT Voice on the Coolray head unit.
     */
    fun diagnostic(snapshot: SceneProviderSnapshot): String = when (classify(snapshot)) {
        State.PROVIDER_NOT_VISIBLE -> "Scene provider không hiển thị trên thiết bị."
        State.QUERY_DENIED -> snapshot.detail ?: "Không đọc được scene provider."
        State.SCHEMA_UNKNOWN -> "Scene provider đọc được nhưng chưa thấy cột scene_mode đã xác minh."
        State.MODE_READABLE -> "Scene provider đọc được scene_mode=${snapshot.sceneModeValueOrNull()}; chưa ánh xạ giá trị này thành chế độ nghỉ/cắm trại."
    }
}
