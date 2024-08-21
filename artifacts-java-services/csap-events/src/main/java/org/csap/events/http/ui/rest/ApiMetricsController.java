package org.csap.events.http.ui.rest;

import jakarta.inject.Inject;
import org.csap.docs.CsapDoc;
import org.csap.events.CsapEventsApplication;
import org.csap.events.db.MetricsDataHandler;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.csap.integations.CsapInformation;
import org.csap.integations.micrometer.CsapMeterUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

@RestController
@RequestMapping ( CsapEventsApplication.METRICS_API )
@CsapDoc ( title = "CSAP Performance Metrics API", type = CsapDoc.PUBLIC, notes = {
        "Provides performance data via JSONP to various UIS",
        "<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>"
} )
public class ApiMetricsController {

    final Logger logger = LoggerFactory.getLogger( getClass( ) );

    @Autowired
    CsapMeterUtilities metricUtilities;

    @Autowired
    CsapInformation csapInfo;

    @Autowired
    CsapEventsApplication csapEventsApplication;

    @Inject
    private MetricsDataHandler metricsDataHandler;

    @GetMapping ( value = "/{hostName}/{id}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE )
    @CrossOrigin
    @CsapDoc ( notes = "Time series performance data suitable for graphing", linkTests = {
            "os-process30Second",
            "host300Second",
            "os-process300Second",
            "os-process-csap-agent300Second",
            "data300Second",
            "java300Second",
            "application-csap-agent300Second",
            "os-process30Second:jsonp"
    }, linkGetParams = {
            "hostName=some-host-name,id=os-process_30,numberOfDays=1",
            "hostName=some-host-name,id=host_300,numberOfDays=1",
            "hostName=some-host-name,id=service_300,numberOfDays=1",
            "hostName=some-host-name,serviceName=csap-agent,id=os-process_300,numberOfDays=1",
            "hostName=some-host-name,serviceName=data,id=os-process_300,numberOfDays=1",
            "hostName=some-host-name,id=java_300,numberOfDays=1",
            "hostName=some-host-name,id=application_csap-agent_300,numberOfDays=1,serviceName=CsAgent",
            "hostName=some-host-name,id=os-process_30,numberOfDays=1,callback=myFunctionCall"
    }, produces = {
            MediaType.APPLICATION_JSON_VALUE
    } )
    public String buildMetricsReport(
            @PathVariable ( value = "hostName" ) String hostName,
            @PathVariable ( value = "id" ) String collectionSetId,
            @RequestParam ( value = "numberOfDays", defaultValue = "1" ) Integer numberOfDaysToRetrieve,
            @RequestParam ( value = "dateOffSet", defaultValue = "0" ) Integer numDaysOffsetFromToday,
            @RequestParam ( value = "serviceName", defaultValue = "" ) String[] serviceNameArray,
            @RequestParam ( value = "callback", defaultValue = "false" ) String callback,
            @RequestParam ( value = "hosts", defaultValue = "" ) String[] hosts,
            @RequestParam ( value = "bucketSize", defaultValue = "1" ) int bucketSize,
            @RequestParam ( value = "bucketSpacing", defaultValue = "0" ) int bucketSpacing,
            @RequestParam ( value = "appId", defaultValue = "null" ) String appId,
            @RequestParam ( value = "life", required = false ) String life,
            @RequestParam ( value = "showDaysFrom", defaultValue = "false" ) boolean showDaysFrom,
            @RequestParam ( value = "padLatest", defaultValue = "true" ) boolean padLatest
    )
            throws IOException {

        if ( logger.isDebugEnabled( ) ) {
            logger.debug( CSAP.buildDescription( "Metrics Report Request",
                    "hostName", hostName,
                    "appId", appId,
                    "life", life,
                    "showDaysFrom", showDaysFrom,
                    "padLatest", padLatest,
                    "collectionSetId", collectionSetId,
                    "numberOfDaysToRetrieve", numberOfDaysToRetrieve,
                    "numDaysOffsetFromToday", numDaysOffsetFromToday,
                    "serviceNameArray", List.of( serviceNameArray ),
                    "hosts", List.of( hosts ),
                    "bucketSize", bucketSize,
                    "bucketSpacing", bucketSpacing
            ) );
        }

        if ( ( collectionSetId.endsWith( "_30" ) && numberOfDaysToRetrieve > 7 )
                || ( collectionSetId.endsWith( "_300" ) && numberOfDaysToRetrieve > 20 )
                || ( collectionSetId.endsWith( "_3600" ) && numberOfDaysToRetrieve > 90 ) ) {

            logger.warn( "Large dataset being loaded for host: {}, Application: {}, Data: {}, Number Of days: {} ",
                    hostName, appId, collectionSetId, numberOfDaysToRetrieve.toString( ) );

        }

        if ( collectionSetId.startsWith( "app-" ) ) {

            collectionSetId = "jmx" + collectionSetId.substring( 4 );

        }

        if ( collectionSetId.startsWith( CsapApplication.COLLECTION_APPLICATION ) ) {

            var fields = collectionSetId.split( "_" );

            if ( fields.length == 2 ) {

                // convenience method for building the collection id
                collectionSetId = fields[ 0 ] + "-" + serviceNameArray[ 0 ] + "_" + fields[ 1 ];

            }

        }

        var timerAll = metricUtilities.startTimer( );

        var timerByResourceType = metricUtilities.startTimer( );

        var timerByAppId = metricUtilities.startTimer( );
        var timerByLifecycle = metricUtilities.startTimer( );

        logger.debug( "In metrics controller" );

        // used in .js and .html to get current host value
        if ( hostName.equals( "some-host-name" ) ) {

            if ( !CsapApplication.isCsapFolderSet( ) ) {

                hostName = csapEventsApplication.getDefaultHostForTest( ); //"csap-dev01" ;
                logger.info( CsapApplication.testHeader( "some-host-name specified, replacing with: {}" ), hostName );

            } else {

                hostName = csapInfo.getHostShortName( );

            }

            logger.debug( "some-host-name specified, replacing with: {}", hostName );
        }

        // build unique cache key for time interval
        Calendar dayBeingRetrieved = Calendar.getInstance( );
        dayBeingRetrieved.add( Calendar.DAY_OF_YEAR, -( numDaysOffsetFromToday ) );
        int reportDayOfYear = dayBeingRetrieved.get( Calendar.DAY_OF_YEAR );
        int year = dayBeingRetrieved.get( Calendar.YEAR );

        String uniqueCacheKey = Arrays.asList( serviceNameArray ).toString( ) + year + "-" + reportDayOfYear;

        String graphReport;

        if ( bucketSpacing > 0 ) {

            graphReport = metricsDataHandler
                    .buildMetricsReportNoCache(
                            hostName, collectionSetId,
                            uniqueCacheKey,
                            numberOfDaysToRetrieve, numDaysOffsetFromToday,
                            bucketSize, bucketSpacing,
                            serviceNameArray, appId, life, showDaysFrom, padLatest );

        } else if ( numDaysOffsetFromToday == 0 ) {

            // note current day cache is getting updated every 15/30 minutes
            graphReport = metricsDataHandler
                    .buildPerformanceGraphDataForToday(
                            hostName, collectionSetId,
                            uniqueCacheKey,
                            numberOfDaysToRetrieve, numDaysOffsetFromToday,
                            bucketSize, bucketSpacing,
                            serviceNameArray, appId, life, showDaysFrom, padLatest );

        } else {

            // We keep historical data around much longer

            graphReport = metricsDataHandler
                    .buildPerformanceGraphData(
                            hostName, collectionSetId,
                            uniqueCacheKey,
                            numberOfDaysToRetrieve, numDaysOffsetFromToday,
                            bucketSize, bucketSpacing,
                            serviceNameArray, appId, life, showDaysFrom, padLatest );

        }

        metricUtilities.stopTimer( timerByResourceType, "csap.performanceData.type." + collectionSetId );
        metricUtilities.stopTimer( timerByAppId, "csap.performanceData.appid." + appId );
        metricUtilities.stopTimer( timerByLifecycle, "csap.performanceData.life." + life );
        metricUtilities.stopTimer( timerAll, "csap.performanceData.ALL" );

        return graphReport;

    }

}
