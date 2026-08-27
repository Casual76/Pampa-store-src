package com.pampa.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogResolverTest {

    private fun entry(
        id: String,
        packageName: String = "com.example.$id",
        stable: String? = "1.2.0",
        beta: String? = null,
    ) = AppEntry(
        id = id,
        name = id.replaceFirstChar(Char::uppercase),
        packageName = packageName,
        stable = stable?.let { VersionInfo(version = it, apkAsset = "$id.apk", releaseTag = "v$it") },
        beta = beta?.let { VersionInfo(version = it, apkAsset = "$id.apk", releaseTag = "v$it") },
    )

    private fun lookup(vararg installed: Pair<String, String>): InstalledVersionLookup {
        val map = installed.toMap()
        return InstalledVersionLookup { packageName -> map[packageName] }
    }

    @Test
    fun `un app non installata non e aggiornabile e non dichiara una versione disponibile`() {
        val apps = resolveStoreApps(listOf(entry("alpha")), lookup())

        val alpha = apps.single()
        assertNull(alpha.installedVersion)
        assertNull(alpha.availableVersion)
        assertFalse(alpha.hasUpdate)
    }

    @Test
    fun `una versione piu vecchia della stabile e aggiornabile`() {
        val apps = resolveStoreApps(
            listOf(entry("alpha", stable = "1.3.0")),
            lookup("com.example.alpha" to "1.2.0"),
        )

        val alpha = apps.single()
        assertTrue(alpha.hasUpdate)
        assertEquals("1.3.0", alpha.availableVersion)
        assertEquals(Channel.STABLE, alpha.installedChannel)
    }

    @Test
    fun `chi ha la beta installata resta sul canale beta`() {
        val apps = resolveStoreApps(
            listOf(entry("alpha", stable = "1.2.0", beta = "1.3.0-beta")),
            lookup("com.example.alpha" to "1.3.0-beta"),
        )

        val alpha = apps.single()
        assertEquals(Channel.BETA, alpha.installedChannel)
        assertEquals("1.3.0-beta", alpha.availableVersion)
        assertFalse(alpha.hasUpdate)
    }

    @Test
    fun `lo Store non compare fra le app risolte`() {
        val apps = resolveStoreApps(
            listOf(entry(STORE_APP_ID), entry("alpha")),
            lookup(),
        )

        assertEquals(listOf("alpha"), apps.map { it.entry.id })
    }

    // --- l'annuncio ----------------------------------------------------------------------

    private fun updatable(id: String, from: String, to: String) = StoreApp(
        entry = entry(id, stable = to),
        installedVersion = from,
        installedChannel = Channel.STABLE,
        availableVersion = to,
        hasUpdate = true,
    )

    private fun pending(id: String, from: String, to: String) =
        listOf(updatable(id, from, to)).pendingUpdates()

    @Test
    fun `la stessa versione non si annuncia due volte`() {
        val apps = pending("alpha", "1.0.0", "1.1.0")

        val first = updatesToAnnounce(apps, emptyMap())
        val second = updatesToAnnounce(apps, announcedVersions(apps))

        assertEquals(listOf("alpha"), first.map { it.id })
        assertTrue(second.isEmpty())
    }

    @Test
    fun `una versione ancora piu nuova e una notizia nuova`() {
        val announced = announcedVersions(pending("alpha", "1.0.0", "1.1.0"))

        val fresh = updatesToAnnounce(pending("alpha", "1.0.0", "1.2.0"), announced)

        assertEquals(listOf("alpha"), fresh.map { it.id })
    }

    @Test
    fun `il registro si ricostruisce dall insieme corrente invece di accumulare`() {
        val before = announcedVersions(
            pending("alpha", "1.0.0", "1.1.0") + pending("beta", "2.0.0", "2.1.0"),
        )
        assertEquals(2, before.size)

        // alpha e' stata installata: esce dall'insieme aggiornabile, quindi esce dal registro.
        val after = announcedVersions(pending("beta", "2.0.0", "2.1.0"))

        assertEquals(setOf("beta"), after.keys)
    }

    @Test
    fun `solo cio che e aggiornabile diventa un annuncio`() {
        val apps = listOf(
            updatable("alpha", "1.0.0", "1.1.0"),
            StoreApp(entry = entry("beta"), installedVersion = "1.2.0", availableVersion = "1.2.0"),
            StoreApp(entry = entry("gamma")),
        )

        assertEquals(listOf("alpha"), apps.pendingUpdates().map { it.id })
    }

    // --- lo Store, che non e' nel catalogo -----------------------------------------------

    private fun releases(stable: String? = null, beta: String? = null) = StoreReleaseChannels(
        stable = stable?.let { VersionInfo(version = it, apkAsset = "store.apk", releaseTag = "v$it") },
        beta = beta?.let { VersionInfo(version = it, apkAsset = "store.apk", releaseTag = "v$it") },
    )

    @Test
    fun `una versione dello Store piu recente di quella in esecuzione e un aggiornamento`() {
        val update = resolveStoreUpdate(releases(stable = "1.7.0"), Channel.STABLE, "1.6.3")

        assertEquals("1.7.0", update?.version?.version)
    }

    @Test
    fun `la stessa versione dello Store non e un aggiornamento`() {
        assertNull(resolveStoreUpdate(releases(stable = "1.6.3"), Channel.STABLE, "1.6.3"))
    }

    @Test
    fun `chi sta sul canale beta dello Store prende la beta`() {
        val update = resolveStoreUpdate(releases(stable = "1.7.0", beta = "1.8.0"), Channel.BETA, "1.6.3")

        assertEquals("1.8.0", update?.version?.version)
    }

    @Test
    fun `sul canale beta senza una beta si ripiega sulla stabile`() {
        val update = resolveStoreUpdate(releases(stable = "1.7.0"), Channel.BETA, "1.6.3")

        assertEquals("1.7.0", update?.version?.version)
    }

    @Test
    fun `senza release non c e niente da aggiornare`() {
        assertNull(resolveStoreUpdate(releases(), Channel.STABLE, "1.6.3"))
    }

    @Test
    fun `lo Store si annuncia accanto alle app e con il proprio id`() {
        val store = resolveStoreUpdate(releases(stable = "1.7.0"), Channel.STABLE, "1.6.3")!!
        val updates = pending("alpha", "1.0.0", "1.1.0") + store.pendingUpdate("1.6.3")

        assertEquals(listOf("alpha", STORE_APP_ID), updates.map { it.id })
        assertEquals(STORE_APP_NAME, updates.last().name)
        assertEquals("1.6.3", updates.last().installedVersion)
        assertEquals(mapOf("alpha" to "1.1.0", STORE_APP_ID to "1.7.0"), announcedVersions(updates))
    }

    @Test
    fun `lo Store gia annunciato non si riannuncia al giro dopo`() {
        val store = resolveStoreUpdate(releases(stable = "1.7.0"), Channel.STABLE, "1.6.3")!!
        val updates = listOf(store.pendingUpdate("1.6.3"))

        assertTrue(updatesToAnnounce(updates, announcedVersions(updates)).isEmpty())
    }

    // --- tornare indietro ---------------------------------------------------------------

    @Test
    fun `una versione piu vecchia di quella installata vuole la disinstallazione`() {
        assertTrue(requiresUninstallFirst(installedVersion = "1.6.0", targetVersion = "1.3.0"))
    }

    @Test
    fun `risalire e una installazione qualunque`() {
        assertFalse(requiresUninstallFirst(installedVersion = "1.3.0", targetVersion = "1.6.0"))
    }

    @Test
    fun `reinstallare la stessa versione non disinstalla niente`() {
        assertFalse(requiresUninstallFirst(installedVersion = "1.6.0", targetVersion = "1.6.0"))
    }

    @Test
    fun `un app non installata non ha niente da disinstallare`() {
        assertFalse(requiresUninstallFirst(installedVersion = null, targetVersion = "1.3.0"))
    }
}
