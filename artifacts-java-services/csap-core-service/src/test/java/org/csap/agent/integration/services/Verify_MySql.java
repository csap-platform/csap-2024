package org.csap.agent.integration.services;

import org.csap.agent.CsapBareTest;
import org.csap.agent.CsapThinNoProfile;
import org.csap.agent.DbPerformanceCollector;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.PostConstruct;


//@Disabled
@Tag ( "mysql" )
@SpringBootTest ( classes = Verify_MySql.BareBoot.class )
@CsapBareTest.ActiveProfiles_JunitDb
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
class Verify_MySql {

    static {

        // includes logging initialization - so must occur very early
        CsapApplication.initialize( CSAP.padNoLine( "Loading" ) + CsapThinNoProfile.class.getName( ) );

    }

    final static Logger logger = LoggerFactory.getLogger( Verify_MySql.class );


    @BeforeAll
    void beforeAll( )
            throws Exception {

        logger.info( CsapApplication.testHeader( ) );

    }


    @Autowired
    BareBoot bareBoot;


    @Test
    void verifyPerformanceCollection( ) {

        logger.info( CsapApplication.testHeader( ) );

        logger.info( "Current Performance: \n{} ",
                bareBoot.getDbPerformanceCollector( ).collectPerformance( ).toString( ) );


    }

    @Test
    void verifyLocalPerformanceCollection( ) {

        logger.info( CsapApplication.testHeader( ) );

        bareBoot.getDbPerformanceCollector( ).setUsername( "root" );
        bareBoot.getDbPerformanceCollector( ).setPassword( "nyw" );

        logger.info( "updated user and pass: {}", bareBoot.getDbPerformanceCollector( ).toString( ) );
        logger.info( "Current Performance: \n{} ",
                bareBoot.getDbPerformanceCollector( ).collectPerformance( ).toString( ) );


    }


    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ConfigurationProperties ( prefix = "csap-core" )
    public static class BareBoot {


        private DbPerformanceCollector dbPerformanceCollector;


        @PostConstruct
        public void postConstruction( ) {

            logger.info( CsapApplication.testHeader( ) );

        }


        public DbPerformanceCollector getDbPerformanceCollector( ) {

            return dbPerformanceCollector;

        }

        public void setDbPerformanceCollector( DbPerformanceCollector dbPerformanceCollector ) {

            this.dbPerformanceCollector = dbPerformanceCollector;

        }

    }


}
