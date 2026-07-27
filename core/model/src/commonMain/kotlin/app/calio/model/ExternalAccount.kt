package app.calio.model

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/** A calendar service Calio can connect to. */
enum class CalendarProvider { GOOGLE, MICROSOFT }

@JvmInline
value class ExternalAccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExternalAccountId must not be blank" }
    }
}

/**
 * Whether the connection still works.
 *
 * A refresh token can be revoked from the provider's side at any time — a password change, a
 * withdrawn consent — and the application finds out only when it next tries. Recording that as a
 * state rather than treating it as an error lets the interface ask for a new sign-in instead of
 * reporting a failure the user cannot interpret.
 */
enum class AccountStatus { CONNECTED, NEEDS_REAUTHENTICATION }

/**
 * A signed-in calendar account.
 *
 * The tokens are deliberately absent. They live in the platform's secure storage keyed by [id],
 * because a database row is copied into backups and exports, and a refresh token is a key to
 * somebody's calendar.
 */
data class ExternalAccount(
    val id: ExternalAccountId,
    val provider: CalendarProvider,
    /** Usually the email address, which is what the user recognises the account by. */
    val displayName: String,
    val connectedAt: Instant,
    val status: AccountStatus = AccountStatus.CONNECTED,
) {
    init {
        require(displayName.isNotBlank()) { "an account needs a name to be recognised by" }
    }

    val needsAttention: Boolean get() = status == AccountStatus.NEEDS_REAUTHENTICATION
}

/**
 * Where a calendar comes from.
 *
 * A sealed type rather than a nullable account id, so that every place which has to behave
 * differently for a mirrored calendar is forced to say which case it is handling. The difference is
 * not cosmetic: an external calendar answers to its provider, may be read-only, and is mirrored by
 * each device separately rather than travelling through Calio's own synchronisation.
 */
sealed interface CalendarOrigin {

    data object Local : CalendarOrigin

    data class External(
        val accountId: ExternalAccountId,
        val provider: CalendarProvider,
        /** The identifier the provider knows this calendar by. */
        val externalId: String,
        /** Shared and subscribed calendars often cannot be written to. */
        val isReadOnly: Boolean = false,
    ) : CalendarOrigin {
        init {
            require(externalId.isNotBlank()) { "an external calendar needs its provider's id" }
        }
    }
}

/**
 * A calendar offered by a connected account, whether or not it is mirrored yet.
 *
 * The account's whole catalogue is kept so the user can choose from it. Only the chosen ones become
 * calendars in Calio; the rest stay a list to pick from and cost nothing but a row.
 */
data class ExternalCalendarInfo(
    val accountId: ExternalAccountId,
    val externalId: String,
    val name: String,
    val color: CalioColor?,
    val isReadOnly: Boolean = false,
    val isMirrored: Boolean = false,
    /** The local calendar this one is mirrored into, once the user has chosen it. */
    val calendarId: CalendarId? = null,
)
