package dev.samson.s24root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val S9210_RELEASE = "6.1.145-android14-11-33419968-abS9210ZHS6DZG1"
private const val S9280_RELEASE = "6.1.145-android14-11-33419968-abS9280ZCS6DZF2"
private const val EZIF_RELEASE = "6.1.162-android14-11-34343818-abS9210ZHU6EZIF"
private const val UNKNOWN_S9210_RELEASE = "6.1.200-android14-11-99999999-abS9210ZHS6XXXX"

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
        kernelSu = BundledArtifact("payloads/p/ksud", "8874894560e46dd3ab711386f07c3d89635bdaae0e02a806878555bdbdc15049", 4995304),
    ),
).filter { s9210 }

private fun candidateProfile() = TargetProfile(
    profileId = "e1q-S9210ZHU6EZIF",
    displayName = "Galaxy S24 China [candidate]",
    models = setOf("SM-S9210"),
    kernelVersions = setOf("6.1.162"),
    kernelReleases = setOf(EZIF_RELEASE),
    exploit = BundledArtifact("payloads/e/cve.so", "2dc137cc1dca72f896ee16347f3137dd0c521ecdee56e1922fb58e733e9b4059", 104128),
    kernelSu = BundledArtifact("payloads/e/ksud", "8874894560e46dd3ab711386f07c3d89635bdaae0e02a806878555bdbdc15049", 4995304),
)

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
        assertEquals(S9210_RELEASE, (failure as ResolutionFailure.WrongFirmware).supportedReleases)
        assertEquals(0, remoteLoads)
    }

    @Test
    fun unknownFirmwareWithOptInListsBothBundledReleasesWithoutLoadingTheFeed() {
        var remoteLoads = 0
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", UNKNOWN_S9210_RELEASE),
            bundled = bundled() + candidateProfile(),
            remote = {
                remoteLoads++
                remote("SM-S9210")
            },
            allowCandidates = true,
        )
        assertTrue(resolution is Resolution.Failed)
        val failure = (resolution as Resolution.Failed).failure
        assertTrue("expected WrongFirmware, got $failure", failure is ResolutionFailure.WrongFirmware)
        val releases = (failure as ResolutionFailure.WrongFirmware).supportedReleases
        assertEquals(listOf(S9210_RELEASE, EZIF_RELEASE).sorted().joinToString(), releases)
        assertTrue(releases.contains(S9210_RELEASE))
        assertTrue(releases.contains(EZIF_RELEASE))
        assertEquals(0, remoteLoads)
    }

    @Test
    fun unknownFirmwareWithoutOptInListsOnlyTheStableRelease() {
        var remoteLoads = 0
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", UNKNOWN_S9210_RELEASE),
            bundled = bundled() + candidateProfile(),
            remote = {
                remoteLoads++
                remote("SM-S9210")
            },
        )
        assertTrue(resolution is Resolution.Failed)
        val failure = (resolution as Resolution.Failed).failure
        assertTrue("expected WrongFirmware, got $failure", failure is ResolutionFailure.WrongFirmware)
        val releases = (failure as ResolutionFailure.WrongFirmware).supportedReleases
        assertEquals(S9210_RELEASE, releases)
        assertFalse(releases.contains(EZIF_RELEASE))
        assertEquals("the reserved model must not reach the online feed", 0, remoteLoads)
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

    @Test
    fun candidateProfileResolvesWhenOptedInWithoutLoadingTheRemoteFeed() {
        var remoteLoads = 0
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", EZIF_RELEASE),
            bundled = bundled() + candidateProfile(),
            remote = {
                remoteLoads++
                remote("SM-S9210")
            },
            allowCandidates = true,
        )
        assertTrue(resolution is Resolution.Found)
        assertEquals("e1q-S9210ZHU6EZIF", (resolution as Resolution.Found).profile.profileId)
        assertEquals("the online feed must not be touched at all", 0, remoteLoads)
    }

    @Test
    fun candidateProfileWithoutOptInReportsOptInRatherThanAFirmwareMismatch() {
        var remoteLoads = 0
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", EZIF_RELEASE),
            bundled = bundled() + candidateProfile(),
            remote = {
                remoteLoads++
                remote("SM-S9210")
            },
        )
        assertTrue(resolution is Resolution.Failed)
        val failure = (resolution as Resolution.Failed).failure
        assertTrue("expected CandidateRequiresOptIn, got $failure", failure is ResolutionFailure.CandidateRequiresOptIn)
        assertEquals("e1q-S9210ZHU6EZIF", (failure as ResolutionFailure.CandidateRequiresOptIn).profileId)
        assertEquals("the reserved model must not reach the online feed", 0, remoteLoads)
    }

    @Test
    fun stableProfileStillResolvesWithDefaultFlagWhenACandidateIsPresent() {
        var remoteLoads = 0
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9210", S9210_RELEASE),
            bundled = bundled() + candidateProfile(),
            remote = {
                remoteLoads++
                remote("SM-S9210")
            },
        )
        assertTrue(resolution is Resolution.Found)
        assertEquals("e1q-S9210ZHS6DZG1", (resolution as Resolution.Found).profile.profileId)
        assertEquals(0, remoteLoads)
    }

    @Test
    fun remoteCandidateEntryNeverResolvesWithoutOptIn() {
        // A remote entry reusing a candidate profile id must not resolve while
        // opt-in is off, even for a non-reserved model whose firmware matches.
        val candidateRemote = TargetProfile(
            profileId = "e1q-S9210ZHU6EZIF",
            displayName = "Remote candidate",
            models = setOf("SM-S9280"),
            kernelVersions = setOf("6.1.145"),
            exploit = RemoteArtifact("https://example.invalid/e", 1),
            kernelSu = RemoteArtifact("https://example.invalid/k", 1),
        )
        val resolution = resolveProfile(
            snapshot = snapshot("SM-S9280", S9280_RELEASE),
            bundled = bundled(),
            remote = { listOf(candidateRemote) },
        )
        assertTrue("expected no resolution, got $resolution", resolution is Resolution.Failed)
        assertEquals(ResolutionFailure.NoProfile, (resolution as Resolution.Failed).failure)
    }
}
