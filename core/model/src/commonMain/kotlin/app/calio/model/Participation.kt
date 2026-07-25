package app.calio.model

enum class AttendeeRole { ORGANIZER, REQUIRED, OPTIONAL }

enum class ResponseStatus { NEEDS_ACTION, ACCEPTED, DECLINED, TENTATIVE }

data class Attendee(
    val id: AttendeeId,
    val name: String,
    val email: String? = null,
    val role: AttendeeRole = AttendeeRole.REQUIRED,
    val response: ResponseStatus = ResponseStatus.NEEDS_ACTION,
) {
    init {
        require(name.isNotBlank() || !email.isNullOrBlank()) {
            "an attendee needs at least a name or an email address"
        }
    }
}

/**
 * Metadata of a file attached to an event.
 *
 * Only the metadata is synchronised; the bytes stay on the device and are referenced by [checksum],
 * so a large attachment can never hold up a synchronisation round. [localPath] is null while the
 * content has not been fetched on this device.
 */
data class Attachment(
    val id: AttachmentId,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val checksum: String,
    val localPath: String? = null,
) {
    init {
        require(fileName.isNotBlank()) { "attachment file name must not be blank" }
        require(sizeBytes >= 0) { "attachment size must not be negative" }
        require(checksum.isNotBlank()) { "attachment checksum must not be blank" }
    }

    val isAvailableLocally: Boolean get() = localPath != null
}
