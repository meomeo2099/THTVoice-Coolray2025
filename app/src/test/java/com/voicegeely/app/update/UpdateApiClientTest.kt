package com.voicegeely.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateApiClientTest {
    @Test fun acceptsOnlyExactHttpsUpdateDownloadRouteOnConfiguredWorker() {
        val host = "tht-voice-coolray2025-license.mavoice.workers.dev"
        val worker = "https://$host"
        assertTrue(UpdateApiClient.isTrustedWorkerUrl("$worker/api/update/apk?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("http://$host/api/update/apk?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://evil.example/api/update/apk?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-coolray-license.mavoice.workers.dev/api/update/apk?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-license.mavoice.workers.dev/api/update/apk?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("$worker/api/status"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("$worker/api/update/other?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("$worker/api/update/apk"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("$worker/api/update/apk?asset_id=0"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("$worker/api/update/apk?asset_id=123&extra=1"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("$worker/api/update/apk?asset_id=123#fragment"))
    }
}
