package com.voicegeely.app.voice

/**
 * Combines the existing scene component observation with the read-only provider contract probe.
 *
 * This resolver never upgrades an unconfirmed component observation to Confirmed and never maps a
 * raw scene_mode value to REST/CAMPING. The provider data is evidence only until head-unit semantics
 * are proven separately.
 */
object SceneCapabilityQueryResolver {
    fun resolve(
        component: AndroidBackendResult,
        snapshot: SceneProviderSnapshot?
    ): AndroidBackendResult {
        if (!component.accepted || !component.confirmed || snapshot == null) return component

        val componentText = component.actualValue?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
        val providerText = when (SceneProviderContractClassifier.classify(snapshot)) {
            SceneProviderContractClassifier.State.PROVIDER_NOT_VISIBLE ->
                "Scene provider không hiển thị trên thiết bị"
            SceneProviderContractClassifier.State.QUERY_DENIED ->
                snapshot.detail?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
                    ?: "Scene provider hiện chưa đọc được"
            SceneProviderContractClassifier.State.SCHEMA_UNKNOWN ->
                "Scene provider đọc được nhưng schema chưa có scene_mode đã xác minh"
            SceneProviderContractClassifier.State.MODE_READABLE ->
                "Scene provider có scene_mode đọc được; giá trị thô chỉ dùng làm bằng chứng và chưa ánh xạ thành chế độ nghỉ/cắm trại"
        }

        val combined = listOfNotNull(componentText, providerText)
            .distinct()
            .joinToString("; ")

        return component.copy(
            verificationSource = VerificationSource.ANDROID_STATE,
            actualValue = combined.ifBlank { component.actualValue }
        )
    }
}
