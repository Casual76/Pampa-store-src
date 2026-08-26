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

    @Test
    fun `la stessa versione non si annuncia due volte`() {
        val apps = listOf(updatable("alpha", "1.0.0", "1.1.0"))

        val first = updatesToAnnounce(apps, emptyMap())
        val second = updatesToAnnounce(apps, announcedVersions(apps))

        assertEquals(listOf("alpha"), first.map { it.entry.id })
        assertTrue(second.isEmpty())
    }

    @Test
    fun `una versione ancora piu nuova e una notizia nuova`() {
        val announced = announcedVersions(listOf(updatable("alpha", "1.0.0", "1.1.0")))

        val fresh = updatesToAnnounce(listOf(updatable("alpha", "1.0.0", "1.2.0")), announced)

        assertEquals(listOf("alpha"), fresh.map { it.entry.id })
    }

    @Test
    fun `il registro si ricostruisce dall insieme corrente invece di accumulare`() {
        val before = announcedVersions(
            listOf(updatable("alpha", "1.0.0", "1.1.0"), updatable("beta", "2.0.0", "2.1.0")),
        )
        assertEquals(2, before.size)

        // alpha e' stata installata: esce dall'insieme aggiornabile, quindi esce dal registro.
        val after = announcedVersions(listOf(updatable("beta", "2.0.0", "2.1.0")))

        assertEquals(setOf("beta"), after.keys)
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
