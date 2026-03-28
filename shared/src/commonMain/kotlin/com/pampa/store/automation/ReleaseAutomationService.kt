package com.pampa.store.automation

import com.pampa.store.data.model.DetectedArtifact
import com.pampa.store.data.model.ReleaseAutomationProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

sealed interface ReleaseAutomationEvent {
    data class ArtifactDetected(
        val profileId: String,
        val artifact: DetectedArtifact
    ) : ReleaseAutomationEvent

    data class ProfileError(
        val profileId: String,
        val message: String?
    ) : ReleaseAutomationEvent
}

interface ReleaseAutomationService {
    val events: Flow<ReleaseAutomationEvent>

    fun start()

    fun updateProfiles(profiles: List<ReleaseAutomationProfile>)

    fun stop()
}

object NoOpReleaseAutomationService : ReleaseAutomationService {
    override val events: Flow<ReleaseAutomationEvent> = emptyFlow()

    override fun start() = Unit

    override fun updateProfiles(profiles: List<ReleaseAutomationProfile>) = Unit

    override fun stop() = Unit
}
