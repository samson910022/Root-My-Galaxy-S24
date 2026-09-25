package dev.samson.s24root

import java.io.File
import java.nio.file.Files
import kotlin.io.DEFAULT_BUFFER_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadFilesTest {
    @Test
    fun sha256MatchesKnownVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256(ByteArray(0).inputStream()),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".byteInputStream()),
        )
    }

    @Test
    fun digestGateRejectsEqualLengthDifferentContent() {
        val directory = Files.createTempDirectory("payload-files").toFile()
        try {
            val staged = File(directory, "payload").apply { writeBytes(ByteArray(104128) { 0x41 }) }
            val replacement = File(directory, "replacement").apply { writeBytes(ByteArray(104128) { 0x42 }) }
            // Identical length, different bytes: length checks cannot see this.
            assertEquals(staged.length(), replacement.length())
            assertNotEquals(sha256OrNull(staged), sha256OrNull(replacement))

            val digest = sha256OrNull(staged)!!
            assertTrue(stagedFileIsCurrent(staged, 104128L, digest))
            // Same length, different bytes: the digest is what separates them.
            assertFalse(stagedFileIsCurrent(staged, 104128L, sha256OrNull(replacement)!!))
            assertFalse(stagedFileIsCurrent(staged, 104128L, "a".repeat(64)))
            // And a size that does not match is rejected even for the right bytes.
            assertFalse(stagedFileIsCurrent(staged, 104127L, digest))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun digestGateRejectsMissingFiles() {
        val directory = Files.createTempDirectory("payload-files-empty").toFile()
        try {
            assertFalse(stagedFileIsCurrent(File(directory, "absent"), 1L, "a".repeat(64)))
            val wrongLength = File(directory, "short").apply { writeBytes("payload".toByteArray()) }
            assertFalse(stagedFileIsCurrent(wrongLength, 1L, shaOf("payload")))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun digestGateDoesNotFollowADirectory() {
        val directory = Files.createTempDirectory("payload-files-dir").toFile()
        try {
            // A directory at the destination must not read as a valid staging,
            // however its length or digest would compare.
            assertTrue(directory.isDirectory)
            assertFalse(stagedFileIsCurrent(directory, 1L, "a".repeat(64)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourceFileComparisonStillWorks() {
        val directory = Files.createTempDirectory("payload-files-src").toFile()
        try {
            val staged = File(directory, "staged").apply { writeText("payload") }
            val same = File(directory, "same").apply { writeText("payload") }
            val other = File(directory, "other").apply { writeText("Payload") }
            assertTrue(stagedFileIsCurrent(staged, same))
            assertFalse(stagedFileIsCurrent(staged, other))
            assertFalse(stagedFileIsCurrent(File(directory, "absent"), same))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun stageVerifiedPublishesMatchingBytes() {
        withDirectory { directory ->
            val destination = File(directory, "cve.so")
            val digest = stageVerified("payload".byteInputStream(), destination, 7, shaOf("payload"), "exploit")
            assertEquals(destination, digest)
            assertEquals("payload", destination.readText())
            assertFalse("the .part file must not survive", File(directory, "cve.so.part").exists())
        }
    }

    @Test
    fun stageVerifiedRejectsWrongLengthAndWrongDigest() {
        withDirectory { directory ->
            val short = File(directory, "short")
            assertEquals(PayloadFailure.Incomplete, failureOf {
                stageVerified("abc".byteInputStream(), short, 4, shaOf("abc"), "exploit")
            })
            assertFalse("nothing may be published on failure", short.exists())

            val wrong = File(directory, "wrong")
            assertEquals(PayloadFailure.DigestMismatch, failureOf {
                stageVerified("abc".byteInputStream(), wrong, 3, shaOf("xyz"), "exploit")
            })
            assertFalse(wrong.exists())
        }
    }

    @Test
    fun stageVerifiedRejectsAnOverlongStreamBeforeWritingItAll() {
        withDirectory { directory ->
            val destination = File(directory, "big")
            val length = 4L * 1024 * 1024
            val source = CountingInputStream(ByteArray(length.toInt()) { 0x5A })
            assertEquals(
                PayloadFailure.SizeExceeded,
                failureOf { stageVerified(source, destination, 16, "a".repeat(64), "exploit") },
            )
            // Abandoned as soon as the declared size was passed: one buffer's
            // worth, not the whole stream. A full drain would be `length`.
            assertEquals(DEFAULT_BUFFER_SIZE.toLong(), source.count)
            assertTrue("read ${source.count} of $length bytes", source.count < length)
            assertFalse(destination.exists())
        }
    }

    @Test
    fun stageVerifiedReplacesAStaleFileOfTheSameLength() {
        withDirectory { directory ->
            val destination = File(directory, "cve.so")
            // A previous run left equal-length garbage that still hashed wrong.
            destination.writeBytes(ByteArray(7) { 0x41 })
            assertFalse(stagedFileIsCurrent(destination, 7L, shaOf("payload")))
            stageVerified("payload".byteInputStream(), destination, 7, shaOf("payload"), "exploit")
            assertEquals("payload", destination.readText())
        }
    }

    @Test
    fun stageVerifiedLeavesAnExistingGoodFileIntactWhenTheCopyFails() {
        withDirectory { directory ->
            val destination = File(directory, "cve.so")
            stageVerified("payload".byteInputStream(), destination, 7, shaOf("payload"), "exploit")
            // A later, corrupt copy must not damage the known-good file.
            assertEquals(
                PayloadFailure.DigestMismatch,
                failureOf { stageVerified("corrupt".byteInputStream(), destination, 7, shaOf("payload"), "exploit") },
            )
            assertEquals("payload", destination.readText())
        }
    }

    @Test
    fun stageVerifiedCleansUpTheScratchFileWhenVerificationFails() {
        withDirectory { directory ->
            val destination = File(directory, "cve.so")
            val scratch = File(directory, "cve.so.part")
            for (expected in listOf(PayloadFailure.Incomplete, PayloadFailure.DigestMismatch)) {
                scratch.writeText("leftover from an earlier run")
                val failure = failureOf {
                    stageVerified(
                        if (expected == PayloadFailure.Incomplete) "ab".byteInputStream() else "xyz".byteInputStream(),
                        destination,
                        3,
                        shaOf("payload"),
                        "exploit",
                    )
                }
                assertEquals(expected, failure)
                assertFalse("scratch file survived $expected", scratch.exists())
            }
            // The over-limit path aborts mid-stream, which is the case most
            // likely to leave a large scratch file behind.
            scratch.writeText("leftover from an earlier run")
            assertEquals(
                PayloadFailure.SizeExceeded,
                failureOf {
                    stageVerified(ByteArray(4096) { 0x5A }.inputStream(), destination, 8, shaOf("payload"), "exploit")
                },
            )
            assertFalse("scratch file survived SizeExceeded", scratch.exists())
        }
    }

    /** Counts the bytes the consumer actually pulled, one read at a time. */
    private class CountingInputStream(private val delegate: ByteArray) : java.io.InputStream() {
        var count = 0L
            private set
        private var position = 0

        override fun read(): Int {
            if (position >= delegate.size) return -1
            position++
            count++
            return delegate[position - 1].toInt()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val n = minOf(length, delegate.size - position)
            if (n <= 0) return -1
            delegate.copyInto(buffer, offset, position, position + n)
            position += n
            count += n
            return n
        }
    }

    private fun shaOf(value: String): String = sha256(value.byteInputStream())

    private fun failureOf(block: () -> Unit): PayloadFailure {
        val thrown = runCatching(block).exceptionOrNull()
        assertTrue("expected a PayloadIntegrityException, got $thrown", thrown is PayloadIntegrityException)
        return (thrown as PayloadIntegrityException).failure
    }

    private inline fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("payload-stage").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
