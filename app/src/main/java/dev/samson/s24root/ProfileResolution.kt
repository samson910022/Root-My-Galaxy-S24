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
    data class WrongFirmware(val supportedRelease: String) : ResolutionFailure

    /** The device is served only by the bundled manifest, which was unusable. */
    data object ReservedModelWithoutBundledPayload : ResolutionFailure

    /** Nothing supports this model and kernel version. */
    data object NoProfile : ResolutionFailure
}

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
): Resolution {
    bundled.firstOrNull { it.passesFirmwareGate(snapshot) }?.let {
        return Resolution.Found(it)
    }
    // Same model, different firmware: say so rather than probing with offsets
    // derived from another build.
    bundled.firstOrNull { it.matchesDevice(snapshot) }?.let {
        return Resolution.Failed(ResolutionFailure.WrongFirmware(it.supportedKernelReleases))
    }
    // A reserved model is served only by the bundled manifest, so once the
    // bundled checks above have not answered for it, stop. An empty or
    // malformed bundled catalogue must not let the online feed supply a
    // payload for the model this fork owns, because only the bundled path
    // carries the digest check and the exact-firmware gate.
    if (isReservedModel(snapshot.model)) {
        return Resolution.Failed(ResolutionFailure.ReservedModelWithoutBundledPayload)
    }
    remote().firstOrNull { it.matches(snapshot) }?.let {
        // Remote profiles carry no exact-release gate, so a matching entry is
        // trusted as published.
        return Resolution.Found(it)
    }
    return Resolution.Failed(ResolutionFailure.NoProfile)
}
