package dev.samson.s24root

import android.content.Context
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the payload repository against the real device. The bundled tests
 * run wherever the app installs; the online test needs connectivity.
 */
@RunWith(AndroidJUnit4::class)
class PayloadRepositoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun bundledCatalogIsPresentAndSelfConsistent() {
        val bundled = PayloadRepository(context).loadBuiltinTargets()
        assertTrue("the APK should carry a bundled manifest", bundled.isNotEmpty())
        for (profile in bundled) {
            assertTrue("${profile.profileId} should be bundled", profile.bundled)
            assertTrue("${profile.profileId} must pin an exact release", profile.kernelReleases.isNotEmpty())
            for (artifact in listOf(profile.exploit, profile.kernelSu)) {
                val bundledArtifact = artifact as BundledArtifact
                val bytes = context.assets.open(bundledArtifact.assetPath).use { it.readBytes() }
                assertEquals(bundledArtifact.size, bytes.size.toLong())
                assertEquals(bundledArtifact.sha256, sha256(bytes.inputStream()))
            }
        }
    }

    /**
     * Runs the full staged-file path. On the target device this is the S9210
     * bundled profile; elsewhere the online feed answers and the bundled-only
     * assertions are skipped rather than failing.
     */
    @Test
    fun bundledProfileStagesVerifiedBytesAndReusesThem() {
        val repository = PayloadRepository(context)
        val profile = repository.resolveTarget(DeviceSnapshot.current())
        val bundled = profile.bundled

        val payloads = repository.acquire(profile) { }
        assertEquals(profile.exploit.size, payloads.exploit.length())
        assertEquals(profile.kernelSu.size, payloads.kernelSu.length())
        assertTrue(payloads.exploit.canRead())
        assertTrue(payloads.kernelSu.canRead())

        for (artifact in listOf(profile.exploit, profile.kernelSu)) {
            val staged = if (artifact === profile.exploit) payloads.exploit else payloads.kernelSu
            // The scratch name carries a random component, so match the suffix
            // rather than reconstructing the exact path: naming it would let
            // this assertion pass vacuously forever.
            val leftovers = staged.parentFile?.listFiles()?.filter { it.name.endsWith(".part") }.orEmpty()
            assertTrue("no .part file may be left behind, found $leftovers", leftovers.isEmpty())
            if (bundled) {
                val expected = (artifact as BundledArtifact).sha256
                assertEquals("digest mismatch for ${expected.take(12)}", expected, sha256OrNull(staged))
            }
        }

        // A second acquire must take the reuse path rather than rewriting.
        val reusedMessages = mutableListOf<String>()
        val reused = repository.acquire(profile) { reusedMessages += it }
        assertEquals(payloads.exploit.absolutePath, reused.exploit.absolutePath)
        if (bundled) {
            // Both the staging and the reuse message name the artifact, so
            // compare the whole localized string to tell them apart.
            val expected = context.getString(
                R.string.repo_staged_current,
                context.getString(R.string.artifact_exploit),
            )
            assertTrue("expected \"$expected\" in $reusedMessages", reusedMessages.contains(expected))
            assertFalse(
                "the second acquire must not re-unpack the payload",
                reusedMessages.contains(
                    context.getString(R.string.repo_staging, context.getString(R.string.artifact_exploit)),
                ),
            )
        }
    }

    @Test
    fun aCorruptStagedFileIsReplacedRatherThanTrusted() {
        val repository = PayloadRepository(context)
        val bundled = bundledProfile()
        // Decide before resolving, so a device that is not the target model
        // skips instead of reaching for the network.
        assumeTrue("this device is not the bundled target", bundled.passesFirmwareGate(DeviceSnapshot.current()))

        val profile = repository.resolveTarget(DeviceSnapshot.current())
        assumeTrue("this check needs a bundled profile", profile.bundled)
        val digest = (profile.kernelSu as BundledArtifact).sha256

        val staged = repository.acquire(profile) { }.kernelSu
        // acquire() leaves the loader read-only, so make it writable again
        // before planting same-length garbage over it.
        Os.chmod(staged.absolutePath, 0b110100100)
        staged.writeBytes(ByteArray(staged.length().toInt()) { 0x41 })
        assertFalse("a length check alone would have accepted this", stagedFileIsCurrent(staged, staged.length(), digest))

        val repaired = repository.acquire(profile) { }.kernelSu
        assertEquals(digest, sha256OrNull(repaired))
    }

    /**
     * The safety gate for this fork. Driven through an injected snapshot so it
     * is exercised on whatever device the suite runs on, not only on a phone
     * that happens to be on the wrong firmware.
     */
    @Test
    fun aBundledProfileIsRefusedOnAnythingButItsPinnedFirmware() {
        val bundled = bundledProfile()

        val sameModelOtherFirmware = DeviceSnapshot(
            manufacturer = "samsung",
            // Same model and same numeric kernel; only the pinned release differs.
            model = bundled.models.first(),
            device = "e1q",
            kernelRelease = "6.1.145-android14-11-99999-not-a-pinned-build",
            kernelVersionInfo = "#1 SMP PREEMPT",
            machine = "aarch64",
            buildId = "BP4A.251205.006.S9210ZHS6DZG1",
            fingerprint = "samsung/e1qzhx/e1q:16/BP4A.251205.006/S9210ZHS6DZG1:user/release-keys",
            androidRelease = "16",
            sdk = 36,
            abi = "arm64-v8a",
            pageSize = 4096,
        )
        assertFalse(bundled.matches(sameModelOtherFirmware))
        assertTrue(bundled.matchesDevice(sameModelOtherFirmware))
        assertFalse(bundled.passesFirmwareGate(sameModelOtherFirmware))

        val gated = PayloadRepository(context) { sameModelOtherFirmware }
        val failure = runCatching { gated.acquire(bundled) { } }.exceptionOrNull()
        val message = failure?.message
            ?: throw AssertionError("acquire() must refuse a bundled profile on the wrong firmware")
        assertTrue("expected the pinned release in: $message", message.contains(bundled.supportedKernelReleases))
        assertTrue(
            "expected the device release in: $message",
            message.contains(sameModelOtherFirmware.kernelRelease),
        )
    }

    @Test
    fun anUnsupportedFirmwareIsRefusedRatherThanProbed() {
        val repository = PayloadRepository(context)
        val bundled = repository.loadBuiltinTargets().firstOrNull() ?: return
        // A snapshot that shares the numeric kernel but not the pinned release
        // must not match, so a firmware update locks the payload out instead of
        // running another build's offsets.
        // Same numeric kernel version on purpose: only the exact-release gate
        // can tell this build apart from the pinned one.
        val wrongFirmware = DeviceSnapshot(
            manufacturer = "samsung",
            model = bundled.models.first(),
            device = "e1q",
            kernelRelease = "6.1.145-android14-11-99999-not-a-pinned-build",
            kernelVersionInfo = "#1 SMP PREEMPT",
            machine = "aarch64",
            buildId = "BP4A.251205.006.S9210ZHS6DZG1",
            fingerprint = "samsung/e1qzhx/e1q:16/BP4A.251205.006/S9210ZHS6DZG1:user/release-keys",
            androidRelease = "16",
            sdk = 36,
            abi = "arm64-v8a",
            pageSize = 4096,
        )
        assertFalse(bundled.matches(wrongFirmware))
        assertTrue(bundled.matchesDevice(wrongFirmware))
        assertFalse(bundled.passesFirmwareGate(wrongFirmware))

        // And the gate the repository actually applies must refuse to stage
        // anything for a device that does not match.
        val onThisDevice = DeviceSnapshot.current()
        if (!bundled.passesFirmwareGate(onThisDevice)) {
            val failure = runCatching { repository.acquire(bundled) { } }.exceptionOrNull()
            assertTrue(
                "expected a firmware refusal, got $failure",
                failure?.message?.contains(bundled.supportedKernelReleases) == true,
            )
            assertFalse(
                "nothing may be written for a refused firmware",
                File(File(context.filesDir, "payloads"), bundled.profileId).exists(),
            )
        }
    }

    @Test
    fun onlineProfileStillResolvesAndAcquires() {
        val repository = PayloadRepository(context)
        val snapshot = DeviceSnapshot.current()
        val profile = repository.resolveTarget(snapshot)
        // Skipped on the target device, where a bundled profile answers first
        // and there is nothing to fetch. Kept so the online path is still
        // exercised on any other supported model.
        assumeTrue("this device resolves a bundled profile", !profile.bundled)
        assertTrue(profile.matches(snapshot))
        val payloads = repository.acquire(profile) { }
        assertEquals(profile.exploit.size, payloads.exploit.length())
        assertEquals(profile.kernelSu.size, payloads.kernelSu.length())
        assertTrue(payloads.exploit.canRead())
        assertTrue(payloads.kernelSu.canRead())
    }

    /**
     * The build is expected to carry a bundled profile, so an absent one is a
     * hard failure rather than a skip that would hide a broken APK.
     */
    private fun bundledProfile(): TargetProfile =
        PayloadRepository(context).loadBuiltinTargets().firstOrNull()
            ?: throw AssertionError("no bundled profile in this build")
}
