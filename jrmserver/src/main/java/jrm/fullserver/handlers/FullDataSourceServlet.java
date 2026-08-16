package jrm.fullserver.handlers;

import java.io.BufferedInputStream;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.fullserver.datasources.AdminXMLResponse;
import jrm.server.shared.TempFileInputStream;
import jrm.server.shared.WebSession;
import jrm.server.shared.datasources.XMLRequest;
import jrm.server.shared.handlers.DataSourceServlet;

/**
 * FullDataSourceServlet is a servlet that extends the DataSourceServlet to handle specific requests for the jrm full server
 * application. It overrides the processResponse method to provide custom handling for requests to the "/datasources/admin"
 * endpoint, allowing for processing of administrative XML requests. For other endpoints, it delegates to the superclass
 * implementation.
 * <p>
 * The servlet processes incoming HTTP requests, checks the request URI, and if it matches the admin endpoint, it reads the request
 * input stream, creates an AdminXMLResponse object to process the XML request, and returns the resulting TempFileInputStream. For
 * other URIs, it calls the superclass's processResponse method to handle the request as usual.
 * 
 * @author jrm
 * 
 * @version 1.0
 * 
 * @since 2024-06
 */
public class FullDataSourceServlet extends DataSourceServlet {

    /**
     * Processes the HTTP request and generates a response based on the request URI. If the request URI is "/datasources/admin", it
     * processes the request as an administrative XML request. For other URIs, it delegates to the superclass implementation.
     * 
     * @param sess The WebSession associated with the request.
     * @param req The HttpServletRequest object containing the client's request.
     * @param resp The HttpServletResponse object for sending the response back to the client.
     * 
     * @return a TempFileInputStream containing the response data.
     * 
     * @throws IOException if an I/O error occurs during processing of the request or response.
     * @throws XMLStreamException if an error occurs while processing XML data in the request.
     */
    @Override
    protected TempFileInputStream processResponse(WebSession sess, HttpServletRequest req, HttpServletResponse resp) throws IOException, XMLStreamException {
        if ("/datasources/admin".equals(req.getRequestURI())) {
            try (final var in = new BufferedInputStream(req.getInputStream())) {
                try (final var response = new AdminXMLResponse(new XMLRequest(sess, in, req.getContentLength()))) {
                    return response.processRequest();
                }
            }
        } else
            return super.processResponse(sess, req, resp);
    }

}
