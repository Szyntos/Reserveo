package org.julsz.smnt.auth

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {

    @Test
    fun `verify accepts the original password`() {
        val stored = PasswordHasher.hash("s3cret!")
        assertTrue(PasswordHasher.verify("s3cret!", stored))
    }

    @Test
    fun `verify rejects a wrong password`() {
        val stored = PasswordHasher.hash("s3cret!")
        assertFalse(PasswordHasher.verify("wrong", stored))
    }

    @Test
    fun `hashing the same password twice yields different salts`() {
        val a = PasswordHasher.hash("s3cret!")
        val b = PasswordHasher.hash("s3cret!")
        assertNotEquals(a, b)
        assertTrue(PasswordHasher.verify("s3cret!", a))
        assertTrue(PasswordHasher.verify("s3cret!", b))
    }
}
