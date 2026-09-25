package dev.samson.s24root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val S9210_RELEASE = "6.1.145-android14-11-33419968-abS9210ZHS6DZG1"
private const val S9280_RELEASE = "6.1.145-android14-11-33419968-abS9280ZCS6DZF2"

private fun snapshot(model: String, kernelRelease: String) = DeviceSnapshot(
    manufacturer = "samsung",
    model = model,
    device = "e1q",
    kernelRelease = kernelRelease,
    kernelVersionInfo = "#1 SMP PREEMPT",
    machine = "aarch64",
    buildId = "BP4A.251205.006.S9210ZHS6DZG1",
    fingerprint = "samsung/e1qzhx/e1q:16/BP4A.251205.006/S9210ZHS6DZG1:user/release-keys",
    androidRelease = "16",
    sdk = 36,
    abi = "arm64-v8a",
    pageSize = 4096,
)

private fun bundled(s9210: Boolean = true) = listOf(
    TargetProfile(
        profileId = "e1q-S9210ZHS6DZG1",
        displayName = "Galaxy S24 China",
        models = setOf("SM-S9210"),
        kernelVersions = setOf("6.1.145"),
        kernelReleases = setOf(S9210_RELEASE),
        exploit = BundledArtifact("payloads/p/cve.so", "2dc137cc1dca72f896ee16347f3137dd0c521ecdee56e1922fb58e733e9b4059", 104128),
        kernelSu = BundledArtifact("payloads/p/ksud", "67b49cadfbbc518ac1fca3f7e514782110e896103882e7caa5cccaa1572ee542", 4895088),
    ),
).filter { s9210 }

private fun remote(vararg models: String) = models.map { model ->
    TargetProfile(
        profileId = "remote-$model",
        displayName = "Remote $model",
        models = setOf(model),
        kernelVersions = setOf("6.1.145"),
        exploit = RemoteArtifact("https://example.invalid/e", 1),
        kernelSu = RemoteArtifact("https://example.invalid/k", 1),
    )
}

class ProfileResolutionTest {
    @Test
    fun bundledProfileResolvesWithoutLoadingTheRemoteFeed() {
        var remoteLoads = 0
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", S9210_RELEASE),
            bundled = bundled(),
            remote = {
                remoteLoads++
                remote("SM-S9210")
            },
        )
        assertTrue(resolution is Resolution.Found)
        assertEquals("e1q-S9210ZHS6DZG1", (resolution as Resolution.Found).profile.profileId)
        assertEquals("the online feed must not be touched at all", 0, remoteLoads)
    }

    @Test
    fun aBundledProfileIsNotReplacedByAnOnlineEntryForTheSameModel() {
        // Even with a matching remote entry present, the bundled one wins and
        // the feed is never loaded, so its digest and firmware gate still apply.
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", S9210_RELEASE),
            bundled = bundled(),
            remote = { error("the online feed must not be loaded for the reserved model") },
        )
        assertEquals("e1q-S9210ZHS6DZG1", (resolution as Resolution.Found).profile.profileId)
        assertTrue(resolution.profile.bundled)
    }

    @Test
    fun theSameModelOnAnotherFirmwareIsRefusedWithoutLoadingTheFeed() {
        var remoteLoads = 0
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", S9280_RELEASE),
            bundled = bundled(),
            remote = {
                remoteLoads++
                remote("SM-S9210")
            },
        )
        assertTrue(resolution is Resolution.Failed)
        val failure = (resolution as Resolution.Failed).failure
        assertTrue(failure is ResolutionFailure.WrongFirmware)
        assertEquals(S9210_RELEASE, (failure as ResolutionFailure.WrongFirmware).supportedRelease)
        assertEquals(0, remoteLoads)
    }

    @Test
    fun theReservedModelFailsClosedWhenTheBundledManifestIsUnusable() {
        // An empty bundled catalogue means the manifest was missing or malformed.
        // The online feed must not get to supply a payload for the reserved model,
        // because only the bundled path carries the digest and firmware guarantees.
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", S9210_RELEASE),
            bundled = bundled(s9210 = false),
            remote = { remote("SM-S9210") },
        )
        assertTrue(resolution is Resolution.Failed)
        assertEquals(
            ResolutionFailure.ReservedModelWithoutBundledPayload,
            (resolution as Resolution.Failed).failure,
        )
    }

    @Test
    fun theReservedModelIsMatchedCaseInsensitively() {
        assertTrue(isReservedModel("sm-s9210"))
        assertTrue(isReservedModel("SM-S9210"))
        assertFalse(isReservedModel("SM-S9280"))
        // The list is deliberately narrow, so the other S24 variants still come
        // from the online feed.
        assertFalse(isReservedModel("SM-S921B"))
        assertFalse(isReservedModel("SM-S926B"))
        assertFalse(isReservedModel("SM-S928B"))
    }

    @Test
    fun otherModelsStillResolveFromTheOnlineFeed() {
        var remoteLoads = 0
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9280", S9280_RELEASE),
            bundled = bundled(),
            remote = {
                remoteLoads++
                remote("SM-S9280")
            },
        )
        assertTrue(resolution is Resolution.Found)
        assertFalse((resolution as Resolution.Found).profile.bundled)
        assertEquals(1, remoteLoads)
    }

    @Test
    fun anUnsupportedModelReportsNoProfileRatherThanAFirmwareMismatch() {
        val resolution = resolveProfile(
            snapshot = snapshot("SM-A155F", "5.10.236-android13-1"),
            bundled = bundled(),
            remote = { remote("SM-S9280") },
        )
        assertTrue(resolution is Resolution.Failed)
        assertEquals(ResolutionFailure.NoProfile, (resolution as Resolution.Failed).failure)
    }

    @Test
    fun anEmptyCatalogueEverywhereFailsCleanly() {
        for (model in listOf("SM-S9210", "SM-S9280")) {
            val resolution = resolveProfile(
                snapshot = snapshot(model, S9210_RELEASE),
                bundled = emptyList(),
                remote = { emptyList() },
            )
            assertTrue(resolution is Resolution.Failed)
            assertEquals(
                if (isReservedModel(model)) ResolutionFailure.ReservedModelWithoutBundledPayload
                else ResolutionFailure.NoProfile,
                (resolution as Resolution.Failed).failure,
            )
        }
    }
}
