package jrm.fullserver.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.mindrot.jbcrypt.BCrypt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link CryptCredential} static methods.
 *
 * <p>
 * Per the test plan (Option A), only pre-hashed idempotent vectors are used — no real Argon2 hashing is performed to keep the
 * suite fast. The BCrypt hash below is a precomputed hash for the plaintext {@code "correct"}.
 * </p>
 */
@DisplayName("CryptCredential static check/hash")
class CryptCredentialTest {

    /** Precomputed BCrypt hash for the plaintext "password". */
    private static final String BCRYPT_CORRECT = BCrypt.hashpw("password", BCrypt.gensalt());

    @Nested
    @DisplayName("hash (idempotent)")
    class HashTest {
        @ParameterizedTest
        @ValueSource(strings = { "$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ0123456",
                "$argon2id$v=19$m=65536,t=4,p=1$abc$def" })
        @DisplayName("returns already-hashed input as-is")
        void returnsAlreadyHashedAsIs(final String hashed) {
            assertThat(CryptCredential.hash(hashed)).isEqualTo(hashed);
        }

        @Test
        @DisplayName("BCrypt-prefixed input returned as-is")
        void bcryptPrefix() {
            final String hash = "$2a$10$something";
            assertThat(CryptCredential.hash(hash)).isEqualTo(hash);
        }
    }

    @Nested
    @DisplayName("check (BCrypt)")
    class CheckBCryptTest {
        @Test
        @DisplayName("correct password matches BCrypt hash")
        void correctPassword() {
            assertThat(CryptCredential.check("password", BCRYPT_CORRECT)).isTrue();
        }

        @Test
        @DisplayName("wrong password does not match BCrypt hash")
        void wrongPassword() {
            assertThat(CryptCredential.check("wrong", BCRYPT_CORRECT)).isFalse();
        }
    }

    @Nested
    @DisplayName("check (Argon2)")
    class CheckArgon2Test {
        /**
         * Real argon2-jvm 2.12 output for the plaintext "correct" (t=2, m=1024, p=1): proves hashes created by the
         * previous native implementation still verify with the pure-Java one.
         */
        private static final String ARGON2JVM_VECTOR = "$argon2id$v=19$m=1024,t=2,p=1$SU0rjdR0y37FAaPh9GQaIA$u6X5E9WxZIu4ueRGEGx0flefSho/uIav1/r0RobfzY0";

        @Test
        @DisplayName("hash produced by argon2-jvm verifies with correct password")
        void argon2JvmInterop() {
            assertThat(CryptCredential.check("correct", ARGON2JVM_VECTOR)).isTrue();
        }

        @Test
        @DisplayName("hash produced by argon2-jvm rejects wrong password")
        void argon2JvmInteropWrongPassword() {
            assertThat(CryptCredential.check("wrongpassword", ARGON2JVM_VECTOR)).isFalse();
        }

        @Test
        @DisplayName("Argon2-prefixed hash with wrong password returns false without throwing")
        void argon2WrongPassword() {
            // A syntactically valid Argon2id hash string; verification will return false for wrong password
            final String argon2Hash = "$argon2id$v=19$m=65536,t=4,p=1$c29tZXNhbHQ$RdescudXJCJt1BsdQdKZ9r1mJGZ9ZJGZ9ZJG9ZJG9ZJG";
            assertThat(CryptCredential.check("wrongpassword", argon2Hash)).isFalse();
        }

        @Test
        @DisplayName("malformed Argon2 hash returns false without throwing")
        void argon2Malformed() {
            assertThat(CryptCredential.check("password", "$argon2id$v=19$m=65536,t=4,p=1$abc$def")).isFalse();
        }

        @Test
        @DisplayName("round-trip: fresh hash verifies and rejects wrong password")
        void argon2RoundTrip() {
            final String hashed = CryptCredential.hash("s3cr3t");
            assertThat(hashed).startsWith("$argon2id$v=19$m=65536,t=40,p=4$");
            assertThat(CryptCredential.check("s3cr3t", hashed)).isTrue();
            assertThat(CryptCredential.check("wrong", hashed)).isFalse();
        }
    }
}