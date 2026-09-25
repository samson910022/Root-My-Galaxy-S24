package dev.samson.s24root

import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.strings(): Set<String> = buildSet {
    for (index in 0 until length()) add(getString(index))
}

/**
 * Where an artifact's bytes come from. The online support feed pins them to a
 * commit on a public repository; profiles this app bundles pin them to an APK
 * asset and a digest, because their upstream pull request is not merged and
 * the bytes are therefore unreachable over the network.
 */
sealed interface PayloadArtifact {
    val size: Long
}

data class RemoteArtifact(
    val url: String,
    override val size: Long,
) : PayloadArtifact

data class BundledArtifact(
    /** Path relative to the APK's `assets/` root, never prefixed with `assets/`. */
    val assetPath: String,
    val sha256: String,
    override val size: Long,
) : PayloadArtifact {
    init {
        // The parser already checks these, but the type is public and can be
        // built directly, so the invariants belong here too.
        require(assetPath.isNotBlank()) { "Bundled asset path must not be blank" }
        require(!assetPath.startsWith("/")) { "Bundled asset path must be relative" }
        require(!assetPath.startsWith("assets/")) { "Bundled asset path must be relative to assets/" }
        require(assetPath.split('/').none { it == ".." }) { "Bundled asset path must not traverse" }
        require(sha256.matches(SHA256_PATTERN)) { "Bundled artifact needs a 64-character hex digest" }
        require(size > 0) { "Bundled artifact size must be positive" }
    }

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

data class TargetProfile(
    val profileId: String,
    val displayName: String,
    val models: Set<String>,
    val kernelVersions: Set<String>,
    val exploit: PayloadArtifact,
    val kernelSu: PayloadArtifact,
    /**
     * Exact `uname -r` strings this payload is validated against. Empty means
     * any release whose numeric version is listed, which is all the online feed
     * can express. A non-empty set is a hard gate: the kernel offsets and the
     * P0 fingerprint table are derived from one specific firmware image, so a
     * different release must not silently reuse them.
     */
    val kernelReleases: Set<String> = emptySet(),
) {
    init {
        require(models.isNotEmpty()) { "Payload must support at least one model" }
        require(kernelVersions.isNotEmpty()) { "Payload must support at least one kernel version" }
        require((exploit is BundledArtifact) == (kernelSu is BundledArtifact)) {
            "Payload must source both artifacts from the same place"
        }
        if (bundled) {
            // Firmware-specific offsets and a per-firmware P0 fingerprint table:
            // without an exact release list a bundled payload would silently
            // apply one build's values to another. Enforced here, not only in
            // the parser, so the property survives copying and construction.
            require(kernelReleases.isNotEmpty() && kernelReleases.none { it.isBlank() }) {
                "A bundled payload must pin at least one exact kernel release"
            }
        }
    }

    val bundled: Boolean
        get() = exploit is BundledArtifact

    /**
     * Whether this payload clears the exact-firmware gate for [snapshot].
     *
     * Only bundled payloads have such a gate, because their offsets and P0
     * fingerprint come from one firmware image; a remote profile is governed by
     * model plus numeric kernel version alone, which [matches] already covers.
     * Both bundled-first resolution and acquisition are decided by this
     * predicate, so neither can drift from the other.
     */
    fun passesFirmwareGate(snapshot: DeviceSnapshot): Boolean = !bundled || matches(snapshot)

    fun matchesDevice(snapshot: DeviceSnapshot): Boolean {
        val model = snapshot.model.trim()
        return models.any { it.trim().equals(model, ignoreCase = true) }
    }

    fun matchesKernelVersion(snapshot: DeviceSnapshot): Boolean =
        snapshot.kernelVersion in kernelVersions

    fun matchesKernelRelease(snapshot: DeviceSnapshot): Boolean =
        kernelReleases.isEmpty() || snapshot.kernelRelease in kernelReleases

    fun matches(snapshot: DeviceSnapshot): Boolean =
        matchesDevice(snapshot) && matchesKernelVersion(snapshot) && matchesKernelRelease(snapshot)

    val supportedKernelReleases: String
        get() = kernelReleases.sorted().joinToString()
}

/**
 * The online support feed, schema version 3. Owned upstream and pinned to a
 * commit, so it keeps parsing exactly what it always did. Artifacts are always
 * remote here; a bundled payload is expressed in schema version 4 instead.
 */
data class SupportManifest(
    val schemaVersion: Int,
    val targets: List<TargetProfile>,
) {
    companion object {
        const val SCHEMA_VERSION = 3

        fun parse(bytes: ByteArray): SupportManifest {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val schemaVersion = root.getInt("schemaVersion")
            require(schemaVersion == SCHEMA_VERSION) { "Unsupported support manifest schema" }
            val payloadsJson = root.getJSONArray("payloads")
            val payloads = buildList {
                for (index in 0 until payloadsJson.length()) {
                    val payload = payloadsJson.getJSONObject(index)
                    val exploit = payload.getJSONObject("exploit")
                    val kernelSu = payload.getJSONObject("kernelsu")
                    add(
                        TargetProfile(
                            profileId = payload.getString("payloadId"),
                            displayName = payload.getString("displayName"),
                            models = payload.getJSONArray("models").strings(),
                            kernelVersions = payload.getJSONArray("kernelVersions").strings(),
                            exploit = RemoteArtifact(
                                url = exploit.getString("url"),
                                size = exploit.getLong("size"),
                            ),
                            kernelSu = RemoteArtifact(
                                url = kernelSu.getString("url"),
                                size = kernelSu.getLong("size"),
                            ),
                        ),
                    )
                }
            }
            return SupportManifest(schemaVersion, payloads)
        }
    }
}

/**
 * The manifest bundled in the APK, schema version 4. It is owned by this
 * repository, which is why it is versioned separately from the upstream feed
 * instead of sharing version 3's number for a shape version 3 does not
 * describe. Only the two artifact location fields differ in meaning; the
 * device-matching fields are shared with [SupportManifest].
 */
data class BuiltinSupportManifest(
    val schemaVersion: Int,
    val targets: List<TargetProfile>,
) {
    companion object {
        const val SCHEMA_VERSION = 4
        private const val SOURCE_BUILTIN = "BUILTIN"

        fun parse(bytes: ByteArray): BuiltinSupportManifest {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val schemaVersion = root.getInt("schemaVersion")
            require(schemaVersion == SCHEMA_VERSION) { "Unsupported bundled manifest schema" }
            val payloadsJson = root.getJSONArray("payloads")
            val payloads = buildList {
                for (index in 0 until payloadsJson.length()) {
                    val payload = payloadsJson.getJSONObject(index)
                    val profileId = payload.getString("payloadId")
                    require(profileId.matches(PROFILE_ID_PATTERN)) {
                        "Unsupported payload id: $profileId"
                    }
                    require(payload.getString("source") == SOURCE_BUILTIN) {
                        "Bundled manifest entry must declare source $SOURCE_BUILTIN"
                    }
                    // Bundled payloads are built from one specific kernel image,
                    // so the exact release is mandatory here. Falling back to
                    // "any 6.1.145" would silently run another build's offsets.
                    val kernelReleases = payload.getJSONArray("kernelReleases").strings()
                    require(kernelReleases.isNotEmpty() && kernelReleases.none { it.isBlank() }) {
                        "Bundled payload $profileId must pin at least one exact kernel release"
                    }
                    add(
                        TargetProfile(
                            profileId = profileId,
                            displayName = payload.getString("displayName"),
                            models = payload.getJSONArray("models").strings(),
                            kernelVersions = payload.getJSONArray("kernelVersions").strings(),
                            kernelReleases = kernelReleases,
                            exploit = payload.bundledArtifact("exploit"),
                            kernelSu = payload.bundledArtifact("kernelsu"),
                        ),
                    )
                }
            }
            return BuiltinSupportManifest(schemaVersion, payloads)
        }

        private fun JSONObject.bundledArtifact(name: String): BundledArtifact {
            val artifact = getJSONObject(name)
            // A profile that claims bundled artifacts must not also carry a
            // url, or the two would disagree about where the bytes live.
            require(!artifact.has("url")) { "Bundled artifact must not carry a url" }
            // Path, digest and size rules live on BundledArtifact itself.
            return BundledArtifact(
                assetPath = artifact.getString("assetPath"),
                sha256 = artifact.getString("sha256"),
                size = artifact.getLong("size"),
            )
        }

        /**
         * The id becomes a directory name under filesDir, so it is restricted
         * to characters that cannot escape it. The shipped id already looks
         * like this; a looser rule would only ever widen the attack surface.
         */
        private val PROFILE_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}
