package dev.samson.s24root

/**
 * Which model a bundled manifest is allowed to claim. The payloads feed must
 * never supply a payload for one of these, so resolution can fail closed when
 * the bundled manifest is unavailable instead of trusting an online entry.
 */
internal val RESERVED_MODELS = setOf("SM-S9210")

internal fun isReservedModel(model: String): Boolean =
    RESERVED_MODELS.any { it.equals(model.trim(), ignoreCase = true) }

/** Why [resolveProfile] could not pick a payload, or null when it picked one. */
internal sealed interface ResolutionFailure {
    /** The device matches a bundled profile's model but not its pinned firmware. */
    data class WrongFirmware(val supportedReleases: String) : ResolutionFailure

    /**
     * The device matches a candidate profile's exact firmware, but candidate
     * profiles are opt-in. Returned instead of [WrongFirmware] so an EZIF
     * device without opt-in gets the opt-in message, not a firmware mismatch.
     */
    data class CandidateRequiresOptIn(val profileId: String) : ResolutionFailure

    /** The device is served only by the bundled manifest, which was unusable. */
    data object ReservedModelWithoutBundledPayload : ResolutionFailure

    /** Nothing supports this model and kernel version. */
    data object NoProfile : ResolutionFailure
}

/**
 * Bundled profiles that are not yet accepted for release. They ship in the
 * APK manifest but never resolve unless the user opts in via Settings, so a
 * matching device without opt-in gets [ResolutionFailure.CandidateRequiresOptIn]
 * instead of silently running an unaccepted payload.
 */
internal val CANDIDATE_PROFILE_IDS = setOf("e1q-S9210ZHU6EZIF")

internal fun isCandidateProfile(profile: TargetProfile): Boolean =
    profile.profileId in CANDIDATE_PROFILE_IDS

/** The outcome of [resolveProfile]. */
internal sealed interface Resolution {
    data class Found(val profile: TargetProfile) : Resolution
    data class Failed(val failure: ResolutionFailure) : Resolution
}

/**
 * The whole resolution decision, as a pure function of the catalogues.
 *
 * Bundled profiles are consulted first and the remote catalogue is loaded
 * lazily, so a device this fork serves from its own manifest resolves with no
 * network access at all. The remote loader is a lambda rather than a value for
 * that reason: a caller cannot fetch it before the bundled check has had its
 * chance.
 */
internal fun resolveProfile(
    snapshot: DeviceSnapshot,
    bundled: List<TargetProfile>,
    remote: () -> List<TargetProfile>,
    allowCandidates: Boolean = false,
): Resolution {
    val (candidates, stable) = bundled.partition(::isCandidateProfile)
    stable.firstOrNull { it.passesFirmwareGate(snapshot) }?.let {
        return Resolution.Found(it)
    }
    if (allowCandidates) {
        candidates.firstOrNull { it.passesFirmwareGate(snapshot) }?.let {
            return Resolution.Found(it)
        }
    } else {
        // A candidate that would have matched must surface the opt-in message,
        // not a misleading firmware mismatch. This check comes before
        // WrongFirmware on purpose: an EZIF device without opt-in matches no
        // stable profile, and the stable-only mismatch below would otherwise
        // blame DZG1 firmware.
        candidates.firstOrNull { it.passesFirmwareGate(snapshot) }?.let {
            return Resolution.Failed(ResolutionFailure.CandidateRequiresOptIn(it.profileId))
        }
    }
    // Same model, different firmware: say so rather than probing with offsets
    // derived from another build. Without opt-in only stable profiles count
    // here, so a gated-off candidate never supplies the mismatch text; with
    // opt-in the full bundled list counts. All same-model releases are
    // listed (sorted, comma-joined), so an unknown SM-S9210 build sees both
    // DZG1 and EZIF instead of only the first match.
    val relevant = if (allowCandidates) bundled else stable
    val sameModel = relevant.filter { it.matchesDevice(snapshot) }
    if (sameModel.isNotEmpty()) {
        val releases = sameModel.flatMap { it.kernelReleases }.distinct().sorted().joinToString()
        return Resolution.Failed(ResolutionFailure.WrongFirmware(releases))
    }
    // A reserved model is served only by the bundled manifest, so once the
    // bundled checks above have not answered for it, stop. An empty or
    // malformed bundled catalogue must not let the online feed supply a
    // payload for the model this fork owns, because only the bundled path
    // carries the digest check and the exact-firmware gate.
    if (isReservedModel(snapshot.model)) {
        return Resolution.Failed(ResolutionFailure.ReservedModelWithoutBundledPayload)
    }
    remote().filter { allowCandidates || !isCandidateProfile(it) }.firstOrNull { it.matches(snapshot) }?.let {
        // Remote profiles carry no exact-release gate, so a matching entry is
        // trusted as published.
        return Resolution.Found(it)
    }
    return Resolution.Failed(ResolutionFailure.NoProfile)
}
