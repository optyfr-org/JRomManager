package jrm.security;

import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

import jrm.locale.Messages;
import jrm.profile.Profile;
import jrm.profile.report.Report;
import jrm.profile.scan.Scan;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a user or server session within the Retro-Gaming ROM manager. A session acts as a central execution context,
 * maintaining reference to the authenticated {@link User}, the active {@link Profile}, and the running {@link Scan}. It also tracks
 * execution modes (such as server, multi-user, and update restrictions) and holds a cumulative session {@link Report}.
 * <p>
 * Sessions are created in several flavors depending on the runtime environment:
 * </p>
 * <ul>
 * <li><b>Desktop (standalone):</b> A local session with an auto-generated administrative user named {@code "JRomManager"}.</li>
 * <li><b>Single-user server:</b> A remote session identified by a unique session identifier, without an explicitly assigned
 * user.</li>
 * <li><b>Multi-user server:</b> A remote session identified by a unique session identifier, with an explicitly assigned username
 * and role set.</li>
 * </ul>
 * <p>
 * Subclasses (such as web-server session variants) extend this class to add transport-specific state, such as long-polling message
 * queues and cached resource lists.
 * </p>
 *
 * @author Expert Java Code Documentation Developer
 * 
 * @since 1.0
 * 
 * @see User
 * @see Profile
 * @see Scan
 * @see Report
 */
public class Session {
    /**
     * The default administrative role identifier used for fallback or auto-generated users.
     */
    private static final String ADMIN = "admin";

    /**
     * The unique session identifier string. This field is managed via a Lombok-generated {@code setSessionId(String)} setter method
     * and accessed through the manual {@link #getSessionId()} getter method defined in this class.
     *
     * @param sessionId the unique session identifier to set; may be {@code null} for unassigned local sessions
     */
    private @Setter String sessionId;

    /**
     * The user context associated with this session. This field is declared {@code protected} so that subclasses (e.g., web-server
     * session variants) and classes within the same package can access or override the user reference directly. It may be
     * {@code null} until a user is explicitly assigned or lazily initialized via {@link #getUser()}.
     */
    protected User user = null;

    /**
     * The resource bundle for localized messages and UI text within this session context.
     *
     * @param msgs the resource bundle to set
     * 
     * @return the resource bundle for localized messages
     */
    private @Getter @Setter ResourceBundle msgs = null;

    /**
     * Flag indicating whether the session is operating as a remote/web server session.
     *
     * @return {@code true} if running in server mode, {@code false} otherwise
     */
    private @Getter boolean server = false;

    /**
     * Flag indicating whether multi-user isolation features are enabled.
     *
     * @return {@code true} if multi-user features are active, {@code false} otherwise
     */
    private @Getter boolean multiuser = false;

    /**
     * Flag indicating whether update checking is disabled during the session.
     *
     * @return {@code true} if automatic updates are disabled, {@code false} otherwise
     */
    private @Getter boolean noupdate = false;

    /**
     * The session report accumulating scan findings, audit results, and diagnostic entries. Initialized eagerly as an empty
     * {@link Report} and accumulates findings throughout the session's lifetime.
     *
     * @return the session execution report
     */
    private final @Getter Report report = new Report();

    /**
     * The currently loaded ROM profile containing configuration rules and scan settings.
     *
     * @param currProfile the active ROM profile to load/set
     * 
     * @return the currently loaded ROM profile, or {@code null} if none is active
     */
    private final AtomicReference<Profile> currProfile = new AtomicReference<>();

    public Profile getCurrProfile() {
        return currProfile.get();
    }

    public void setCurrProfile(Profile currProfile) {
        this.currProfile.set(currProfile);
    }

    /**
     * The current scan process execution state.
     *
     * @param currScan the current scan context to set
     * 
     * @return the active scan execution context, or {@code null} if no scan is currently running
     */
    private @Getter @Setter Scan currScan;

    /**
     * Protected default constructor. Used for subclasses or internal framework-driven instantiation where session fields are
     * populated after construction.
     */
    protected Session() {

    }

    /**
     * Package-private constructor to build a standalone desktop session context. Initializes a default user named
     * {@code "JRomManager"} with administrative privileges and loads the default locale resource bundle.
     *
     * @param multiuser {@code true} to enable multi-user environment checks, {@code false} for standard single-user
     * @param noupdate {@code true} to disable check for updates, {@code false} to permit updates
     */
    public Session(boolean multiuser, boolean noupdate) {
        this.multiuser = multiuser;
        this.noupdate = noupdate;
        this.sessionId = null;
        user = new User(this, "JRomManager", new String[] { ADMIN });
        msgs = Messages.getBundle();
    }

    /**
     * Constructs a server session with a specific session identifier and no authenticated user. The {@link #server} flag is set to
     * {@code true} and the default locale resource bundle is loaded. A user must be assigned explicitly via {@link #setUser} (or the
     * multi-user constructor) before privileged operations; {@link #getUser()} does not invent an admin identity.
     *
     * @param sessionId the unique session ID representing this session on the server
     */
    public Session(String sessionId) {
        this.server = true;
        this.sessionId = sessionId;
        msgs = Messages.getBundle();
    }

    /**
     * Constructs a multi-user server session with a specific session identifier, username, and role assignments. Both the
     * {@link #server} and {@link #multiuser} flags are set to {@code true}. When {@code user} is {@code null}, no user is attached
     * until {@link #setUser(String, String[])} is called (e.g. after successful login). When {@code user} is non-null and
     * {@code roles} is {@code null}, roles default to a single {@value #ADMIN} role.
     *
     * @param sessionId the unique session ID representing this session on the server
     * @param user the username to associate with this session; if {@code null}, the session remains unauthenticated
     * @param roles the roles assigned to the user; if {@code null} and {@code user} is non-null, defaults to a single {@code "admin"}
     *        role
     */
    public Session(String sessionId, String user, String[] roles) {
        this.multiuser = true;
        this.server = true;
        this.sessionId = sessionId;
        if (user != null)
            this.user = new User(this, user, roles == null ? new String[] { ADMIN } : roles);
        msgs = Messages.getBundle();
    }

    /**
     * Returns whether this session has an authenticated {@link User}. Server sessions stay unauthenticated until a user is assigned
     * explicitly; desktop sessions always have a user after construction.
     *
     * @return {@code true} if a user is attached, {@code false} otherwise
     */
    public boolean hasUser() {
        return user != null;
    }

    /**
     * Returns the user context associated with this session.
     * <p>
     * For non-server (desktop/CLI) sessions only, a missing user is lazily initialized as the local administrator
     * {@code "JRomManager"}. Server sessions never auto-elevate: callers must authenticate and assign a user first, or
     * {@link #hasUser()} is {@code false} and this method throws.
     * </p>
     *
     * @return the active {@link User} associated with this session; never {@code null}
     * @throws IllegalStateException if this is a server session with no authenticated user
     */
    public User getUser() {
        if (user == null) {
            if (server)
                throw new IllegalStateException("No authenticated user for server session");
            user = new User(this, "JRomManager", new String[] { ADMIN });
        }
        return user;
    }

    /**
     * Configures or overrides the user profile associated with this session. Creates a new {@link User} instance with the given
     * username and roles, replacing any previously assigned user.
     *
     * @param user the username of the user
     * @param roles the roles to assign to the user
     */
    public void setUser(String user, String[] roles) {
        this.user = new User(this, user, roles);
    }

    /**
     * Retrieves the unique session identifier.
     *
     * @return the session ID, or {@code null} if this is an unassigned local session
     */
    public String getSessionId() {
        return sessionId;
    }

}
