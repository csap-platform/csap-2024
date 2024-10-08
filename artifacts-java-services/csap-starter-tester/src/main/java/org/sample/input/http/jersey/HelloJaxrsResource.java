package org.sample.input.http.jersey;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Straight JAXRS without any Spring Wiring. Note that the paths are relative to
 * the path in the jersey init param
 *
 * @author pnightin
 * @see <a href=
 * "http://jersey.java.net/nonav/documentation/latest/user-guide.html#d4e1847">
 * Jersey Spring Docs </a>
 */

@Component
@Path ( "/helloworld" )
public class HelloJaxrsResource {
    protected final Log logger = LogFactory.getLog( getClass( ) );

    // The Java method will process HTTP GET requests
    @GET
    // The Java method will produce content identified by the MIME Media
    // type "text/plain"
    @Produces ( "text/plain" )
    public String getClichedMessage( @Context HttpServletRequest request ) {

        logger.info( "Simple hello" );
        // Return some cliched textual content
        return "Hello from host: " + HOST_NAME
                + "\n\t request: " + request.getRequestURL( )
                + "\n\t " + LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) );

    }

    static String HOST_NAME = "notFound";

    static {

        try {

            HOST_NAME = InetAddress.getLocalHost( ).getHostName( );

        } catch ( UnknownHostException e ) {

            HOST_NAME = "HOST_LOOKUP_ERROR";

        }

    }
}
