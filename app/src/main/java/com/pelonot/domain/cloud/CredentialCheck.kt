package com.pelonot.domain.cloud

/**
 * Whether what the rider has typed is worth sending (PLAN 15.1.2a).
 *
 * Pure, and here rather than in the screen for the usual reason: the rules are
 * the part worth testing at every boundary, and a `@Composable` cannot be asked
 * what it would say about a six-character password with a trailing space.
 *
 * **The bike is why this is stricter than it looks.** Every field on this form
 * is typed on a touchscreen at arm's length with no password manager, and a
 * mistyped password on a sign-*up* is not recoverable by trying again — it
 * creates an account whose password nobody knows, attached to an email that has
 * already been consumed. That is what the repeated field is for, and it is why
 * a leading or trailing space is worth catching rather than silently trimming:
 * trimming a password changes it, and a rider whose password manager holds the
 * untrimmed one can then never sign in from their phone.
 */
object CredentialCheck {

    /**
     * Supabase's own floor is 6 (`password_min_length`), and this does not
     * raise it.
     *
     * The reasoning against raising it: a rule the server does not share is a
     * rule the rider meets *twice* with two different answers — once here and
     * once on the web app, which is the same account. Strength advice belongs
     * where the account is created for real (17.13, on a device with a password
     * manager), not on a bike.
     */
    const val MIN_PASSWORD_LENGTH = 6

    /**
     * The one thing this deliberately does **not** do is decide whether an
     * address is real. `a@b` is a valid address and half the regexes in the
     * world reject it; the server is the authority and the confirmation email
     * is the actual check. This catches the typo the rider can see — no `@`, a
     * space in the middle, nothing after the dot — and lets the rest through.
     */
    fun emailProblem(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Enter your email address"
            trimmed.any { it.isWhitespace() } -> "An email address has no spaces in it"
            trimmed.count { it == '@' } != 1 -> "That does not look like an email address"
            trimmed.substringBefore('@').isEmpty() -> "That does not look like an email address"
            trimmed.substringAfter('@').length < 3 -> "That does not look like an email address"
            !trimmed.substringAfter('@').contains('.') -> "That does not look like an email address"
            trimmed.endsWith('.') -> "That does not look like an email address"
            else -> null
        }
    }

    /**
     * @param repeated the second password field, or null when signing *in* —
     *   where there is nothing to compare against and asking twice would be
     *   ceremony. The asymmetry is the point: signing in is recoverable by
     *   typing it again, signing up is not.
     */
    fun passwordProblem(password: String, repeated: String? = null): String? = when {
        password.isEmpty() -> "Enter a password"
        password.length < MIN_PASSWORD_LENGTH ->
            "Passwords need at least $MIN_PASSWORD_LENGTH characters"
        password != password.trim() ->
            "That password starts or ends with a space — easy to mistype later"
        repeated != null && repeated.isEmpty() -> "Type the password again"
        repeated != null && password != repeated -> "The two passwords do not match"
        else -> null
    }

    /** True when a form is safe to submit. Nothing more than the two above. */
    fun canSubmit(email: String, password: String, repeated: String? = null): Boolean =
        emailProblem(email) == null && passwordProblem(password, repeated) == null

    /**
     * The address as it should be sent: trimmed and lower-cased.
     *
     * Addresses are case-insensitive in every mail system anyone uses, but
     * Supabase stores what it is given — so `Simon@…` and `simon@…` are one
     * account on the server and two different strings in a sign-in form, and a
     * rider who capitalises on the bike and not on their phone gets a failure
     * with no explanation available to them.
     */
    fun normaliseEmail(email: String): String = email.trim().lowercase()
}
