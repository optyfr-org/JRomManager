package jrm.server.shared.handlers;

import java.io.BufferedInputStream;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jrm.misc.Log;
import jrm.server.shared.TempFileInputStream;
import jrm.server.shared.WebSession;
import jrm.server.shared.datasources.AnywareListListXMLResponse;
import jrm.server.shared.datasources.AnywareListXMLResponse;
import jrm.server.shared.datasources.AnywareXMLResponse;
import jrm.server.shared.datasources.BatchCompressorFRXMLResponse;
import jrm.server.shared.datasources.BatchDat2DirResultXMLResponse;
import jrm.server.shared.datasources.BatchDat2DirSDRXMLResponse;
import jrm.server.shared.datasources.BatchDat2DirSrcXMLResponse;
import jrm.server.shared.datasources.BatchTrntChkReportTreeXMLResponse;
import jrm.server.shared.datasources.BatchTrntChkSDRXMLResponse;
import jrm.server.shared.datasources.CatVerXMLResponse;
import jrm.server.shared.datasources.NPlayersXMLResponse;
import jrm.server.shared.datasources.ProfilesListXMLResponse;
import jrm.server.shared.datasources.ProfilesTreeXMLResponse;
import jrm.server.shared.datasources.RemoteFileChooserXMLResponse;
import jrm.server.shared.datasources.RemoteRootChooserXMLResponse;
import jrm.server.shared.datasources.ReportTreeXMLResponse;
import jrm.server.shared.datasources.XMLRequest;
import jrm.server.shared.datasources.XMLResponse;

/**
 * Servlet responsible for processing XML-based data source requests from the web client.
 * <p>
 * This servlet acts as a central dispatcher for all data source operations. It receives XML-encoded requests via POST, parses them
 * into an {@link XMLRequest}, and routes them to the appropriate {@link XMLResponse} implementation based on the request URI.
 * <h2>Supported endpoints</h2>
 * <ul>
 * <li>{@code /datasources/profilesTree} - Profile tree structure</li>
 * <li>{@code /datasources/profilesList} - Profile list</li>
 * <li>{@code /datasources/remoteFileChooser} - Remote file chooser browser</li>
 * <li>{@code /datasources/remoteRootChooser} - Remote root directory chooser</li>
 * <li>{@code /datasources/CatVer} - Category/Version data</li>
 * <li>{@code /datasources/NPlayers} - N-Players data</li>
 * <li>{@code /datasources/AnywareListList} - Anyware list of lists</li>
 * <li>{@code /datasources/AnywareList} - Anyware list</li>
 * <li>{@code /datasources/Anyware} - Anyware detail</li>
 * <li>{@code /datasources/Report} - Report tree</li>
 * <li>{@code /datasources/BatchDat2DirSrc} - Batch Dat-to-Directory source</li>
 * <li>{@code /datasources/BatchDat2DirSDR} - Batch Dat-to-Directory SDR</li>
 * <li>{@code /datasources/BatchDat2DirResult} - Batch Dat-to-Directory result</li>
 * <li>{@code /datasources/BatchTrntChkSDR} - Batch Torrent Check SDR</li>
 * <li>{@code /datasources/BatchTrntChkReportTree} - Batch Torrent Check report tree</li>
 * <li>{@code /datasources/BatchCompressorFR} - Batch Compressor file result</li>
 * </ul>
 * <p>
 * The response is written as a temporary XML file streamed back to the client via {@link TempFileInputStream}, ensuring efficient
 * handling of large result sets.
 * 
 * @author JRM Project
 * 
 * @version 1.0
 * 
 * @since 1.0
 * 
 * @see XMLRequest
 * @see XMLResponse
 * @see TempFileInputStream
 * @see WebSession
 */
public class DataSourceServlet extends HttpServlet {

    /**
     * Processes incoming POST requests containing XML data source queries.
     * <p>
     * Validates the request content length and content type ({@code text/xml}), then delegates to
     * {@link #processResponse(WebSession, HttpServletRequest, HttpServletResponse)} for URI-based routing. The resulting XML is
     * streamed back to the client with appropriate HTTP headers (content type, content length, status code).
     * <p>
     * HTTP status codes returned:
     * <ul>
     * <li>{@code 411 Length Required} - if content length is negative (long)</li>
     * <li>{@code 413 Request Entity Too Large} - if content length exceeds int range</li>
     * <li>{@code 200 OK} - successful response with XML body</li>
     * <li>{@code 204 No Content} - request processed but no data to return</li>
     * <li>{@code 500 Internal Server Error} - on I/O or unexpected errors</li>
     * </ul>
     * <p>
     * If the content length is zero or the content type is not {@code text/xml}, the request is silently ignored and the response
     * status remains at its default value.
     * 
     * @param req the HTTP servlet request containing the XML query body
     * @param resp the HTTP servlet response for writing the XML result
     * 
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs during request/response processing
     */
    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        try {
            if (req.getContentLengthLong() < 0)
                resp.setStatus(HttpServletResponse.SC_LENGTH_REQUIRED);
            else if (req.getContentLength() < 0)
                resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            else if (req.getContentLength() > 0 && req.getContentType().equalsIgnoreCase("text/xml")) {
                final var response = processResponse((WebSession) req.getSession().getAttribute("session"), req, resp); //NOSONAR
                if (response != null) {
                    resp.setContentType("text/xml");
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setContentLengthLong(response.getLength());
                    IOUtils.copy(response, resp.getOutputStream());
                } else if (resp.getStatus() == HttpServletResponse.SC_OK)
                    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        } catch (final XMLStreamException e) {
            Log.err(e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (final IOException _) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (final Exception e) {
            Log.err(e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Routes the XML request to the appropriate {@link XMLResponse} handler based on the request URI.
     * <p>
     * This method reads the request body as a buffered input stream, constructs an {@link XMLRequest}, and uses a switch expression
     * on the request URI to instantiate the correct {@code XMLResponse} subclass. The response is processed and returned as a
     * {@link TempFileInputStream} for efficient streaming back to the client.
     * <p>
     * If the request URI does not match any known data source endpoint, the HTTP response status is set to
     * {@code 501 Not Implemented}.
     * 
     * @param sess the current web session associated with the request
     * @param req the HTTP servlet request providing the input stream and URI
     * @param resp the HTTP servlet response for setting error status codes
     * 
     * @return a {@link TempFileInputStream} containing the XML response, or {@code null} if the URI is unrecognized or the response
     *         is empty
     * 
     * @throws IOException if an I/O error occurs while reading the request body
     * @throws XMLStreamException if an error occurs during XML stream processing
     */
    protected TempFileInputStream processResponse(final WebSession sess, final HttpServletRequest req, final HttpServletResponse resp) throws IOException, XMLStreamException {
        final int bodylen = req.getContentLength();
        try (final var in = new BufferedInputStream(req.getInputStream());
                final var response = getXMLResponse(sess, req, bodylen, in);) {
            if (response != null)
                return response.processRequest();
            resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
        return null;
    }

    /**
     * Returns an instance of the appropriate {@link XMLResponse} handler based on the request URI.
     * <p>
     * This method uses a switch expression to match the request URI against known data source endpoints and instantiate the
     * corresponding {@code XMLResponse} subclass. If the URI is unrecognized, the method returns {@code null}.
     * 
     * @param sess the current web session associated with the request
     * @param req the HTTP servlet request providing the input stream and URI
     * @param bodylen the length of the request body in bytes
     * @param in the buffered input stream from which the request body can be read
     * 
     * @return an instance of the appropriate {@code XMLResponse} handler, or {@code null} if the URI is unrecognized
     * 
     * @throws IOException if an I/O error occurs while reading the request body
     * @throws XMLStreamException if an error occurs during XML stream processing
     */
    private XMLResponse getXMLResponse(final WebSession sess, final HttpServletRequest req, final int bodylen, final BufferedInputStream in) throws IOException, XMLStreamException {
        return switch (req.getRequestURI()) {
            case "/datasources/profilesTree" -> new ProfilesTreeXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/profilesList" -> new ProfilesListXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/remoteFileChooser" -> new RemoteFileChooserXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/remoteRootChooser" -> new RemoteRootChooserXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/CatVer" -> new CatVerXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/NPlayers" -> new NPlayersXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/AnywareListList" -> new AnywareListListXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/AnywareList" -> new AnywareListXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/Anyware" -> new AnywareXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/Report" -> new ReportTreeXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/BatchDat2DirSrc" -> new BatchDat2DirSrcXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/BatchDat2DirSDR" -> new BatchDat2DirSDRXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/BatchDat2DirResult" -> new BatchDat2DirResultXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/BatchTrntChkSDR" -> new BatchTrntChkSDRXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/BatchTrntChkReportTree" -> new BatchTrntChkReportTreeXMLResponse(new XMLRequest(sess, in, bodylen));
            case "/datasources/BatchCompressorFR" -> new BatchCompressorFRXMLResponse(new XMLRequest(sess, in, bodylen));
            default -> null;
        };
    }
}
