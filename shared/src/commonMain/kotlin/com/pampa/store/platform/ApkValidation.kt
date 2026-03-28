package com.pampa.store.platform

import com.pampa.store.data.model.ApkValidationResult

interface ApkValidationService {
    suspend fun validate(apkPath: String): ApkValidationResult
}

expect fun createApkValidationService(): ApkValidationService
