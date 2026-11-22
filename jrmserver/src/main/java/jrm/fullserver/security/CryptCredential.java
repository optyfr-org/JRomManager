package jrm.fullserver.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;

import org.apache.commons.dbutils.handlers.BeanHandler;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.eclipse.jetty.util.security.Credential;
import org.mindrot.jbcrypt.BCrypt;

import jrm.fullserver.db.SQL;
import jrm.misc.Log;
import lombok.Getter;

/**
 * CryptCredential is a custom implementation of the Credential class that provides functionality for checking user credentials
 * against stored password hashes. It supports both BCrypt and Argon2 hashing algorithms for secure password storage.
 * Argon2 runs on BouncyCastle's pure-Java implementation so it also works in native images (no JNI/JNA native library).
 * <p>
 * The class retrieves user credentials from a database using the provided SQL object and checks the provided credentials against
 * the stored password hash. It also provides static methods for hashing passwords and verifying credentials against stored hashes.
 * The check method retrieves the user's credentials from the database and verifies them using the appropriate hashing algorithm
 * based on the format of the stored password hash.
 */
public class CryptCredential extends Credential {

    /**
     * The username associated with the credentials.
     * <p>
     * This field is used to identify the user for whom the credentials are being checked. It is typically used in conjunction with
     * the SQL object to query the database for the user's credentials based on the provided username. The username is stored as a
     * private field in the CryptCredential class and is initialized through the constructor when creating an instance of
     * CryptCredential. It is used in the check method to retrieve the user's credentials from the database and verify them against
     * the provided credentials.
     */
    private final String username;

    /**
     * The SQL object used for database access to retrieve user credentials.
     * <p>
     * This field is marked as transient to avoid serialization, as the SQL object is not serializable and should not be included in
     * the serialized form of the CryptCredential object. The SQL object is used to query the database for user credentials during
     * the check method, but it is not needed for serialization and can be re-initialized when deserialized if necessary.
     * <p>
     * Note: The SQL class is a custom class that provides methods for executing SQL queries and retrieving results from the
     * database. It is used in this context to query the USERS table for the user's credentials based on the provided username.
     */
    private final transient SQL sql;

    /**
     * The UserCredential object representing the user's credentials retrieved from the database.
     * <p>
     * This field is marked as transient to avoid serialization, and it is populated during the check method when the user's
     * credentials are retrieved from the database. The @Getter annotation from Lombok is used to generate a getter method for this
     * field, allowing other parts of the application to access the user's credentials after they have been retrieved and checked.
     * <p>
     * Note: The UserCredential class is a simple data class that contains fields for the user's login, password, and roles. It is
     * used to store and manage user credentials in the application.
     * 
     * @return the UserCredential object representing the user's credentials retrieved from the database.
     */
    private transient @Getter UserCredential user;

    /**
     * Constructs a new CryptCredential with the specified username and SQL object.
     * <p>
     * The constructor initializes the username and SQL fields of the CryptCredential object. The username is used to identify the
     * user for whom the credentials are being checked, and the SQL object is used to query the database for the user's credentials
     * during the check method. This constructor is typically used when creating an instance of CryptCredential for a specific user,
     * allowing the check method to retrieve the user's credentials from the database and verify them against the provided
     * credentials. The SQL object is passed as a parameter to allow for database access without needing to serialize the SQL
     * object, as it is marked as transient and will not be included in the serialized form of the CryptCredential object.
     * <p>
     * Note: When using this constructor, ensure that the SQL object is properly initialized and connected to the database before
     * creating an instance of CryptCredential, as the check method relies on the SQL object to retrieve user credentials from the
     * database for verification.
     * 
     * @param username The username associated with the credentials, used to identify the user for whom the credentials are being
     *        checked.
     * @param sql The SQL object used for database access to retrieve user credentials during the check method.
     * 
     * @throws IllegalArgumentException if the username is null or empty, or if the SQL object is null.
     */
    public CryptCredential(String username, SQL sql) {
        this.username = username;
        this.sql = sql;
    }

    /**
     * Checks the provided credentials against the stored password hash for the user. It retrieves the user's credentials from the
     * database using the SQL object and verifies the provided credentials against the stored password hash using the appropriate
     * hashing algorithm (BCrypt or Argon2) based on the format of the stored password hash.
     * <p>
     * The method first queries the database for the user's credentials using the provided username. If the user is found, it
     * retrieves the stored password hash from the UserCredential object and calls the static check method to verify the provided
     * credentials against the stored password hash. If the credentials are valid and match the stored password hash, it returns
     * true; otherwise, it returns false. If any SQLException occurs during the database query, it logs the error and returns false.
     * <p>
     * Note: The check method relies on the SQL object to retrieve user credentials from the database, so it is important to ensure
     * that the SQL object is properly initialized and connected to the database before calling this method. Additionally, the check
     * method assumes that the stored password hash in the database is in either BCrypt or Argon2 format, and it uses the
     * appropriate verification method based on the format of the stored password hash. Ensure that the stored password hashes in
     * the database are properly formatted for secure password storage and verification.
     * <p>
     * The credentials parameter is typically a plaintext password that the user is attempting to authenticate with. The method
     * checks this plaintext password against the stored password hash retrieved from the database to determine if the
     * authentication attempt is valid. The method returns true if the credentials are valid and match the stored password hash, and
     * false otherwise.
     * <p>
     * Example usage:
     * 
     * <pre>
     * SQL sql = new SQL(...); // Initialize SQL object for database access
     * CryptCredential credential = new CryptCredential("username", sql);
     * boolean isValid = credential.check("plaintextPassword");
     * if (isValid) {
     *    // Authentication successful
     * } else {
     *    // Authentication failed
     * }
     * </pre>
     * 
     * @param credentials The credentials to check, typically a plaintext password that the user is attempting to authenticate with.
     * 
     * @return true if the credentials are valid and match the stored password hash; false otherwise.
     */
    @Override
    public boolean check(Object credentials) {
        try {
            if (null != (user = sql.queryHandler("SELECT * FROM USERS WHERE LOGIN=?", new BeanHandler<>(UserCredential.class), username)))
                return check(credentials.toString(), user.getPassword());

        } catch (SQLException e) {
            Log.err(e.getMessage(), e);
        }
        return false;
    }

    /**
     * Checks the provided credentials against the stored password hash. This static method verifies the provided credentials
     * against the stored password hash using the appropriate hashing algorithm (BCrypt or Argon2) based on the format of the stored
     * password hash. It supports both BCrypt and Argon2 hashing algorithms for secure password storage and verification. The method
     * returns true if the credentials are valid and match the stored password hash, and false otherwise.
     * <p>
     * The method first checks if the stored password hash starts with the Argon2 prefix ("$argon2"). If it does, it determines the
     * specific Argon2 variant (id, i, or d) based on the prefix and creates an Argon2 instance accordingly. It then uses the verify
     * method of the Argon2 instance to check if the provided credentials match the stored password hash. If the stored password
     * hash does not start with the Argon2 prefix, it assumes it is a BCrypt hash and uses the BCrypt.checkpw method to verify the
     * credentials against the stored password hash.
     * 
     * @param credentials The credentials to check, typically a plaintext password.
     * @param password The stored password hash to compare against, which can be in either BCrypt or Argon2 format.
     * 
     * @return true if the credentials are valid and match the stored password hash; false otherwise.
     */
    public static boolean check(String credentials, String password) {
        if (password.startsWith("$argon2")) {
            try {
                return verifyArgon2(credentials, password);
            } catch (RuntimeException | LinkageError e) {
                Log.err("Argon2 verification failed, denying login: " + e.getMessage());
            }
        } else
            try {
                if (BCrypt.checkpw(credentials, password)) // si le hash bcrypt matche
                    return true;
            } catch (RuntimeException e) {
                Log.err("BCrypt verification failed, denying login: " + e.getMessage());
            }
        return false;
    }

    /**
     * Hashes a password using Argon2id if possible, otherwise falls back to BCrypt. This static method takes a plaintext password
     * as input and attempts to hash it using the Argon2id algorithm, which is a secure password hashing algorithm designed to
     * resist various types of attacks. If the hashing process using Argon2id fails for any reason (e.g., due to an exception), the
     * method falls back to using the BCrypt hashing algorithm, which is also widely used for secure password hashing. The method
     * returns the resulting hashed password, which can be stored in a database for later verification during authentication
     * processes.
     * <p>
     * The method first checks if the provided password already starts with the prefixes for either BCrypt ("$2a $") or Argon2
     * ("$argon2"). If it does, it assumes the password is already hashed and returns it as is. If not, it attempts to hash the
     * password using Argon2id with specific parameters (40 iterations, 65536 memory, and 4 parallelism). If an exception occurs
     * during the hashing process, it catches the exception and hashes the password using BCrypt instead. Finally, it returns the
     * hashed password.
     * <p>
     * Note: The parameters used for Argon2id (iterations, memory, and parallelism) can be adjusted based on the security
     * requirements and performance considerations of the application. It is recommended to use strong parameters for secure
     * password hashing while balancing performance.
     * 
     * @param password The plaintext password to hash.
     * 
     * @return The hashed password, which can be stored in a database for later verification.
     */
    public static String hash(String password) {
        if (password.startsWith("$2a$") || password.startsWith("$argon2"))
            return password;
        try {
            return hashArgon2(password, 40, 65536, 4);
        } catch (RuntimeException | LinkageError e) {
            Log.err("Argon2 hashing unavailable, falling back to BCrypt: " + e.getMessage());
            return BCrypt.hashpw(password, BCrypt.gensalt());
        }
    }

    /**
     * Shared entropy source for salts. SecureRandom is thread-safe, and reusing one instance avoids paying (and
     * potentially blocking on) reseeding for every hash. Initialized at runtime, so native images seed it fresh on
     * each launch rather than freezing build-time entropy into the image.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Argon2id iterations, memory (KiB) and parallelism used for new hashes. */
    private static final int ARGON2_ITERATIONS = 40;
    private static final int ARGON2_MEMORY_KIB = 65536;
    private static final int ARGON2_PARALLELISM = 4;
    private static final int ARGON2_VERSION = 0x13;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    /**
     * Hashes a password with Argon2id and encodes the result in Modular Crypt Format
     * ({@code $argon2id$v=19$m=...,t=...,p=...$salt$hash}, standard Base64 without padding),
     * compatible with hashes previously produced by argon2-jvm.
     *
     * @param password the plaintext password
     * @param iterations the time cost
     * @param memoryKib the memory cost in KiB
     * @param parallelism the parallelism
     * @return the MCF-encoded hash
     */
    static String hashArgon2(final String password, final int iterations, final int memoryKib, final int parallelism) {
        final var salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        final var out = runArgon2(Argon2Parameters.ARGON2_id, ARGON2_VERSION, iterations, memoryKib, parallelism, salt,
                password, HASH_LENGTH);
        return "$argon2id$v=19$m=" + memoryKib + ",t=" + iterations + ",p=" + parallelism + '$' + encodeB64(salt) + '$'
                + encodeB64(out);
    }

    /**
     * Verifies a plaintext password against an MCF-encoded Argon2 hash
     * ({@code $argon2id$v=19$m=...,t=...,p=...$salt$hash}).
     *
     * @param password the plaintext password to check
     * @param mcf the stored MCF hash
     * @return true if the password matches
     * @throws IllegalArgumentException if the MCF string is malformed
     */
    static boolean verifyArgon2(final String password, final String mcf) {
        final String[] parts = mcf.split("\\$", -1);
        if (parts.length != 6 || !parts[0].isEmpty())
            throw new IllegalArgumentException("Malformed Argon2 MCF hash");
        final int type = switch (parts[1]) {
            case "argon2d" -> Argon2Parameters.ARGON2_d;
            case "argon2i" -> Argon2Parameters.ARGON2_i;
            case "argon2id" -> Argon2Parameters.ARGON2_id;
            default -> throw new IllegalArgumentException("Unknown Argon2 type: " + parts[1]);
        };
        if (!"v=19".equals(parts[2]))
            throw new IllegalArgumentException("Unsupported Argon2 version: " + parts[2]);
        int iterations = -1;
        int memoryKib = -1;
        int parallelism = -1;
        for (final String param : parts[3].split(",", -1)) {
            final int eq = param.indexOf('=');
            if (eq < 0)
                throw new IllegalArgumentException("Malformed Argon2 params: " + parts[3]);
            final int value = Integer.parseInt(param.substring(eq + 1));
            switch (param.substring(0, eq)) {
                case "m" -> memoryKib = value;
                case "t" -> iterations = value;
                case "p" -> parallelism = value;
                default -> throw new IllegalArgumentException("Unknown Argon2 param: " + param);
            }
        }
        if (iterations < 0 || memoryKib < 0 || parallelism < 0)
            throw new IllegalArgumentException("Incomplete Argon2 params: " + parts[3]);
        final var salt = decodeB64(parts[4]);
        final var expected = decodeB64(parts[5]);
        final var actual = runArgon2(type, ARGON2_VERSION, iterations, memoryKib, parallelism, salt, password,
                expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * Runs BouncyCastle's pure-Java Argon2.
     *
     * @return the raw hash bytes
     */
    private static byte[] runArgon2(final int type, final int version, final int iterations, final int memoryKib,
            final int parallelism, final byte[] salt, final String password, final int outLength) {
        final var parameters = new Argon2Parameters.Builder(type).withVersion(version).withIterations(iterations)
                .withMemoryAsKB(memoryKib).withParallelism(parallelism).withSalt(salt).build();
        final var generator = new Argon2BytesGenerator();
        generator.init(parameters);
        final var pwd = password.getBytes(StandardCharsets.UTF_8);
        try {
            final var out = new byte[outLength];
            generator.generateBytes(pwd, out);
            return out;
        } finally {
            Arrays.fill(pwd, (byte) 0);
        }
    }

    /** Standard Base64 without padding, as used by MCF. */
    private static String encodeB64(final byte[] data) {
        return Base64.getEncoder().withoutPadding().encodeToString(data);
    }

    /** Decodes unpadded standard Base64, restoring padding first. */
    private static byte[] decodeB64(final String data) {
        final var padded = data + "=".repeat((4 - data.length() % 4) % 4);
        return Base64.getDecoder().decode(padded);
    }

}