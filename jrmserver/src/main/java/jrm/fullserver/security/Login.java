package jrm.fullserver.security;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import javax.security.auth.Subject;

import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.RolePrincipal;
import org.eclipse.jetty.security.UserIdentity;
import org.eclipse.jetty.security.UserPrincipal;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Session;

import jrm.fullserver.ServerSettings;
import jrm.fullserver.db.DB;
import jrm.fullserver.db.SQL;
import jrm.misc.Log;
import jrm.server.shared.WebSession;
import lombok.Getter;
import lombok.Setter;

/**
 * Login service for the Jetty server.
 * <p>
 * This class implements the LoginService interface to provide authentication and authorization functionality for the Jetty server.
 * It uses a database to store user credentials and roles, and provides methods for logging in, validating user identities, and
 * logging out. The class also includes a caching mechanism to improve performance by storing user identities in memory for a short
 * period of time. The login method checks the provided credentials against the stored credentials in the database and creates a
 * UserIdentity object if the credentials are valid. The validate method checks if a UserIdentity is valid by checking the cache,
 * and the logout method removes the UserIdentity from the cache. The class also includes a default admin user that is created if
 * the users table is empty.
 */
public class Login extends SQL implements LoginService {
    /**
     * The default admin username.
     * <p>
     * This constant defines the default username for the admin user that is created if the users table in the database is empty.
     * The admin user is created with the username "admin" and a password that is hashed using the CryptCredential class. The admin
     * user is assigned the "admin" role, which can be used to grant administrative privileges to the user. It is important to
     * change the default admin credentials after the initial setup for security reasons.
     */
    private static final String ADMIN = "admin";

    /**
     * The database instance used for authentication.
     * <p>
     * This field holds the database instance that is used to access the user credentials and roles stored in the database. It is
     * initialized in the constructor by connecting to the database using the DB class. The database connection is used to execute
     * SQL queries for creating the users table, inserting the default admin user, and retrieving user credentials during the login
     * process. It is important to ensure that the database connection is properly managed and closed when it is no longer needed to
     * prevent resource leaks and ensure the stability of the application.
     */
    protected IdentityService identityService = new DefaultIdentityService();

    /**
     * Constructs a new Login service and initializes the database connection. It creates the users table if it does not exist and
     * inserts a default admin user if the table is empty.
     * <p>
     * The constructor connects to the database using the DB class and executes SQL queries to set up the users table and default
     * admin user. It is important to handle any potential exceptions that may occur during the database connection and setup
     * process, such as IOException or SQLException, to ensure that the application can gracefully handle errors and provide
     * appropriate feedback to the user or administrator.
     * 
     * @throws IOException If an I/O error occurs while connecting to the database or executing SQL queries.
     * @throws SQLException If a database access error occurs while connecting to the database or executing SQL queries.
     */
    public Login() throws IOException, SQLException {
        super(true, new ServerSettings());
        db = DB.getInstance(getSettings()).connectToDB("Server.sys", false, true);
        update("CREATE TABLE IF NOT EXISTS USERS(LOGIN VARCHAR_IGNORECASE(255) PRIMARY KEY, PASSWORD VARCHAR(255), ROLES VARCHAR(255))");
        if (count("SELECT * FROM USERS") == 0)
            update("INSERT INTO USERS VALUES(?, ?, ?)", ADMIN, CryptCredential.hash(ADMIN), ADMIN);
    }

    /**
     * Checks if the default admin user exists in the database. This method queries the users table to determine if a user with the
     * username "admin" exists. It returns true if the admin user is found, indicating that the default admin user is present in the
     * database. If the admin user is not found, it returns false, indicating that the default admin user does not exist.
     * <p>
     * Note: This method is useful for checking the existence of the default admin user during application startup or for
     * administrative purposes. It can be used to verify that the default admin user has been created and is available for login.
     * It is important to ensure that the users table is properly set up and populated with user credentials before calling this
     * method to avoid potential errors or unexpected behavior.
     * 
     * @return true if the default admin user exists in the database; false otherwise.
     * @throws SQLException If a database access error occurs while querying the users table.
     */
    public boolean checkAdmin() throws SQLException {
        return count("SELECT * FROM USERS WHERE LOGIN = ?", ADMIN) > 0;
    }


    /**
     * Returns the name of the login service.
     * <p>
     * This method is required by the LoginService interface and is used to identify the login service. In this implementation, it
     * returns the string "Authentication" to indicate that this login service is responsible for handling authentication for the
     * server. The name can be used for logging, debugging, or other purposes to identify the specific login service being used in
     * the application. It is important to choose a descriptive and meaningful name for the login service to clearly indicate its
     * purpose and functionality within the application.
     * <p>
     * Note: The name of the login service does not affect its functionality or behavior, but it can be useful for organizational
     * and debugging purposes to have a clear and descriptive name for the login service. It is recommended to choose a name that
     * accurately reflects the role of the login service within the application, such as "Authentication" or "UserLoginService", to
     * provide clarity and context when working with the login service in the codebase or when analyzing logs and debugging issues
     * related to authentication.
     * 
     * @return the name of the login service, which is "Authentication" in this implementation.
     */
    @Override
    public String getName() {
        return "Authentication";
    }

    /**
     * A cache for storing user identities, with a timestamp to track when the cache was last updated. The cache is used to improve
     * performance by storing user identities in memory for a short period of time.
     * <p>
     * The cache is implemented as a HashMap where the key is a combination of the username and session ID, and the value is the
     * UserIdentity object representing the authenticated user's identity. The cache is cleared if it has been more than 60 seconds
     * since the last update to ensure that stale user identities are not used. The cache is synchronized to ensure thread safety
     * when accessing and modifying the cache.
     * <p>
     * Note: The cache is a simple in-memory cache and does not persist user identities across server restarts. It is intended to
     * improve performance by reducing the number of database queries for user authentication, but it should be used with caution to
     * ensure that it does not introduce security issues or allow unauthorized access to protected resources. It is important to
     * properly manage the cache and ensure that it is cleared appropriately to maintain the security and integrity of the
     * authentication system.
     */
    @Getter
    private static final HashMap<String, UserIdentity> cache = new HashMap<>();

    /**
     * Reentrant lock protecting {@link #cache} and {@link #cachetime}. Uses {@link ReentrantLock} rather than
     * {@code synchronized} so that virtual threads handling requests are not pinned to their carrier thread
     * while waiting for the lock during database I/O in {@link #login(String, Object, String, WebSession)}.
     */
    private static final ReentrantLock cacheLock = new ReentrantLock();

    /**
     * The timestamp of the last cache update, used to determine when to clear the cache. The cache is cleared if it has been more
     * than 60 seconds since the last update.
     * <p>
     * This field is used to track when the cache was last updated, and it is updated whenever the cache is modified (e.g., when a
     * new UserIdentity is added to the cache). The cache is cleared if it has been more than 60 seconds since the last update to
     * ensure that stale user identities are not used. The cachetime is initialized to the current time when the Login class is
     * loaded, and it is updated whenever the cache is modified to reflect the most recent update time. It is important to properly
     * manage the cachetime to ensure that the cache is cleared at appropriate intervals and that stale user identities are not used
     * for authentication, which could lead to security issues or unauthorized access to protected resources.
     * <p>
     * Note: The cachetime is used in conjunction with the cache to determine when to clear the cache and ensure that only valid
     * user identities are stored in memory. It is important to handle the cachetime correctly to maintain the security and
     * integrity of the authentication system while also improving performance by reducing database queries for user authentication.
     */
    @Setter
    private static long cachetime = System.currentTimeMillis();

    /**
     * Logs in a user by checking the provided credentials against the stored credentials in the database. It retrieves the session
     * ID from the request and checks if a UserIdentity is already cached for the username and session ID. If a cached UserIdentity
     * is found, it is returned. Otherwise, it creates a new UserIdentity by checking the credentials against the database and
     * caching the result if the login is successful. The method also sets the user information in the session if a session ID is
     * available.
     * <p>
     * Note: The cache is used to improve performance by storing user identities in memory for a short period of time. The cache is
     * cleared if it has been more than 60 seconds since the last update to ensure that stale user identities are not used. The
     * method is synchronized on the cache to ensure thread safety when accessing and modifying the cache.
     * <p>
     * The method returns a UserIdentity object if the login is successful, or null if the login fails due to invalid credentials or
     * other issues. It is important to handle the return value appropriately in the calling code to ensure that only authenticated
     * users are granted access to protected resources.
     * <p>
     * The method may throw IOException or SQLException if there are issues with database access or other I/O operations during the
     * login process. Ensure that proper error handling is implemented when calling this method to handle potential exceptions
     * gracefully.
     * <p>
     * Note: The actual implementation of the login logic is delegated to a private login method that takes the username,
     * credentials, session ID, and WebSession as parameters. This allows for separation of concerns and makes it easier to manage
     * the login logic while still adhering to the LoginService interface requirements.
     * <p>
     * The method retrieves the session ID from the request and checks if a UserIdentity is already cached for the username and
     * session ID. If a cached UserIdentity is found, it is returned immediately, improving performance by avoiding unnecessary
     * database queries. If no cached UserIdentity is found, it creates a new UserIdentity by checking the credentials against the
     * database using the CryptCredential class. If the credentials are valid, it creates a UserIdentity object with the user's
     * roles and caches it for future use. The method also sets the user information in the session if a session ID is available,
     * allowing for session-based authentication and authorization in subsequent requests.
     * <p>
     * Note: It is important to ensure that the credentials provided by the user are properly validated and sanitized to prevent
     * security vulnerabilities such as SQL injection or other types of attacks. The CryptCredential class should be implemented
     * securely to handle password hashing and verification, and the database queries should be parameterized to prevent SQL
     * injection. Additionally, proper error handling should be implemented to handle potential exceptions that may occur during the
     * login process, such as database access errors or other I/O issues, to ensure that the application can gracefully handle
     * errors and provide appropriate feedback to the user or administrator.
     * <p>
     * The method is designed to be thread-safe by synchronizing on the cache when accessing and modifying it. This ensures that
     * multiple concurrent login attempts do not cause race conditions or other issues with the cache, which could lead to
     * inconsistent behavior or security vulnerabilities. It is important to ensure that the synchronization is properly implemented
     * to maintain the integrity of the cache and prevent potential issues with concurrent access.
     * <p>
     * Note: The method assumes that the credentials provided by the user are in a format that can be checked against the stored
     * credentials in the database using the CryptCredential class. It is important to ensure that the credentials are properly
     * formatted and validated before being passed to this method to prevent issues with authentication and ensure that only valid
     * credentials are accepted for login.
     * <p>
     * The method also assumes that the Request object provided contains the necessary information to retrieve the session ID and
     * manage the session for the login process. It is important to ensure that the Request object is properly constructed and
     * contains the required information for session management to ensure that the login process works correctly and securely.
     * <p>
     * Note: The method may need to be modified or extended in the future to support additional authentication mechanisms, such as
     * multi-factor authentication or integration with external identity providers. It is important to design the login logic in a
     * way that allows for flexibility and extensibility to accommodate future requirements and enhancements to the authentication
     * system.
     * 
     * @param username The username of the user attempting to log in.
     * @param credentials The credentials provided by the user, typically a plaintext password.
     * @param request The Request object containing information about the login request.
     * @param getOrCreateSession A function that retrieves or creates a Session object for the login request.
     * 
     * @return A UserIdentity object representing the authenticated user if the login is successful; null otherwise.
     */
    @Override
    public UserIdentity login(String username, Object credentials, Request request, Function<Boolean, Session> getOrCreateSession) {
        String sessionid = null;
        WebSession sess = null;
        if (request instanceof Request) {
            final var session = getOrCreateSession.apply(true);
            if (session != null) {
                sessionid = session.getId();
                if (sessionid != null)
                    sess = (WebSession) session.getAttribute("session");
            }
        }
        return login(username, credentials, sessionid, sess);
    }

    /**
     * Logs in a user by checking the credentials against the database. This method is called by the login method that handles the
     * Request object. It checks if a UserIdentity is already cached for the username and session ID, and if so, it returns the
     * cached UserIdentity. If not, it creates a new UserIdentity by checking the credentials against the database using the
     * CryptCredential class. If the credentials are valid, it creates a UserIdentity object with the user's roles and caches it for
     * future use. The method also sets the user information in the session if a session ID is available.
     * <p>
     * Note: The cache is cleared if it has been more than 60 seconds since the last update to ensure that stale user identities are
     * not used. The method is synchronized on the cache to ensure thread safety when accessing and modifying the cache.
     * <p>
     * The method returns a UserIdentity object if the login is successful, or null if the login fails due to invalid credentials or
     * other issues. It is important to handle the return value appropriately in the calling code to ensure that only authenticated
     * users are granted access to protected resources.
     * <p>
     * The method may throw IOException or SQLException if there are issues with database access or other I/O operations during the
     * login process. Ensure that proper error handling is implemented when calling this method to handle potential exceptions
     * gracefully.
     * 
     * @param username The username of the user attempting to log in.
     * @param credentials The credentials provided by the user, typically a plaintext password.
     * @param sessionid The session ID associated with the login request, used for caching the UserIdentity.
     * @param sess The WebSession object associated with the login request, used for setting user information in the session if the
     *        login is successful.
     * 
     * @return A UserIdentity object representing the authenticated user if the login is successful; null otherwise.
     * 
     * @throws IOException If an I/O error occurs during the login process, such as issues with database access or other I/O
     *         operations.
     * @throws SQLException If a database access error occurs during the login process, such as issues with querying the database
     *         for user credentials or roles.
     * 
     * @see #login(String, Object, Request, Function) for the method that handles the Request object and calls this method for the
     *      actual login logic.
     * @see CryptCredential for the class used to check the credentials against the database and retrieve user information.
     * @see UserIdentity for the class representing the authenticated user's identity, including their roles and credentials.
     */
    private UserIdentity login(String username, Object credentials, String sessionid, WebSession sess) {
        cacheLock.lock();
        try {
            evictStaleCache();
            final var cached = cachedIdentity(username, sessionid, sess);
            if (cached != null) {
                return cached;
            }
            return authenticateAndCache(username, credentials, sessionid, sess);
        } finally {
            cacheLock.unlock();
        }
    }

    private static void evictStaleCache() {
        if (60000 < (System.currentTimeMillis() - cachetime)) {
            cache.clear();
            setCachetime(System.currentTimeMillis());
        }
    }

    private static UserIdentity cachedIdentity(String username, String sessionid, WebSession sess) {
        if (sessionid == null) {
            return null;
        }
        final var identity = cache.get(username + ":" + sessionid);
        if (identity == null) {
            return null;
        }
        if (sess != null && !sess.hasUser()) {
            sess.setUser(username, rolesFromIdentity(identity));
        }
        return identity;
    }

    private UserIdentity authenticateAndCache(String username, Object credentials, String sessionid, WebSession sess) {
        final var credential = new CryptCredential(username, this);
        if (!credential.check(credentials)) {
            return null;
        }
        final var principal = new UserPrincipal(credential.getUser().getLogin(), credential);
        final var subject = new Subject();
        subject.getPrincipals().add(principal);
        subject.getPublicCredentials().add(username + ":" + sessionid);
        final String[] roles = credential.getUser().getRoles().split(";");
        for (String role : roles) {
            subject.getPrincipals().add(new RolePrincipal(role));
        }
        final var identity = identityService.newUserIdentity(subject, principal, roles);
        if (sessionid != null) {
            cache.put(username + ":" + sessionid, identity);
            if (sess != null) {
                sess.setUser(username, roles);
            }
        }
        return identity;
    }

    /**
     * Extracts role names from a Jetty {@link UserIdentity} subject (RolePrincipal principals).
     *
     * @param identity authenticated identity
     * @return role name array; never null
     */
    private static String[] rolesFromIdentity(final UserIdentity identity) {
        if (identity == null || identity.getSubject() == null)
            return new String[0];
        return identity.getSubject().getPrincipals(RolePrincipal.class).stream().map(RolePrincipal::getName).toArray(String[]::new);
    }

    /**
     * Validates the user's identity. This method checks if the user's identity is valid by checking the cache for the user's
     * credentials. It iterates through the public credentials of the user's subject and checks if any of them are present in the
     * cache. If a matching credential is found in the cache, it returns true, indicating that the user's identity is valid. If no
     * matching credential is found in the cache, it returns false, indicating that the user's identity is not valid.
     * <p>
     * Note: The cache is used to improve performance by storing user identities in memory for a short period of time. The cache is
     * cleared if it has been more than 60 seconds since the last update to ensure that stale user identities are not used. The
     * method is synchronized on the cache lock to ensure thread safety when accessing the cache.
     * <p>
     * The method returns true if the user's identity is valid (i.e., if a matching credential is found in the cache), or false if
     * the user's identity is not valid (i.e., if no matching credential is found in the cache). It is important to handle the
     * return value appropriately in the calling code to ensure that only authenticated users are granted access to protected
     * resources.
     * <p>
     * The method may throw exceptions if there are issues with accessing the cache or other internal errors during the validation
     * process. Ensure that proper error handling is implemented when calling this method to handle potential exceptions gracefully.
     * 
     * @param user The UserIdentity object representing the user's identity to validate. This object contains the user's credentials
     *        and roles, which are used to check against the cache for validation.
     * 
     * @return true if the user's identity is valid (i.e., if a matching credential is found in the cache); false otherwise.
     * 
     * @see #login(String, Object, String, WebSession) for the method that handles the login logic and populates the cache with user
     *      identities.
     * @see UserIdentity for the class representing the authenticated user's identity, including their credentials and roles.
     * @see #logout(UserIdentity) for the method that handles logging out a user and removing their identity from the cache.
     */
    @Override
    public boolean validate(UserIdentity user) {
        Log.debug("validate");
        cacheLock.lock();
        try {
            for (final var credential : user.getSubject().getPublicCredentials())
                if (cache.containsKey(credential))
                    return true;
            return false;
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Returns the IdentityService associated with this LoginService. The IdentityService is responsible for managing user
     * identities and providing authentication and authorization services. In this implementation, it returns the identityService
     * instance variable, which is initialized as a DefaultIdentityService. This method allows other components of the server to
     * access the IdentityService for performing authentication and authorization tasks.
     * <p>
     * Note: The IdentityService returned by this method is used by the Jetty server to manage user identities and perform
     * authentication and authorization tasks. It is important to ensure that the IdentityService is properly configured and
     * implemented to meet the security requirements of the application and to provide the necessary functionality for managing user
     * identities and access control. If there is a need to use a different IdentityService, it should be implemented in a subclass
     * of Login that overrides this method to return the desired IdentityService implementation.
     * <p>
     * The method does not take any parameters and returns an IdentityService object that can be used by other components of the
     * server to perform authentication and authorization tasks based on the user identities managed by this LoginService. It is
     * important to ensure that the IdentityService returned by this method is compatible with the authentication and authorization
     * mechanisms used in the application to ensure that it can effectively manage user identities and provide the necessary
     * functionality for securing access to protected resources.
     * <p>
     * Example usage:
     * 
     * <pre>
     * LoginService loginService = new Login();
     * IdentityService identityService = loginService.getIdentityService();
     * // Use the identityService for authentication and authorization tasks
     * </pre>
     * 
     * @return the IdentityService associated with this LoginService
     */
    @Override
    public IdentityService getIdentityService() {
        return identityService;
    }

    /**
     * Sets the IdentityService for this LoginService. In this implementation, the method is disabled and does not allow changing
     * the IdentityService. This is because the Login class uses a DefaultIdentityService that is initialized in the constructor,
     * and changing it may lead to unexpected behavior or security issues. If there is a need to use a different IdentityService, it
     * should be implemented in a subclass of Login that overrides this method to allow setting a custom IdentityService.
     * 
     * @param service the IdentityService to set for this LoginService
     */
    @Override
    public void setIdentityService(IdentityService service) {
        // disabled
    }

    /**
     * Logs out a user by removing their identity from the cache. This method iterates through the public credentials of the user's
     * subject and removes any matching credentials from the cache. This effectively logs out the user by invalidating their cached
     * identity, ensuring that they will need to log in again to access protected resources. The method is synchronized on the cache
     * lock to ensure thread safety when modifying the cache.
     * <p>
     * Note: The cache is used to improve performance by storing user identities in memory for a short period of time. The cache is
     * cleared if it has been more than 60 seconds since the last update to ensure that stale user identities are not used. The
     * method should be called when a user logs out to ensure that their identity is removed from the cache and they are no longer
     * authenticated.
     * <p>
     * The method does not return a value, but it effectively logs out the user by removing their identity from the cache. It is
     * important to call this method when a user logs out to ensure that their identity is properly invalidated and they are
     * required to log in again for future access.
     * <p>
     * The method may throw exceptions if there are issues with accessing or modifying the cache during the logout process. Ensure
     * that proper error handling is implemented when calling this method to handle potential exceptions gracefully.
     * 
     * @param user The UserIdentity object representing the user's identity to log out. This object contains the user's credentials
     *        and roles, which are used to identify and remove their identity from the cache.
     * 
     * @see #validate(UserIdentity) for the method that checks if a user's identity is valid based on the cache.
     * @see #login(String, Object, String, WebSession) for the method that handles the login logic and populates the cache with user
     *      identities.
     */
    @Override
    public void logout(UserIdentity user) {
        cacheLock.lock();
        try {
            for (final var credential : user.getSubject().getPublicCredentials())
                cache.remove(credential);
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Returns the context associated with this LoginService. In this implementation, the method returns null, indicating that there
     * is no specific context associated with this LoginService. The context can be used to provide additional information or
     * configuration for the LoginService, but in this case, it is not utilized. If there is a need to provide a specific context
     * for this LoginService, it can be implemented in a subclass that overrides this method to return the desired context.
     * 
     * @return null, indicating that there is no specific context associated with this LoginService
     */
    @Override
    public String getContext() {
        return null;
    }
}
