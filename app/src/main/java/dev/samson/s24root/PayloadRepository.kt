package dev.samson.s24root

import android.content.Context
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONException
import org.json.JSONObject

data class VerifiedPayloads(
    val profile: TargetProfile,
    val exploit: File,
    val kernelSu: File,
)

class PayloadRepository(
    private val context: Context,
    /**
     * Indirection over [DeviceSnapshot.current] so the exact-firmware gate can
     * be exercised against a snapshot the test chooses, rather than only
     * against whichever phone the test happens to run on.
     */
    private val deviceSnapshot: () -> DeviceSnapshot = { DeviceSnapshot.current() },
) {
    /**
     * The bundled catalogue. Read from the APK, so it costs no network and is
     * available with the radio off. A missing or malformed asset degrades to
     * an empty list rather than throwing, so the online feed stays usable.
     * Cancellation and other fatal errors are deliberately not swallowed.
     */
    fun loadBuiltinTargets(): List<TargetProfile> = try {
        BuiltinSupportManifest.parse(readAsset(BUILTIN_MANIFEST, MAX_BUILTIN_MANIFEST_BYTES)).targets
    } catch (error: IOException) {
        emptyList()
    } catch (error: JSONException) {
        emptyList()
    } catch (error: IllegalArgumentException) {
        emptyList()
    }

    fun loadTargets(): List<TargetProfile> {
        val commit = resolveMainCommit()
        val manifestBytes = downloadBytes(rawUrl(commit, "support/targets-v3.json"), MAX_MANIFEST_BYTES)
        val remote = SupportManifest.parse(manifestBytes).targets.map { profile -> profile.copy(
            exploit = pinRemote(profile.exploit, commit),
            kernelSu = pinRemote(profile.kernelSu, commit),
        ) }
        // A bundled profile is authoritative if the online feed ever grows an
        // entry with the same id, so let it win the de-duplication.
        return (loadBuiltinTargets() + remote).distinctBy(TargetProfile::profileId)
    }

    fun resolveTarget(snapshot: DeviceSnapshot): TargetProfile {
        // Checked before any HTTP call: the bundled profile is the one device
        // this app targets, and it must resolve with no connectivity at all.
        val bundled = loadBuiltinTargets()
        bundled.firstOrNull { it.passesFirmwareGate(snapshot) }?.let { return it }
        val wrongFirmware = bundled.firstOrNull { it.matchesDevice(snapshot) }
        if (wrongFirmware != null) {
            error(
                context.getString(
                    R.string.repo_firmware_unsupported,
                    wrongFirmware.supportedKernelReleases,
                    snapshot.kernelRelease,
                ),
            )
        }
        return loadTargets()
            .firstOrNull { it.matches(snapshot) }
            ?: error(context.getString(R.string.repo_no_profile))
    }

    fun resolveTarget(profileId: String): TargetProfile {
        val bundled = loadBuiltinTargets().firstOrNull { it.profileId == profileId }
        if (bundled != null) {
            // A bundled profile is only ever run on the exact firmware it was
            // derived from, whoever asked for it. Advanced mode can name a
            // profile directly, and that must not become a way around the gate.
            requireCompatible(bundled)
            return bundled
        }
        return loadTargets().firstOrNull { it.profileId == profileId }
            ?: error(context.getString(R.string.repo_profile_missing, profileId))
    }

    fun acquire(profile: TargetProfile, onProgress: (String) -> Unit): VerifiedPayloads {
        // Last gate before anything is written or run. Automatic resolution has
        // already checked this, but acquire is also reachable with a profile the
        // caller picked, and a bundled payload on the wrong firmware build is
        // exactly the mistake that damages hardware.
        requireCompatible(profile)
        val directory = payloadDirectory(profile.profileId)
        val exploit = acquireArtifact(
            profile.exploit,
            File(directory, EXPLOIT_FILE_NAME),
            context.getString(R.string.artifact_exploit),
            onProgress,
        )
        val kernelSu = acquireArtifact(
            profile.kernelSu,
            File(directory, KERNELSU_FILE_NAME),
            context.getString(R.string.artifact_kernelsu),
            onProgress,
        )
        Os.chmod(exploit.absolutePath, PAYLOAD_MODE)
        Os.chmod(kernelSu.absolutePath, PAYLOAD_MODE)
        return VerifiedPayloads(profile, exploit, kernelSu)
    }

    private fun requireCompatible(profile: TargetProfile) {
        val snapshot = deviceSnapshot()
        if (profile.passesFirmwareGate(snapshot)) return
        error(
            context.getString(
                R.string.repo_firmware_unsupported,
                profile.supportedKernelReleases,
                snapshot.kernelRelease,
            ),
        )
    }

    private fun payloadDirectory(profileId: String): File {
        val root = File(context.filesDir, PAYLOAD_ROOT).canonicalFile
        val directory = File(root, profileId).canonicalFile
        require(directory.parentFile == root) { "Payload id escapes the payload directory" }
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create the payload directory" }
        return directory
    }

    private fun pinRemote(artifact: PayloadArtifact, commit: String): PayloadArtifact = when (artifact) {
        is RemoteArtifact -> artifact.copy(url = pinArtifactUrl(artifact.url, commit))
        is BundledArtifact -> artifact
    }

    private fun acquireArtifact(
        artifact: PayloadArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File = when (artifact) {
        is RemoteArtifact -> downloadArtifact(artifact, destination, label, onProgress)
        is BundledArtifact -> stageAsset(artifact, destination, label, onProgress)
    }

    /**
     * Copies a bundled artifact out of the APK and refuses to publish it
     * unless the bytes hash to the digest the bundled manifest declares. The
     * APK is the trust anchor here, so the manifest travels with the bytes and
     * there is no network round trip to verify against.
     */
    private fun stageAsset(
        artifact: BundledArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File {
        if (stagedFileIsCurrent(destination, artifact.size, artifact.sha256)) {
            onProgress(context.getString(R.string.repo_staged_current, label))
            return destination
        }
        onProgress(context.getString(R.string.repo_staging, label))
        return try {
            context.assets.open(artifact.assetPath).use { source ->
                stageVerified(source, destination, artifact.size, artifact.sha256, label)
            }.also { onProgress(context.getString(R.string.repo_verified, label)) }
        } catch (failure: PayloadIntegrityException) {
            error(context.getString(messageFor(failure.failure), label))
        }
    }

    private fun messageFor(failure: PayloadFailure): Int = when (failure) {
        PayloadFailure.SizeExceeded -> R.string.repo_size_exceeded
        PayloadFailure.Incomplete -> R.string.repo_incomplete
        PayloadFailure.DigestMismatch -> R.string.repo_digest_mismatch
        PayloadFailure.FinalizeFailed -> R.string.repo_finalize_failed
    }

    private fun readAsset(path: String, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        context.assets.open(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) {
                    context.getString(R.string.repo_asset_too_large)
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun downloadArtifact(
        artifact: RemoteArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File {
        onProgress(context.getString(R.string.repo_downloading, label))
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val connection = open(artifact.url)
        require(connection.contentLengthLong == -1L || connection.contentLengthLong == artifact.size) {
            context.getString(R.string.repo_size_mismatch, label)
        }
        var total = 0L
        connection.inputStream.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= artifact.size) {
                        context.getString(R.string.repo_size_exceeded, label)
                    }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        connection.disconnect()
        require(total == artifact.size) { context.getString(R.string.repo_incomplete, label) }
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) {
            context.getString(R.string.repo_finalize_failed, label)
        }
        onProgress(context.getString(R.string.repo_verified, label))
        return destination
    }

    private fun resolveMainCommit(): String {
        val response = downloadBytes(COMMIT_API_URL, MAX_COMMIT_RESPONSE_BYTES)
        val commit = JSONObject(response.toString(Charsets.UTF_8))
            .getJSONObject("object")
            .getString("sha")
        require(commit.matches(Regex("[0-9a-f]{40}"))) { context.getString(R.string.repo_commit_invalid) }
        return commit
    }

    private fun rawUrl(commit: String, path: String) = "$RAW_REPOSITORY/$commit/$path"

    private fun pinArtifactUrl(url: String, commit: String): String {
        require(url.startsWith(MUTABLE_RAW_PREFIX)) { context.getString(R.string.repo_url_invalid) }
        return "$RAW_REPOSITORY/$commit/${url.removePrefix(MUTABLE_RAW_PREFIX)}"
    }

    private fun downloadBytes(url: String, maximum: Int): ByteArray {
        val connection = open(url)
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) {
                    context.getString(R.string.repo_response_too_large)
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        connection.disconnect()
        return bytes
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RootMyGalaxyS24/${BuildConfig.VERSION_NAME}")
            connect()
            require(responseCode == HttpURLConnection.HTTP_OK) { "HTTP $responseCode" }
        }

    companion object {
        private const val COMMIT_API_URL =
            "https://api.github.com/repos/BuSung-dev/Root-My-Galaxy-Payloads/git/ref/heads/main"
        private const val RAW_REPOSITORY =
            "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads"
        private const val MUTABLE_RAW_PREFIX = "$RAW_REPOSITORY/main/"
        private const val MAX_COMMIT_RESPONSE_BYTES = 16 * 1024
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val MAX_BUILTIN_MANIFEST_BYTES = 256 * 1024
        private const val PAYLOAD_ROOT = "payloads"
        private const val BUILTIN_MANIFEST = "builtin-targets.json"
        private const val EXPLOIT_FILE_NAME = "cve-2026-43499-app.so"
        private const val KERNELSU_FILE_NAME = "ksud-kdp"

        /**
         * Read-only to the app. The exploit is dlopen'd rather than executed,
         * and the loader is always staged to /data/local/tmp with mode 755
         * before anything runs it, so neither needs the exec bit here.
         */
        private const val PAYLOAD_MODE = 0b100100100
    }
}
