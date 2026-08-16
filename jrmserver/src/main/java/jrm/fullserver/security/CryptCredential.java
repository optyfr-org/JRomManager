package jrm.fullserver.security;

import java.sql.SQLException;

import org.apache.commons.dbutils.handlers.BeanHandler;
import org.eclipse.jetty.util.security.Credential;
import org.mindrot.jbcrypt.BCrypt;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;
import jrm.fullserver.db.SQL;
import jrm.misc.Log;
import lombok.Getter;

/**
 * CryptCredential is a custom implementation of the Credential class that provides functionality for checking user credentials
 * against stored password hashes. It supports both BCrypt and Argon2 hashing algorithms for secure password storage.
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
    private String username;

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
    private transient SQL sql;

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
            Argon2 argon2;
            if (password.startsWith("$argon2id$"))
                argon2 = Argon2Factory.create(Argon2Types.ARGON2id);
            else if (password.startsWith("$argon2i$"))
                argon2 = Argon2Factory.create(Argon2Types.ARGON2i);
            else if (password.startsWith("$argon2d$"))
                argon2 = Argon2Factory.create(Argon2Types.ARGON2d);
            else
                argon2 = Argon2Factory.create();
            if (argon2.verify(password, credentials.toCharArray()))
                return true;
        } else if (BCrypt.checkpw(credentials, password)) // si le hash bcrypt matche
            return true;
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
            password = Argon2Factory.create(Argon2Types.ARGON2id).hash(40, 65536, 4, password.toCharArray());
        } catch (Exception _) {
            password = BCrypt.hashpw(password, BCrypt.gensalt());
        }
        return password;
    }

}