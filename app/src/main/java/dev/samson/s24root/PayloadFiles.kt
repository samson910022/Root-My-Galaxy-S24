package dev.samson.s24root

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Why a payload could not be published to its destination. */
internal enum class PayloadFailure {
    SizeExceeded,
    Incomplete,
    DigestMismatch,
    FinalizeFailed,
}

/**
 * Carries the reason rather than a message so the wording stays in the
 * resources, where it can be translated, instead of in this file.
 */
internal class PayloadIntegrityException(
    val failure: PayloadFailure,
    label: String,
) : Exception("$failure while staging $label")

/**
 * Payloads are truncated to a fixed release size, so a rebuild of a target --
 * or a different target padded to the same size -- has exactly the length of
 * whatever is already staged, and would keep running in its place. Length
 * alone therefore cannot establish that a staged file is the one that was
 * meant to be there; the content digest is what does.
 */
internal fun stagedFileIsCurrent(staged: File, source: File): Boolean {
    if (!staged.exists()) return false
    val stagedDigest = sha256OrNull(staged) ?: return false
    return stagedDigest == sha256OrNull(source)
}

/** The same question, answered against the size and digest the manifest declares. */
internal fun stagedFileIsCurrent(staged: File, expectedSize: Long, expectedSha256: String): Boolean {
    if (!staged.isFile) return false
    if (staged.length() != expectedSize) return false
    return sha256OrNull(staged) == expectedSha256
}

/**
 * Writes [source] to [destination] only if the bytes are exactly the declared
 * length and hash.
 *
 * The bytes land in a sibling `.part` file that is flushed, digested, and then
 * moved into place with an atomic replace. Nothing unverified is ever visible
 * at [destination], and a destination that already held verified bytes is only
 * replaced once the replacement is known good, so a failure at any step leaves
 * the previous file intact.
 */
internal fun stageVerified(
    source: InputStream,
    destination: File,
    expectedSize: Long,
    expectedSha256: String,
    label: String,
): File {
    // createTempFile silently stages into java.io.tmpdir when the directory is
    // null, which would make the move below cross-device and quietly give up
    // the atomicity it documents. A bare relative File has no parent.
    val directory = requireNotNull(destination.parentFile) { "destination must have a parent directory" }
    // A unique scratch name, not a fixed one: a leftover file from an
    // interrupted run, or a concurrent writer, would otherwise be renamed into
    // the destination by whichever process finished last.
    val temporary = File.createTempFile("${destination.name}.", ".part", directory)
    try {
        var total = 0L
        source.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > expectedSize) throw PayloadIntegrityException(PayloadFailure.SizeExceeded, label)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        if (total != expectedSize) throw PayloadIntegrityException(PayloadFailure.Incomplete, label)
        if (sha256OrNull(temporary) != expectedSha256) {
            throw PayloadIntegrityException(PayloadFailure.DigestMismatch, label)
        }
        publish(temporary, destination, expectedSha256, label)
    } finally {
        // A failed or abandoned copy must not leave its scratch file behind to
        // be mistaken for a staged payload on the next run.
        temporary.delete()
    }
    return destination
}

private fun publish(
    temporary: File,
    destination: File,
    expectedSha256: String,
    label: String,
) {
    try {
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (error: AtomicMoveNotSupportedException) {
        // Some filesystems cannot rename atomically. Fall back to a plain
        // replace, then re-verify what actually landed, so the destination is
        // still only ever the verified bytes.
        if (!runCatching {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        }.getOrDefault(false) || sha256OrNull(destination) != expectedSha256) {
            throw PayloadIntegrityException(PayloadFailure.FinalizeFailed, label).also { it.initCause(error) }
        }
    } catch (error: Throwable) {
        throw PayloadIntegrityException(PayloadFailure.FinalizeFailed, label).also { it.initCause(error) }
    }
}

internal fun sha256OrNull(file: File): String? = runCatching {
    file.inputStream().use { sha256(it) }
}.getOrNull()

internal fun sha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DIGEST_BUFFER_BYTES)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private const val DIGEST_BUFFER_BYTES = 8192
