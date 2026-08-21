package jrm.fullserver.datasources;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import jrm.fullserver.security.CryptCredential;
import jrm.fullserver.security.Login;
import jrm.fullserver.security.UserCredential;
import jrm.server.shared.datasources.XMLRequest;
import jrm.server.shared.datasources.XMLRequest.Operation;
import jrm.server.shared.datasources.XMLResponse;
import lombok.val;

/**
 * AdminXMLResponse is an XMLResponse that allows an admin user to manage user credentials. It supports fetching all users, adding a
 * new user, updating an existing user, and removing a user. The response is structured in XML format, and the operations are
 * performed on a database using the Login class for database interactions. The class checks if the user making the request is an
 * admin before allowing any operations. If the user is not an admin, it returns a failure response with a message indicating that
 * the operation cannot be performed. The class uses the BeanHandler and BeanListHandler from Apache Commons DbUtils to map database
 * query results to UserCredential objects. It also uses the CryptCredential class to hash passwords before storing them in the
 * database. The XML response structure includes a status element to indicate the success or failure of the operation, and a data
 * element that contains the user records when fetching or modifying user credentials.
 * 
 * @author jrm
 * 
 * @version 1.0
 * 
 * @since 2024-06
 * 
 * @see XMLResponse
 * @see XMLRequest
 * @see Login
 * @see UserCredential
 * @see CryptCredential
 * @see BeanHandler
 * @see BeanListHandler
 */
public class AdminXMLResponse extends XMLResponse {
    private static final String CAN_T_DO_THAT = "Can't do that!";
    private static final String ROLES = "Roles";
    private static final String PWTITLE = "Password";
    private static final String LOGIN = "Login";
    private static final String RESPONSE = "response";
    private static final String STATUS = "status";

    /**
     * Constructs a new AdminXMLResponse object.
     * 
     * @param request The XMLRequest object containing the request details.
     * 
     * @throws IOException If an I/O error occurs.
     * @throws XMLStreamException If an error occurs while writing the XML response.
     */
    public AdminXMLResponse(XMLRequest request) throws IOException, XMLStreamException {
        super(request);
    }

    /**
     * Fetches all user credentials from the database and writes them to the XML response. Only admin users are allowed to perform
     * this operation. If the user is not an admin, a failure response is returned.
     * 
     * @param operation The operation containing the request details.
     * 
     * @throws XMLStreamException If an error occurs while writing the XML response.
     */
    @Override
    public void fetch(Operation operation) throws XMLStreamException {
        if (request.getSession().getUser().isAdmin()) {
            try (final var login = new Login()) {
                val rows = login.queryHandler("SELECT * FROM USERS", new BeanListHandler<UserCredential>(UserCredential.class));
                writer.writeStartElement(RESPONSE);
                writer.writeElement(STATUS, "0");
                fetchList(operation, rows, (row, _) -> writeRecord(row));
                writer.writeEndElement();
            } catch (Exception e) {
                failure(e.getMessage());
            }
        } else
            failure(CAN_T_DO_THAT);
    }

    /**
     * Writes an individual user record to the XML output.
     * 
     * @param user The UserCredential object to be written.
     * 
     * @throws XMLStreamException If an error occurs while writing the XML.
     */
    private void writeRecord(UserCredential user) throws XMLStreamException {
        writer.writeStartElement("record");
        writer.writeAttribute(LOGIN, user.getLogin());
        writer.writeAttribute(PWTITLE, user.getPassword());
        writer.writeAttribute(ROLES, user.getRoles());
        writer.writeEndElement();
    }

    /**
     * Adds a new user credential to the database. Only admin users are allowed to perform this operation. The method checks if the
     * required data (login and password) is provided in the operation. If the user is not an admin or if the required data is
     * missing, a failure response is returned.
     * 
     * @param operation The operation containing the request details and data for the new user.
     * 
     * @throws XMLStreamException If an error occurs while writing the XML response.
     */
    @Override
    public void add(Operation operation) throws XMLStreamException {
        if (request.getSession().getUser().isAdmin()) {
            if (operation.hasData(LOGIN) && operation.hasData(PWTITLE)) {
                try (final var login = new Login()) {
                    login.update("INSERT INTO USERS VALUES(?, ?, ?)", operation.getData(LOGIN), CryptCredential.hash(operation.getData(PWTITLE)),
                            Optional.ofNullable(operation.getData(ROLES)).orElse("admin"));
                    fetchSingle(operation, login);
                } catch (Exception e) {
                    failure(e.getMessage());
                }
            } else
                failure();
        } else
            failure(CAN_T_DO_THAT);
    }

    /**
     * Updates an existing user credential in the database. Only admin users are allowed to perform this operation. The method
     * checks if the required data (login and password) is provided in the operation. If the user is not an admin or if the required
     * data is missing, a failure response is returned.
     * 
     * @param operation The operation containing the request details and data for the user to be updated.
     * 
     * @throws XMLStreamException If an error occurs while writing the XML response.
     */
    @Override
    public void update(Operation operation) throws XMLStreamException {
        if (request.getSession().getUser().isAdmin()) {
            if (operation.hasData(LOGIN) && operation.hasData(PWTITLE)) {
                try (final var login = new Login()) {
                    login.update("UPDATE USERS SET PASSWORD=?, ROLES=? WHERE LOGIN=?", CryptCredential.hash(operation.getData(PWTITLE)),
                            Optional.ofNullable(operation.getData(ROLES)).orElse("admin"), operation.getData(LOGIN));
                    fetchSingle(operation, login);
                } catch (Exception e) {
                    failure(e.getMessage());
                }
            } else
                failure();
        } else
            failure(CAN_T_DO_THAT);
    }

    /**
     * Removes a user credential from the database. Only admin users are allowed to perform this operation. The method checks if the
     * required data (login) is provided in the operation. If the user is not an admin or if the required data is missing, a failure
     * response is returned.
     * 
     * @param operation The operation containing the request details and data for the user to be removed.
     * 
     * @throws XMLStreamException If an error occurs while writing the XML response.
     */
    @Override
    public void remove(Operation operation) throws XMLStreamException {
        if (request.getSession().getUser().isAdmin()) {
            if (operation.hasData(LOGIN)) {
                try (final var login = new Login()) {
                    if (0 != login.update("DELETE FROM USERS WHERE LOGIN=?", operation.getData(LOGIN))) {
                        writer.writeStartElement(RESPONSE);
                        writer.writeElement(STATUS, "0");
                        writer.writeStartElement("data");
                        writer.writeStartElement("record");
                        writer.writeAttribute(LOGIN, operation.getData(LOGIN));
                        writer.writeEndElement();
                        writer.writeEndElement();
                        writer.writeEndElement();
                    } else
                        failure();
                } catch (Exception e) {
                    failure(e.getMessage());
                }
            } else
                failure();
        } else
            failure(CAN_T_DO_THAT);
    }

    /**
     * Fetches a single user credential from the database and writes it to the XML response.
     * 
     * @param operation The operation containing the request details.
     * @param login The Login object used for database interactions.
     * 
     * @throws SQLException If a database error occurs.
     * @throws XMLStreamException If an error occurs while writing the XML response.
     */
    private void fetchSingle(Operation operation, final Login login) throws SQLException, XMLStreamException {
        val user = login.queryHandler("SELECT * FROM USERS WHERE LOGIN=?", new BeanHandler<UserCredential>(UserCredential.class), operation.getData(LOGIN));
        if (user != null)
            writeSingle(user);
        else
            failure();
    }

    /**
     * Writes a single user credential to the XML response.
     * 
     * @param user The UserCredential object to be written to the XML response.
     * 
     * @throws XMLStreamException If an error occurs while writing the XML response.
     */
    private void writeSingle(final jrm.fullserver.security.UserCredential user) throws XMLStreamException {
        writer.writeStartElement(RESPONSE);
        writer.writeElement(STATUS, "0");
        writer.writeStartElement("data");
        writeRecord(user);
        writer.writeEndElement();
        writer.writeEndElement();
    }

}
