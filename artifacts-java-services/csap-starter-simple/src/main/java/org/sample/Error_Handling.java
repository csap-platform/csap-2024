package org.sample;

import com.fasterxml.jackson.core.JsonParseException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.csap.alerts.CsapGlobalId;
import org.csap.helpers.CSAP;
import org.csap.integations.micrometer.CsapMeterUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@ControllerAdvice
public class Error_Handling {

    final Logger logger = LoggerFactory.getLogger( getClass( ) );

    @Autowired
    CsapMeterUtilities metricUtilities;

    @ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Exception during processing, examine server Logs" )
    @ExceptionHandler ( Exception.class )
    public void defaultHandler(
            HttpServletRequest request,
            Exception e
    ) {

        commonHandling( request, e );

    }

    private void commonHandling(
            HttpServletRequest request,
            Exception e
    ) {

        request.setAttribute( "csapFiltered", CSAP.buildCsapStack( e ) );
        logger.warn( "{}: {}", request.getRequestURI( ), CSAP.buildCsapStack( e ) );
        logger.debug( "Full exception", e );
        metricUtilities.incrementCounter( CsapGlobalId.EXCEPTION.id );

    }

    @ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Exception during processing, examine server Logs" )
    @ExceptionHandler ( NullPointerException.class )
    public void handleNullPointer(
            HttpServletRequest request,
            Exception e
    ) {

        commonHandling( request, e );

    }

    @ResponseStatus ( value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Exception during processing, examine server Logs" )
    @ExceptionHandler ( JsonParseException.class )
    public void handleJsonParsing(
            HttpServletRequest request,
            Exception e
    ) {

        commonHandling( request, e );

    }

    // ClientAbort which extends ioexception cannot have response written cannot
    // have a response written
    @ExceptionHandler ( IOException.class )
    public void handleIOException(
            HttpServletRequest request,
            Exception e,
            HttpServletResponse response
    ) {

        String stackFrames = ExceptionUtils.getStackTrace( e );

        if ( stackFrames.contains( "ClientAbortException" ) ) {

            logger.info( "ClientAbortException found: " + e.getMessage( ) );

        } else {

            commonHandling( request, e );

            try {

                response.setStatus( HttpStatus.INTERNAL_SERVER_ERROR.value( ) );
                response.getWriter( )
                        .print( HttpStatus.INTERNAL_SERVER_ERROR.value( )
                                + " : Exception during processing, examine server Logs" );

            } catch ( IOException e1 ) {

                // TODO Auto-generated catch block
                e1.printStackTrace( );

            }

        }

    }

}
