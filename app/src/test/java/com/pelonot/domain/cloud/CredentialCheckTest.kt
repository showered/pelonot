package com.pelonot.domain.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the sign-in form will and will not send (PLAN 15.1.2a).
 *
 * The rules are here rather than in the screen precisely so they can be asked
 * questions like this one, and the questions worth asking are all about a
 * touchscreen keyboard at arm's length rather than about email syntax.
 */
class CredentialCheckTest {

    @Test
    fun `an ordinary address passes`() {
        assertNull(CredentialCheck.emailProblem("simon@example.com"))
        assertNull(CredentialCheck.emailProblem("  simon@example.com  "))
    }

    @Test
    fun `the typos a rider can see are caught`() {
        assertNotNull(CredentialCheck.emailProblem(""))
        assertNotNull(CredentialCheck.emailProblem("simon"))
        assertNotNull(CredentialCheck.emailProblem("simon@"))
        assertNotNull(CredentialCheck.emailProblem("@example.com"))
        assertNotNull(CredentialCheck.emailProblem("simon@@example.com"))
        assertNotNull(CredentialCheck.emailProblem("simon@example"))
        assertNotNull(CredentialCheck.emailProblem("simon@example."))
        assertNotNull(CredentialCheck.emailProblem("simon @example.com"))
    }

    /**
     * The server is the authority on what an address is, and the confirmation
     * email is the real check. A regex that rejects addresses the server would
     * have accepted is a rider who cannot sign in and cannot find out why.
     */
    @Test
    fun `unusual but legal addresses are not rejected`() {
        assertNull(CredentialCheck.emailProblem("s+bike@example.co.uk"))
        assertNull(CredentialCheck.emailProblem("simon.h@sub.domain.example"))
        assertNull(CredentialCheck.emailProblem("SIMON@EXAMPLE.COM"))
    }

    @Test
    fun `the address is sent lower-cased and trimmed`() {
        assertEquals(
            "simon@example.com",
            CredentialCheck.normaliseEmail("  Simon@Example.COM ")
        )
    }

    @Test
    fun `a password shorter than the server's own floor is refused here first`() {
        assertNotNull(CredentialCheck.passwordProblem("short"))
        assertNull(CredentialCheck.passwordProblem("longenough"))
    }

    /**
     * The asymmetry is deliberate: signing in wrong costs a retry, signing *up*
     * wrong creates an account with a password nobody knows, attached to an
     * email address that has now been used.
     */
    @Test
    fun `signing up asks twice and signing in does not`() {
        assertNull(CredentialCheck.passwordProblem("correct horse"))
        assertNotNull(CredentialCheck.passwordProblem("correct horse", ""))
        assertNotNull(CredentialCheck.passwordProblem("correct horse", "correct hors"))
        assertNull(CredentialCheck.passwordProblem("correct horse", "correct horse"))
    }

    /**
     * A trailing space is invisible on a bike and permanent in a password
     * manager. Trimming it would silently change the password, so it is
     * reported instead.
     */
    @Test
    fun `a password with an edge space is reported rather than trimmed`() {
        assertNotNull(CredentialCheck.passwordProblem("trailing "))
        assertNotNull(CredentialCheck.passwordProblem(" leading"))
        assertNull(CredentialCheck.passwordProblem("in ternal"))
    }

    @Test
    fun `canSubmit is the two checks and nothing else`() {
        assertTrue(CredentialCheck.canSubmit("simon@example.com", "sixchars"))
        assertTrue(CredentialCheck.canSubmit("simon@example.com", "sixchars", "sixchars"))
        assertFalse(CredentialCheck.canSubmit("simon@example.com", "sixchars", "sixchar"))
        assertFalse(CredentialCheck.canSubmit("nope", "sixchars"))
        assertFalse(CredentialCheck.canSubmit("simon@example.com", "five!"))
    }
}
