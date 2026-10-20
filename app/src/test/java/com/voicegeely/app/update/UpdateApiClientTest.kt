package com.voicegeely.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateApiClientTest {
    @Test fun acceptsOnlyExactHttpsUpdateDownloadRouteOnConfiguredWorker() {
        assertTrue(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-coolray-license.mavoice.workers.dev/api/update/apk?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("http://tht-voice-coolray-license.mavoice.workers.dev/api/update/apk?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://evil.example/api/update/apk?asset_id=123"))
        // Coolray never downloads updates from the EX2 server.
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-license.mavoice.workers.dev/api/update/apk?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-coolray-license.mavoice.workers.dev/api/status"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-coolray-license.mavoice.workers.dev/api/update/other?asset_id=123"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-coolray-license.mavoice.workers.dev/api/update/apk"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-coolray-license.mavoice.workers.dev/api/update/apk?asset_id=0"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-coolray-license.mavoice.workers.dev/api/update/apk?asset_id=123&extra=1"))
        assertFalse(UpdateApiClient.isTrustedWorkerUrl("https://tht-voice-coolray-license.mavoice.workers.dev/api/update/apk?asset_id=123#fragment"))
    }
}
