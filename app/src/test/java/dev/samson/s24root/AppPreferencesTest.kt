package dev.samson.s24root

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun fromStoredValueFallsBackToKernelSuForUnknownOrMissingValues() {
        assertEquals(CandidateFlavour.KernelSu, CandidateFlavour.fromStoredValue(null))
        assertEquals(CandidateFlavour.KernelSu, CandidateFlavour.fromStoredValue(""))
        assertEquals(CandidateFlavour.KernelSu, CandidateFlavour.fromStoredValue("bogus"))
        assertEquals(CandidateFlavour.KernelSu, CandidateFlavour.fromStoredValue("KERNELSU"))
    }

    @Test
    fun storedValuesCoverExactlyTheKnownCandidateFlavours() {
        // The manifest parser, TargetProfile and resolveProfile all validate
        // against KNOWN_CANDIDATE_FLAVOURS; the enum must stay in lockstep.
        assertEquals(
            TargetProfile.KNOWN_CANDIDATE_FLAVOURS,
            CandidateFlavour.entries.map { it.storedValue }.toSet(),
        )
        assertEquals("kernelsu", CandidateFlavour.KernelSu.storedValue)
        assertEquals("kernelsu-next", CandidateFlavour.KernelSuNext.storedValue)
    }
}
