package com.pampa.store.platform

import com.pampa.store.data.model.ApkValidationResult

actual fun createApkValidationService(): ApkValidationService = object : ApkValidationService {
    override suspend fun validate(apkPath: String): ApkValidationResult {
        return ApkValidationResult.Unsupported(
            "La validazione APK preventiva e' disponibile solo dal pannello admin desktop."
        )
    }
}
