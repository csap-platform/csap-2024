package org.sample.heap;
//package com.workday.perf.input.http.ui.windows ;
//package com.workday.perf.heap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.csap.integations.micrometer.CsapMeterUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class HeapTester {

    final Logger logger = LoggerFactory.getLogger( getClass( ) );

    static volatile ArrayList<String> leakStringList = new ArrayList<String>( );
    static volatile ArrayList<String> shorterHeapStringReferences = new ArrayList<String>( );
    static volatile ArrayList<String> longHeapStringReference = new ArrayList<String>( );

    static volatile ArrayList<HeapTestContainer> shorterHeapObjectReferences = new ArrayList<>( );
    static volatile ArrayList<HeapTestContainer> longHeapObjectReference = new ArrayList<>( );

    private static volatile ArrayList<JsonNode> heapConsumptionObjects = new ArrayList<>( );

    String referenceJsonObjectAsString = "";

    StringBuilder oneMb = new StringBuilder( 1024 * 1025 );
    StringBuilder oneKb = new StringBuilder( 1025 );

    String objectGraphFilePath;

    //
    //
    //

    public int getCsapTestAllocKb( ) {
        return csapTestAllocKb;
    }

    public void setCsapTestAllocKb( int csapTestAllocKb ) {
        this.csapTestAllocKb = csapTestAllocKb;
    }

    public int getCsapTestAllocCount( ) {
        return csapTestAllocCount;
    }

    public void setCsapTestAllocCount( int csapTestAllocCount ) {
        this.csapTestAllocCount = csapTestAllocCount;
    }

    public boolean isCsapTestJsonObjects( ) {
        return csapTestJsonObjects;
    }

    public void setCsapTestJsonObjects( boolean csapTestJsonObjects ) {
        this.csapTestJsonObjects = csapTestJsonObjects;
    }

    int csapTestAllocKb = 0;
    int csapTestAllocCount = 0;
    int csapTestInitAllocGb = 0;
    int csapTestInitThreadCount = 3;
    boolean csapTestJsonObjects = false;


    int csapTestAllocMetaspaceCount = 0;
    volatile ArrayList<Object> shortClassesCache = new ArrayList<>( );
    volatile ArrayList<Object> longClassesCache = new ArrayList<>( );
    MetaspaceClassCreator classCreator = new MetaspaceClassCreator( );

    ObjectMapper jsonMapper;
    File dataJsonFile = null;

    public HeapTester( ObjectMapper jsonMapper ) {

        this.jsonMapper = jsonMapper;

        int charToInsert = 0;

        for ( int i = 0 ; i < 1024 ; i++ ) {

            oneKb.append( charToInsert++ );

            if ( charToInsert > 9 ) {

                charToInsert = 0;

            }

        }

        for ( int i = 0 ; i < 1024 ; i++ ) {

            oneMb.append( oneKb.toString( ) );

        }

        objectGraphFilePath = System.getProperty( "csapTestAllocFile", "/alloc-data-sample.json" );


        try {

            dataJsonFile = new File( getClass( ).getResource( objectGraphFilePath ).getPath( ) );
            referenceJsonObjectAsString = FileUtils.readFileToString( dataJsonFile );
            logger.info( "Loaded referenceJsonObjectAsString, size: {} characters", referenceJsonObjectAsString
                    .length( ) );

        } catch ( Exception e ) {

            logger.error( "Failed to load resource: {} in file: {}, reason: {}", objectGraphFilePath, dataJsonFile, CSAP.buildCsapStack( e ) );

        }

        String csapTestAllocKbString = System.getProperty( "csapTestAllocKb", "" );
        String csapTestAllocCountKString = System.getProperty( "csapTestAllocCountK", "" );
        String csapTestAllocJsonObjectGraphs = System.getProperty( "csapTestAllocJsonObjects", "" );
        String csapTestInitAllocGbString = System.getProperty( "csapTestInitAllocGb", Integer.toString(
                csapTestInitAllocGb ) );
        String csapTestInitThreadCountString = System.getProperty( "csapTestInitThreadCount", Integer.toString(
                csapTestInitThreadCount ) );

        String csapTestAllocMetaspaceCountString = System.getProperty( "csapTestAllocMetaspaceCount", "" );

        if ( StringUtils.isNotEmpty( csapTestAllocMetaspaceCountString ) ) {

            try {
                csapTestAllocMetaspaceCount = Integer.parseInt( csapTestAllocMetaspaceCountString );

            } catch ( Exception e ) {

                logger.warn( "Failed to parse -DcsapTestAllocMetaspaceCount {}", CSAP.buildCsapStack( e ) );

            }

        }


        if ( StringUtils.isNotEmpty( csapTestAllocKbString ) && csapTestAllocJsonObjectGraphs.toLowerCase( ).equals(
                "true" ) ) {

            csapTestJsonObjects = true;

        }

        if ( StringUtils.isNotEmpty( csapTestAllocKbString )
                && StringUtils.isNotEmpty( csapTestAllocCountKString ) ) {

            try {

                csapTestAllocKb = Integer.parseInt( csapTestAllocKbString );
                csapTestAllocCount = Integer.parseInt( csapTestAllocCountKString );
                csapTestInitAllocGb = Integer.parseInt( csapTestInitAllocGbString );
                csapTestInitThreadCount = Integer.parseInt( csapTestInitThreadCountString );

                if ( csapTestAllocCount > 0 ) {

                    csapTestAllocCount = csapTestAllocCount * 1000;

                } else {

                    csapTestAllocCount = Math.abs( csapTestAllocCount );

                }


            } catch ( Exception e ) {

                logger.warn( "Failed to parse -DcsapTestAllocKb {}", CSAP.buildCsapStack( e ) );

            }

        }

        logger.info( CsapApplication.testHeader( "{}" ), getSummary( ) );

    }

    public String getSummary( ) {
        return CSAP.buildDescription(
                "* Heap Allocation Settings",
                "csapTestAllocKb", csapTestAllocKb,
                "csapTestAllocCount", csapTestAllocCount,
                "csapTestInitAllocGb", csapTestInitAllocGb,
                "csapTestInitThreadCount", csapTestInitThreadCount,
                "csapTestJsonObjects", csapTestJsonObjects,
                "objectGraphFilePath", objectGraphFilePath,
                "dataJsonFile", dataJsonFile,
                "csapTestAllocMetaspaceCount", csapTestAllocMetaspaceCount );
    }

    public long getShortLivedSize( ) {
        return shorterHeapObjectReferences.size( );
    }

    public long getLongLivedSize( ) {
        return longHeapObjectReference.size( );
    }

    @PostConstruct
    public void initializeHeapAllocations( ) {

        if ( csapTestInitAllocGb > 0 ) {


            metricUtilities.addGauge( "csap.heap-tester.live.short-lived", this, HeapTester::getShortLivedSize );
            metricUtilities.addGauge( "csap.heap-tester.live.long-lived", this, HeapTester::getLongLivedSize );

            int heapAllocationPerThousand = 220; // 210 from jprofiler - but large heaps underestimates

            if ( objectGraphFilePath.contains( "large" ) ) {

                heapAllocationPerThousand = 450;

            }

            int totalObjectsToAllocate = csapTestInitAllocGb * 1000 * 1000 / heapAllocationPerThousand;
            int batchSize = 100;

            // let numBlocks

            logger.info( CSAP.buildDescription( "Heap Allocation Estimation",
                    "Json Raw Object", referenceJsonObjectAsString.length( ) + " characters",
                    "~heapAllocationPerThousand", heapAllocationPerThousand + " mb",
                    "requested", csapTestInitAllocGb + " gb",
                    "batchSize", batchSize,
                    "threadsToUse", csapTestInitThreadCount,
                    "totalObjectsToAllocate", totalObjectsToAllocate ) );

            if ( !CsapApplication.isCsapFolderSet( ) ) {

//				totalRuns = totalRuns / 10 ;
//				batchSize = batchSize / 10 ;
                csapTestInitThreadCount = 4;
                logger.info( CsapApplication.testHeader( "Desktop: {}" ),
                        CSAP.buildDescription( "Batch Settings",
                                "totalObjectsToAllocate", totalObjectsToAllocate,
                                "batchSize", batchSize,
                                "threadsToUse", csapTestInitThreadCount ) );

            }

            ExecutorCompletionService<Integer> fileTransferComplete;
            ExecutorService fileTransferService;
            BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )
                    .namingPattern( "CsapHeapLoader-%d" )
                    .daemon( true )
                    .priority( Thread.NORM_PRIORITY )
                    .build( );

            int jobCount = 0;
            fileTransferService = Executors.newFixedThreadPool( csapTestInitThreadCount, schedFactory );

            fileTransferComplete = new ExecutorCompletionService<Integer>( fileTransferService );

            // parallel allocations

            int objectsScheduledForAllocation = 0;

            while ( ( objectsScheduledForAllocation + batchSize ) < totalObjectsToAllocate ) {

                fileTransferComplete.submit( new HeapLoader( batchSize ) );
                objectsScheduledForAllocation += batchSize;

                jobCount++;

            }

            fileTransferComplete.submit( new HeapLoader( totalObjectsToAllocate - objectsScheduledForAllocation ) );
            jobCount++;

            try {

                int numPrinted = 0;
                int objectsAllocated = 0;

                for ( int jobIndex = 0 ; jobIndex < jobCount ; jobIndex++ ) {

                    Future<Integer> finishedJob = fileTransferComplete.take( );

                    objectsAllocated += finishedJob.get( );

                    if ( numPrinted++ % 15 == 0 ) {

                        System.out.print( "\n" );

                    }

                    System.out.print( "\t" + ( objectsAllocated ) );

                }

                System.out.print( "\n\n" );

                logger.info( CsapApplication.testHeader( "requesting gc" ) );
                System.gc( );

            } catch ( Exception e ) {

                logger.info( "Failed waiting for jobs to completed" );

            }

            fileTransferService.shutdownNow( );

        }

    }

    public class HeapLoader implements Callable<Integer> {

        int numToAdd = 0;

        public HeapLoader( int numToAdd ) {

            this.numToAdd = numToAdd;

        }

        @Override
        public Integer call( ) throws Exception {

            try {

                for ( int i = 0 ; i < numToAdd ; i++ ) {

                    JsonNode testObject = jsonMapper.readTree( referenceJsonObjectAsString );
                    heapConsumptionObjects.add( testObject );

                }

            } catch ( Exception e ) {

                logger.error( "Failed to load reference object: {}", CSAP.buildCsapStack( e ) );

            }

            return numToAdd;

        }

    }

    AtomicInteger allocCount = new AtomicInteger( 0 );
    AtomicInteger miniCount = new AtomicInteger( 0 );

    public void perform_heap_allocations( ) {

//		postConstruction( ) ;

        if ( csapTestAllocKb > 0 ) {

            boolean doShortAndLongHeapAllocations = true;

            consumeHeap( 0, csapTestAllocKb, doShortAndLongHeapAllocations, csapTestJsonObjects, csapTestAllocMetaspaceCount );

            int currentCount = allocCount.incrementAndGet( );

            if ( currentCount > csapTestAllocCount ) {

                Timer.Sample theTimer = metricUtilities.startTimer( );
                logger.info( clearLongHeapReferences( ) );
                allocCount.set( 0 );
                metricUtilities.stopTimer( theTimer, "csap.heap-tester.clear.long-lived" );

            }

            int miniCounter = miniCount.incrementAndGet( );

            if ( miniCounter > ( csapTestAllocCount / 3 ) ) {


                Timer.Sample theTimer = metricUtilities.startTimer( );
                logger.info( clearShortHeapReferences( ) );
                miniCount.set( 0 );
                metricUtilities.stopTimer( theTimer, "csap.heap-tester.clear.short-lived" );

            }

        }

    }

    public static class HeapTestContainer {
        Logger logger ;
        JsonNode node;
        public HeapTestContainer( Logger logger, JsonNode node) {
            this.logger = logger;
            this.node = node ;
        }

        // finalize added to trigger zing allocation counter
        public void finalize() {
            logger.debug( "Cleaning up") ;
        }
    }

    public int CHARS_IN_KB_UTF_16 = 512;
    @Lazy
    @Autowired
    CsapMeterUtilities metricUtilities;

    public String consumeHeap(
            int mbToLeak,
            int kbToLeak,
            boolean doLongAndShortAllocations,
            boolean useJsonObjectGraphs,
            int metaSpaceAllocationCount
    ) {

        StringBuilder result = new StringBuilder( "\n\nLeaked: " + mbToLeak + "Mb, " + kbToLeak + " Kb\n" );

        logger.info( "Leaking: {} mb {} Kb, doLongAndShortAllocations: {}, useJsonObjectGraphs: {}, classes: {}",
                mbToLeak, kbToLeak, doLongAndShortAllocations, useJsonObjectGraphs, metaSpaceAllocationCount );

        Timer.Sample theTimer = metricUtilities.startTimer( );
        try {

            if ( useJsonObjectGraphs ) {

                for ( int j = 0 ; j < metaSpaceAllocationCount ; j++ ) {
                    Class leakedClass = classCreator.createClass();
                    shortClassesCache.add(leakedClass);
                    metricUtilities.incrementCounter( "csap.heap-tester.consume-heap.metaspace.short-lived" );
                    longClassesCache.add(leakedClass);
                    metricUtilities.incrementCounter( "csap.heap-tester.consume-heap.metaspace.long-lived" );
                }

                for ( int j = 0 ; j < kbToLeak ; j++ ) {


                    // allocateMemoryToHeap( doLongAndShortAllocations, oneKb ) ;

                    var leakedJsonNode = jsonMapper.readTree( referenceJsonObjectAsString );
                    var leakedObjectWithFinalizer =  new HeapTestContainer(logger, leakedJsonNode) ;
                    shorterHeapObjectReferences.add( leakedObjectWithFinalizer );
                    metricUtilities.incrementCounter( "csap.heap-tester.consume-heap.json-object.short-lived" );

                    longHeapObjectReference.add( leakedObjectWithFinalizer  );
                    metricUtilities.incrementCounter( "csap.heap-tester.consume-heap.json-object.long-lived" );

                }

            } else {

                int numPrinted = 0;

                for ( int mbCount = 0 ; mbCount < mbToLeak ; mbCount++ ) {

                    // logger.info( "mb: {}", kb );

                    for ( int kbAllocation = 0 ; kbAllocation < 1024 ; kbAllocation++ ) {

                        String kb = RandomStringUtils.randomAlphanumeric( CHARS_IN_KB_UTF_16 );

                        // String kb = "xx" ;
                        if ( kbAllocation % 100 == 0 ) {

                            if ( numPrinted++ % 20 == 0 ) System.out.print( "\n\t" );
                            System.out.print( "\t" + kbAllocation );

                        }

                        allocateMemoryToHeap( kb, doLongAndShortAllocations );
                        metricUtilities.incrementCounter( "csap.heap-tester.consume-heap.string.long.kb" );

                    }
                    // allocateMemoryToHeap( doLongAndShortAllocations, oneMb ) ;

                }

                for ( int j = 0 ; j < kbToLeak ; j++ ) {

                    String kb = RandomStringUtils.randomAlphanumeric( CHARS_IN_KB_UTF_16 );
                    allocateMemoryToHeap( kb, doLongAndShortAllocations );

                    metricUtilities.incrementCounter( "csap.heap-tester.consume-heap.string.long.kb" );
//					allocateMemoryToHeap( doLongAndShortAllocations, oneKb ) ;

                }

            }

        } catch ( Exception e ) {

            logger.warn( "Failed to allocate objects: {}", CSAP.buildCsapStack( e ) );

        }

        metricUtilities.stopTimer( theTimer, "csap.heap-tester.consume-heap" );


        result.append( "Remember to clean up when done, current Items in List: " + leakStringList.size( ) );

        result.append( "\n===================\n\n" );

        return result.toString( );

    }

    private ReentrantLock testingHeapLock = new ReentrantLock( );

    private void allocateMemoryToHeap(
            String buffer,
            boolean includeShortAndLongHeapAllocations
    ) {

        String stringToLeak = buffer.toString( );

        if ( includeShortAndLongHeapAllocations ) {

//			if ( testingHeapLock.tryLock( ) ) {

//				softReferences.add( new SoftReference<String>( stringToLeak ) ) ;

            longHeapStringReference.add( stringToLeak );
            shorterHeapStringReferences.add( stringToLeak );

//			testingHeapLock.unlock( ) ;

//			}

        } else {

            leakStringList.add( stringToLeak );

        }

    }

    public String clearShortHeapReferences( ) {

        int numberOfStrings = shorterHeapStringReferences.size( );
        shorterHeapStringReferences = new ArrayList<String>( );

        int numberOfObjects = shorterHeapObjectReferences.size( );
        shorterHeapObjectReferences = new ArrayList<HeapTestContainer>( );

        int numberOfClasses = shortClassesCache.size( );
        shortClassesCache = new ArrayList<Object>( );

        return "**** Create new list, old list contained ****"
                + numberOfStrings + " Strings,"
                + numberOfObjects  + " Objects, and "
                + numberOfClasses + " Classes";

    }

    void tryToAllocateAllAvailableMemory( ) {

        try {

            final List<Object[]> allocations = new ArrayList<>( );
            int size;

            while ( ( size = ( int ) Runtime.getRuntime( ).freeMemory( ) ) > 0 ) {

                Object[] part = new Object[ Math.min( size, Integer.MAX_VALUE ) ];
                allocations.add( part );

            }

        } catch ( OutOfMemoryError e ) {

            System.out.println( "catch expected exception: " + e.getMessage( ) );

        }

    }

    public String clearLongHeapReferences( ) {

        StringBuilder result = new StringBuilder(
                "\n\n ===========================\n\n current items in List: " + leakStringList.size( ) );

        synchronized ( leakStringList ) {

            leakStringList.clear( );

        }

        synchronized ( longHeapStringReference ) {

            longHeapStringReference.clear( );

        }

        synchronized ( longHeapObjectReference ) {

            longHeapObjectReference.clear( );

        }



        synchronized ( longClassesCache ) {

            longClassesCache.clear( );

        }

        result.append( "\nList now contains: " + leakStringList.size( ) );
        result.append( "\n===================\n\n" );

        return result.toString( );

    }

    public long getLeakStringSize( ) {
        return leakStringList.size( );
    }

    public long getShorterStringSize( ) {
        return shorterHeapStringReferences.size( );
    }

    public long getLongerStringSize( ) {
        return longHeapStringReference.size( );
    }


    public long getLongerClassSize( ) {
        return longClassesCache.size( );
    }

    public long getShorterClassSize( ) {
        return shortClassesCache.size( );
    }


    public int getCsapTestAllocMetaspaceCount( ) {
        return csapTestAllocMetaspaceCount;
    }

    public void setCsapTestAllocMetaspaceCount( int csapTestAllocMetaspaceCount ) {
        this.csapTestAllocMetaspaceCount = csapTestAllocMetaspaceCount;
    }
}
