package test.scenario_2_beans;

import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Simple_Tests {

    Logger logger = LoggerFactory.getLogger( getClass( ) );


    static {

        CsapApplication.initialize( "Test Setup Complete" ) ;

    }

    @Test
    void verifyStackPrintouts( ) {

        logger.info( CsapApplication.testHeader() ) ;

        try {
            throw new Exception( "demo stack" );
        } catch ( Exception e ) {

            logger.info( "log4j stack", e );

            logger.info( "csap stack: {}", CSAP.buildCsapStack( e ) );

            logger.info( "full stack: {}", CSAP.buildFilteredStack( e, "" ) );


        }
    }
    @Disabled
    @Test
    void ignoredSample( ) {

        logger.info( CsapApplication.testHeader( "12" ) ) ;
    }
}
