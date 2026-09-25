package dev.samson.s24root

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val S9210_RELEASE = "6.1.145-android14-11-33419968-abS9210ZHS6DZG1"
private const val S9210_DIGEST = "2dc137cc1dca72f896ee16347f3137dd0c521ecdee56e1922fb58e733e9b4059"
private const val KSUD_DIGEST = "67b49cadfbbc518ac1fca3f7e514782110e896103882e7caa5cccaa1572ee542"

private fun bundledManifestJson(
    exploit: String = """{"assetPath":"payloads/p/cve.so","sha256":"$S9210_DIGEST","size":104128}""",
    kernelSu: String = """{"assetPath":"payloads/p/ksud","sha256":"$KSUD_DIGEST","size":4895088}""",
    kernelReleases: String = """"kernelReleases":["$S9210_RELEASE"],""",
    profileId: String = "e1q-S9210ZHS6DZG1",
) = """
    {
      "schemaVersion": 4,
      "payloads": [
        {
          "payloadId": "$profileId",
          "displayName": "Galaxy S24 China",
          "source": "BUILTIN",
          "models": ["SM-S9210"],
          "kernelVersions": ["6.1.145"],
          $kernelReleases
          "exploit": $exploit,
          "kernelsu": $kernelSu
        }
      ]
    }
""".trimIndent().toByteArray()

private fun snapshot(
    model: String = "SM-S9210",
    kernelRelease: String = S9210_RELEASE,
) = DeviceSnapshot(
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

class BuiltinSupportManifestTest {
    @Test
    fun parsesBundledArtifactsAsAssets() {
        val profile = BuiltinSupportManifest.parse(bundledManifestJson()).targets.single()
        assertEquals("e1q-S9210ZHS6DZG1", profile.profileId)
        assertTrue(profile.bundled)
        val exploit = profile.exploit as BundledArtifact
        assertEquals("payloads/p/cve.so", exploit.assetPath)
        assertEquals(104128L, exploit.size)
        assertEquals(4895088L, (profile.kernelSu as BundledArtifact).size)
    }

    @Test
    fun onlineManifestParsesToRemoteArtifactsAndStaysUngated() {
        val bytes = """
            {
              "schemaVersion": 3,
              "payloads": [
                {
                  "payloadId": "e3q-S9280ZCS6DZF2",
                  "displayName": "Galaxy S24 Ultra China",
                  "models": ["SM-S9280"],
                  "kernelVersions": ["6.1.145"],
                  "exploit": {"url":"https://example.invalid/e.so","size":104128},
                  "kernelsu": {"url":"https://example.invalid/ksud","size":4879856}
                }
              ]
            }
        """.trimIndent().toByteArray()

        val profile = SupportManifest.parse(bytes).targets.single()
        assertFalse(profile.bundled)
        assertEquals("https://example.invalid/e.so", (profile.exploit as RemoteArtifact).url)
        assertEquals(4879856L, (profile.kernelSu as RemoteArtifact).size)
        // The online feed cannot express a firmware gate, so it must keep
        // matching on model plus numeric version alone.
        assertTrue(profile.kernelReleases.isEmpty())
        assertTrue(profile.matches(snapshot(model = "SM-S9280", kernelRelease = "6.1.145-android14-11-9-other")))
    }

    @Test
    fun rejectsTheOtherSchemasSchema() {
        assertTrue(runCatching { SupportManifest.parse(bundledManifestJson()) }.isFailure)
        val remote = """{"schemaVersion":3,"payloads":[]}"""
        assertTrue(runCatching { BuiltinSupportManifest.parse(remote.toByteArray()) }.isFailure)
    }

    @Test
    fun rejectsMalformedBundledArtifacts() {
        val cases = mapOf(
            "url instead of assetPath" to """{"url":"https://x/y","size":10}""",
            "assets/ prefixed path" to """{"assetPath":"assets/p/cve.so","sha256":"$S9210_DIGEST","size":1}""",
            "absolute path" to """{"assetPath":"/p/cve.so","sha256":"$S9210_DIGEST","size":1}""",
            "traversal" to """{"assetPath":"p/../../cve.so","sha256":"$S9210_DIGEST","size":1}""",
            "short digest" to """{"assetPath":"p/cve.so","sha256":"2dc137cc","size":1}""",
            "uppercase digest" to """{"assetPath":"p/cve.so","sha256":"${S9210_DIGEST.uppercase()}","size":1}""",
            "zero size" to """{"assetPath":"p/cve.so","sha256":"$S9210_DIGEST","size":0}""",
            "negative size" to """{"assetPath":"p/cve.so","sha256":"$S9210_DIGEST","size":-1}""",
            "blank path" to """{"assetPath":"  ","sha256":"$S9210_DIGEST","size":1}""",
        )
        for ((label, artifact) in cases) {
            val bytes = bundledManifestJson(exploit = artifact)
            assertTrue("expected rejection: $label", runCatching { BuiltinSupportManifest.parse(bytes) }.isFailure)
        }
    }

    @Test
    fun requiresTheBundledSourceMarker() {
        val bytes = bundledManifestJson().toString(Charsets.UTF_8)
            .replace("\"source\": \"BUILTIN\"", "\"source\": \"REMOTE\"")
            .toByteArray()
        assertTrue(runCatching { BuiltinSupportManifest.parse(bytes) }.isFailure)
    }

    @Test
    fun passesFirmwareGateIsWhatProtectsBundledPayloads() {
        val bundled = BuiltinSupportManifest.parse(bundledManifestJson()).targets.single()
        val remote = SupportManifest.parse(
            """
            {"schemaVersion":3,"payloads":[{
              "payloadId":"e3q-S9280ZCS6DZF2","displayName":"S24 Ultra China",
              "models":["SM-S9280"],"kernelVersions":["6.1.145"],
              "exploit":{"url":"https://example.invalid/e","size":1},
              "kernelsu":{"url":"https://example.invalid/k","size":1}}]}
            """.trimIndent().toByteArray(),
        ).targets.single()

        // Same numeric kernel, different firmware: this is the case the gate
        // exists for, and it must be refused rather than probed.
        val otherFirmware = snapshot(kernelRelease = "6.1.145-android14-11-99999-abS9280ZCS6DZF2")
        assertFalse(bundled.matches(otherFirmware))
        assertFalse(
            "the gate must refuse a bundled payload on another build",
            bundled.passesFirmwareGate(otherFirmware),
        )
        assertTrue(bundled.passesFirmwareGate(snapshot()))

        // A remote profile has no exact-release gate, so this predicate is not
        // the thing that screens it: [matches] is, on model plus numeric
        // version alone.
        val s9280 = snapshot(model = "SM-S9280", kernelRelease = "6.1.145-android14-11-99999-abS9280ZCS6DZF2")
        assertTrue(remote.matches(s9280))
        assertFalse(remote.matches(snapshot()))
        assertTrue("remote profiles are not firmware-gated", remote.passesFirmwareGate(snapshot()))
    }

    @Test
    fun aBundledProfileCannotBeConstructedWithoutItsReleaseGate() {
        // The invariant has to survive copying, not just parsing.
        val bundled = BuiltinSupportManifest.parse(bundledManifestJson()).targets.single()
        assertTrue(
            runCatching { bundled.copy(kernelReleases = emptySet()) }.isFailure,
        )
        assertTrue(
            runCatching { bundled.copy(kernelReleases = setOf("  ")) }.isFailure,
        )
        // A remote profile is unaffected.
        val remote = TargetProfile(
            profileId = "r",
            displayName = "R",
            models = setOf("SM-S9280"),
            kernelVersions = setOf("6.1.145"),
            exploit = RemoteArtifact("https://example.invalid/e", 1),
            kernelSu = RemoteArtifact("https://example.invalid/k", 1),
        )
        assertTrue(remote.passesFirmwareGate(snapshot(model = "SM-S9280", kernelRelease = "6.1.145-x")))
    }

    @Test
    fun requiresAnExactKernelRelease() {
        // Omitting the gate must fail closed, not widen the profile to every
        // 6.1.145 build: the offsets come from one firmware image.
        assertTrue(
            runCatching { BuiltinSupportManifest.parse(bundledManifestJson(kernelReleases = "")) }.isFailure,
        )
        assertTrue(
            runCatching {
                BuiltinSupportManifest.parse(bundledManifestJson(kernelReleases = """"kernelReleases":[],"""))
            }.isFailure,
        )
        assertTrue(
            runCatching {
                BuiltinSupportManifest.parse(bundledManifestJson(kernelReleases = """"kernelReleases":["  "],"""))
            }.isFailure,
        )
    }

    @Test
    fun rejectsProfileIdThatCouldEscapeThePayloadDirectory() {
        for (id in listOf("../escape", "a/b", "..", ".hidden/x", "with space", "x".repeat(65))) {
            val bytes = bundledManifestJson(profileId = id)
            assertTrue("expected rejection: $id", runCatching { BuiltinSupportManifest.parse(bytes) }.isFailure)
        }
    }

    @Test
    fun rejectsProfileThatMixesSources() {
        assertTrue(
            runCatching {
                TargetProfile(
                    profileId = "mixed",
                    displayName = "Mixed",
                    models = setOf("SM-S9210"),
                    kernelVersions = setOf("6.1.145"),
                    exploit = RemoteArtifact("https://example.invalid/e", 1),
                    kernelSu = BundledArtifact("p/k", "a".repeat(64), 1),
                )
            }.isFailure,
        )
    }

    @Test
    fun requiresAtLeastOneModelAndOneKernelVersion() {
        fun build(models: Set<String>, kernelVersions: Set<String>) = runCatching {
            TargetProfile(
                profileId = "p",
                displayName = "P",
                models = models,
                kernelVersions = kernelVersions,
                exploit = RemoteArtifact("https://example.invalid/e", 1),
                kernelSu = RemoteArtifact("https://example.invalid/k", 1),
            )
        }
        assertTrue(build(emptySet(), setOf("6.1.145")).isFailure)
        assertTrue(build(setOf("SM-S9210"), emptySet()).isFailure)
        assertTrue(build(setOf("SM-S9210"), setOf("6.1.145")).isSuccess)
    }

    @Test
    fun exactReleaseGateRejectsOtherFirmwareOnTheSameNumericVersion() {
        val s9210 = BuiltinSupportManifest.parse(bundledManifestJson()).targets.single()
        val s9280 = SupportManifest.parse(
            """
            {"schemaVersion":3,"payloads":[{
              "payloadId":"e3q-S9280ZCS6DZF2","displayName":"S24 Ultra China",
              "models":["SM-S9280"],"kernelVersions":["6.1.145"],
              "exploit":{"url":"https://example.invalid/e","size":1},
              "kernelsu":{"url":"https://example.invalid/k","size":1}}]}
            """.trimIndent().toByteArray(),
        ).targets.single()

        val chinaS24 = snapshot()
        val chinaUltra = snapshot(model = "SM-S9280", kernelRelease = "6.1.145-android14-11-33419968-abS9280ZCS6DZF2")

        // Each profile claims only its own model, and the bundled one further
        // refuses the other build of the same numeric kernel version.
        assertTrue(s9210.matches(chinaS24))
        assertFalse(s9210.matches(chinaUltra))
        assertTrue(s9280.matches(chinaUltra))
        assertFalse(s9280.matches(chinaS24))
        assertFalse(s9210.matches(snapshot(kernelRelease = "6.1.146-android14-11-1")))
    }

    @Test
    fun shippedBundledManifestIsInternallyConsistent() {
        // Reads the repository's own manifest, not the APK, so this guards the
        // checked-in contract. The APK copy is verified on device.
        val shipped = locate("src/main/assets/builtin-targets.json")
        assertNotNull("could not locate the bundled manifest from ${File(".").absolutePath}", shipped)
        val manifest = BuiltinSupportManifest.parse(shipped!!.readBytes())
        val profile = manifest.targets.single()
        assertEquals("e1q-S9210ZHS6DZG1", profile.profileId)
        assertEquals(setOf("SM-S9210"), profile.models)
        assertEquals(S9210_RELEASE, profile.supportedKernelReleases)
        assertTrue(profile.bundled)
        for (artifact in listOf(profile.exploit, profile.kernelSu)) {
            val bundled = artifact as BundledArtifact
            val file = File(shipped.parentFile, bundled.assetPath)
            assertTrue("missing asset ${bundled.assetPath}", file.isFile)
            assertEquals(bundled.size, file.length())
            assertEquals(bundled.sha256, sha256OrNull(file))
        }
    }

    /** Walks up from the working directory so the test does not depend on it. */
    private fun locate(relative: String): File? {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        return null
    }
}
