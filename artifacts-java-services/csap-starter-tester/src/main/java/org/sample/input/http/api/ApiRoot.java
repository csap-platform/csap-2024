package org.sample.input.http.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Timer;
import org.csap.helpers.CSAP;
import org.csap.integations.micrometer.CsapMeterUtilities;
import org.sample.Csap_Tester_Application;
import org.sample.heap.HeapTester;
import org.sample.heap.MetaspaceClassCreator;
import org.sample.jpa.DemoManager;
import org.sample.heap.MetaspaceLeakerClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
//import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
@RequestMapping ( Csap_Tester_Application.API_URL )
public class ApiRoot {

    final Logger logger = LoggerFactory.getLogger( getClass( ) );

    DemoManager demoDataService;

    @Inject
    public ApiRoot( DemoManager demoDataService ) {

        logger.info( " ===== Best Practice: use constructor injection for dependencies" );
        this.demoDataService = demoDataService;

    }

    @RequestMapping ( method = RequestMethod.GET )
    public ModelAndView get( ) {

        logger.info( "Got help" );
        return new ModelAndView( "redirect:/api/help" );

    }

    @RequestMapping ( "/help" )
    public ModelAndView help( ) {

        logger.info( "Got help" );
        return new ModelAndView( "api/help" );

    }

    @Lazy
    @Autowired
    CsapMeterUtilities metricUtilities;
    private ObjectMapper jacksonMapper = new ObjectMapper( );

    String SEARCH_PACKAGE = ""; // com, org.sample, ...
    final String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + SEARCH_PACKAGE + "/**/*.class";
    ResourceLoader RESOURCE_LOADER = new DefaultResourceLoader(ApiRoot.class.getClassLoader());
    @GetMapping("/native-memory/run")
    public ObjectNode javaNativeMemory(
            int threads,
            int iterations) {

        logger.info("running using: {} threads  and invoking each: {} iterations", threads, iterations);

        ObjectNode report = jacksonMapper.createObjectNode();
        report.put("started", LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss")));
        report.put("duration", -1);

        Timer.Sample theTimer = metricUtilities.startTimer();


        try {
            Resource[] resources = new PathMatchingResourcePatternResolver(RESOURCE_LOADER).getResources(packageSearchPath);
            int loaded = resources.length;

            // isReadable is expensive
            //  - triggering deep call stack to zip.inflate,
            //  - allocates libz.so native memory, and leaks on zing ( thread concurrency )
            ForkJoinPool customThreadPool = new ForkJoinPool(threads);
            List<Integer> listOfNumbers = IntStream.range(0, threads)
                    .boxed()
                    .collect(Collectors.toList());

            customThreadPool.submit(() ->
                    listOfNumbers.parallelStream().forEach(number -> {

                        IntStream.range(0, iterations).forEach(count -> {
                            logger.info("started: {} , name: {}", count, Thread.currentThread().getName());
                            for (final Resource resource : resources) {
                                if (resource.isReadable()) {
                                    // do nothing
                                }
                            }
                            logger.info("completed: {} , name: {}", count, Thread.currentThread().getName());
                        });
                    })
            );

            customThreadPool.shutdown();
            customThreadPool.awaitTermination(2, TimeUnit.MINUTES);
            report.put("resourcesLocated", loaded);
            report.put("totalChecked", loaded * iterations * threads);

        } catch (final Exception e) {
            logger.warn( "Failure while invoking resource.isReadable() {}", CSAP.buildCsapStack(e)) ;
        }
        long timeNanos = metricUtilities.stopTimer(theTimer, "csap.heap-tester.consume-heap");
        report.put("duration", CSAP.autoFormatNanos(timeNanos));

        doGc();
        long freeBytes = Runtime.getRuntime().freeMemory();

        report.put("heap-post-gc", CSAP.printBytesWithUnits(freeBytes));
        return report;
    }

    List<Object> classesCache = new ArrayList<>();
    @GetMapping ( "/metaSpace/add"  )
    public ObjectNode javaMetaSpaceAllocate (
            int numberOfClasses ) {

        logger.info( "creating : {} classes  ", numberOfClasses ) ;

        ObjectNode report = jacksonMapper.createObjectNode() ;

        MetaspaceClassCreator classCreator = new MetaspaceClassCreator();

        int numberLeaked = 0 ;
        while( numberLeaked < numberOfClasses ) {
            // create a new class and store its reference into the classesCache
            Class leakedClass = classCreator.createClass();
            classesCache.add(leakedClass);

            numberLeaked++ ;

            //Print name of class loaded
            if ( numberLeaked % 1000 == 0 ) {

                System.out.println( "count: " + numberLeaked + "\t" + leakedClass.getName( ) );

            }

        }

        report.put( "timestamp", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "hh:mm:ss" ) ) ) ;
        report.put( "currentSize", classesCache.size() ) ;
        return report ;
    }


    void doGc( )  {

        logger.warn( "performing gc" ) ;

        try {
            TimeUnit.SECONDS.sleep( 1 );
            System.gc();
            TimeUnit.SECONDS.sleep( 1 );
            System.gc();
            TimeUnit.SECONDS.sleep( 1 );
        } catch ( InterruptedException e ) {
            logger.warn( "{}", CSAP.buildCsapStack( e ) );
        }
    }

    @GetMapping  (  "/garbageCollection"  )
    public ObjectNode garbageCollection () {


        doGc() ;

        ObjectNode report = jacksonMapper.createObjectNode() ;
        report.put( "timestamp", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "hh:mm:ss" ) ) ) ;
        report.put( "currentSize", classesCache.size() ) ;
        return report ;
    }

    @GetMapping  (  "/metaSpace/clear"  )
    public ObjectNode javaMetaSpaceClear () {

        logger.warn( "clearing allocated classes" ) ;

        classesCache.clear( );

        ObjectNode report = jacksonMapper.createObjectNode() ;
        report.put( "timestamp", LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "hh:mm:ss" ) ) ) ;
        report.put( "currentSize", classesCache.size() ) ;
        return report ;
    }


    //
    // Heap tester
    //

    // static List<String> leakStringList = new ArrayList<String>();
    Random randomGenerator = new Random( ) ;


    @GetMapping ( "/heap/allocate"  )
    public ObjectNode leakMemory (
            @RequestParam ( value = "mb" , defaultValue = "0" ) int mbToLeak ,
            @RequestParam ( value = "kb" , defaultValue = "0" ) int kbToLeak ,
            @RequestParam ( value = "classesToLeak" , defaultValue = "0" ) int metaSpaceClassToLeak ,
            @RequestParam ( value = "longAndShort", defaultValue = "false" ) boolean doLongAndShortAllocations ,
            @RequestParam ( value = "objects", defaultValue = "false" ) boolean useJsonObjectGraphs ) {

        ObjectNode report = jacksonMapper.createObjectNode() ;
        report.put( "result",
                heapTester.consumeHeap(
                        mbToLeak, kbToLeak,
                        doLongAndShortAllocations,
                        useJsonObjectGraphs,
                        metaSpaceClassToLeak )  ) ;

        updateCurrentAllocations( report );
        return report ;


    }

    private void updateCurrentAllocations( ObjectNode report ) {
        report.put("generalStringSize", heapTester.getLeakStringSize()) ;
        report.put("shorterStringSize", heapTester.getShorterStringSize()) ;
        report.put("longerStringSize", heapTester.getLongerStringSize()) ;
        report.put("shortLivedObjects", heapTester.getShortLivedSize()) ;
        report.put("longLivedObjects", heapTester.getLongLivedSize()) ;
        report.put("shortLivedClasses", heapTester.getShorterClassSize()) ;
        report.put("longLivedClasses", heapTester.getLongerClassSize()) ;
    }


//	private static volatile ArrayList<SoftReference<String>> softReferences = new ArrayList<>( ) ;

    @GetMapping ( "/heap/free"  )
    public ObjectNode freeMemory ( ) {

        ObjectNode report = jacksonMapper.createObjectNode() ;

        report.put( "resultLong", heapTester.clearLongHeapReferences( )  ) ;
        report.put( "resultShort", heapTester.clearShortHeapReferences( )  ) ;
        updateCurrentAllocations( report );

        return report ;

    }
    @GetMapping ( "/heap/refresh"  )
    public ObjectNode refreshCounts ( ) {

        ObjectNode report = jacksonMapper.createObjectNode() ;
        updateCurrentAllocations( report );

        return report ;

    }



    @Autowired(required = false)
    HeapTester heapTester ;
    @GetMapping ( "/heap/test/refresh"  )
    public ObjectNode heapTestRefresh ( ) {

        ObjectNode report = jacksonMapper.createObjectNode( ) ;
        addHeapTesterObjectSettings( report );
        return report ;

    }

    private void addHeapTesterObjectSettings( ObjectNode report ) {
        report.put( "isHeapTestEnabled", heapTester.isCsapTestJsonObjects() ) ;
        report.put( "objectsToAllocate", heapTester.getCsapTestAllocKb() ) ;
        report.put( "classesToAllocate", heapTester.getCsapTestAllocMetaspaceCount() ) ;
        report.put( "resetInterval", heapTester.getCsapTestAllocCount() ) ;
    }

    @GetMapping ( "/heap/test/update"  )
    public ObjectNode heapTestUpdate ( Integer objectsToAllocate, Integer resetInterval, Integer classesToAllocate) {

        logger.info( CSAP.buildDescription( "updating",
                "objectsToAllocate", objectsToAllocate,
                "resetInterval", resetInterval)  );
        ObjectNode report = jacksonMapper.createObjectNode( ) ;
        heapTester.setCsapTestAllocCount( resetInterval );
        heapTester.setCsapTestAllocKb( objectsToAllocate );
        heapTester.setCsapTestAllocMetaspaceCount( classesToAllocate );

        report.put( "updated", true ) ;
        addHeapTesterObjectSettings( report );
        report.put( "summary", heapTester.getSummary() ) ;
        return report ;

    }



    @RequestMapping ( value = "/helloJson")
    public ObjectNode helloJson( ) {

        ObjectNode resultNode = jacksonMapper.createObjectNode( );
        resultNode.put( "message", "Hello" );
        return resultNode;

    }

    @Cacheable ( Csap_Tester_Application.SIMPLE_CACHE_EXAMPLE )
    @RequestMapping ( value = "/simpleCacheExample" )
    public ObjectNode simpleCached( @RequestParam ( value = "key", defaultValue = "peter" ) String key ) {

        ObjectNode resultNode = jacksonMapper.createObjectNode( );
        resultNode.put( "message",
                "Sample has max entries 3. Change request param a few times to observer cache eviction" );
        resultNode.put( "key", key );

        SimpleDateFormat formatter = new SimpleDateFormat( "MMM.d H:mm:ss" );
        resultNode.put( "timestamp", formatter.format( new Date( ) ) );
        return resultNode;

    }

    /**
     * More complex ehcache example - uses custom key - based on path and request
     * params, and excludes request param
     *
     * @param key
     * @param request
     * @return
     */

    @Cacheable ( value = Csap_Tester_Application.SIMPLE_CACHE_EXAMPLE, key = "{#path1, #path2, #key}" )
    @RequestMapping ( value = "/customKeyExample/{path1}/{path2}", produces = MediaType.APPLICATION_JSON_VALUE )
    public ObjectNode customKeyExample(
            @PathVariable ( "path1" ) String path1,
            @PathVariable ( "path2" ) String path2,
            @RequestParam ( value = "key", defaultValue = "peter" ) String key,
            HttpServletRequest request
    ) {

        logger.info( "Got here" );
        ObjectNode resultNode = jacksonMapper.createObjectNode( );
        resultNode.put( "message",
                "Sample has max entries 3. Change request param a few times to observer cache eviction" );
        resultNode.put( "key", key );
        resultNode.put( "path1", path1 );
        resultNode.put( "path2", path2 );

        SimpleDateFormat formatter = new SimpleDateFormat( "MMM.d H:mm:ss" );
        resultNode.put( "timestamp", formatter.format( new Date( ) ) );
        return resultNode;

    }

    @Cacheable ( Csap_Tester_Application.TIMEOUT_CACHE_EXAMPLE )
    @RequestMapping ( value = "/cacheWithTimeout", produces = MediaType.APPLICATION_JSON_VALUE )
    public ObjectNode cacheWithTimeout( ) {

        ObjectNode resultNode = jacksonMapper.createObjectNode( );
        resultNode.put( "message", "Cache has timeout eviction of ten seconds. Wait a few minutes and try again" );

        SimpleDateFormat formatter = new SimpleDateFormat( "MMM.d H:mm:ss" );
        resultNode.put( "timestamp", formatter.format( new Date( ) ) );
        return resultNode;

    }

    @RequestMapping ( value = "/showTestDataJson", produces = MediaType.APPLICATION_JSON_VALUE )
    public ObjectNode showTestDataJson(
            @RequestParam ( value = "filter", defaultValue = DemoManager.TEST_TOKEN ) String filter,
            @RequestParam ( value = "pageSize", defaultValue = "20", required = false ) int pageSize,
            @RequestParam ( value = "count", defaultValue = "1", required = false ) int count,
            HttpServletRequest request,
            HttpServletResponse response
    )
            throws Exception {

        logger.debug( "Getting Test data" );

        ObjectNode resultNode = null;

        if ( count == 1 ) {

            try {

                resultNode = demoDataService.showScheduleItemsWithFilter( filter, pageSize );

            } catch ( Exception e ) {

                logger.error( "Failed getting data: ", e );

            }

        } else {

            resultNode = jacksonMapper.createObjectNode( );
            int recordsFound = 0;
            ArrayNode timesNode = resultNode.arrayNode( );
            long totalStart = System.currentTimeMillis( );

            for ( int i = 0 ; i < count ; i++ ) {

                long start = System.currentTimeMillis( );
                ObjectNode items = demoDataService.showScheduleItemsWithFilter( filter, pageSize );
                recordsFound += items.path( "count" ).asInt( );
                timesNode.add( System.currentTimeMillis( ) - start );

            }

            resultNode.put( "totalTimeInSeconds", ( System.currentTimeMillis( ) - totalStart ) / 1000 );
            resultNode.put( "averageTimeInMillSeconds",
                    ( ( System.currentTimeMillis( ) - totalStart ) / count ) );

            resultNode.put( "recordsFound",
                    recordsFound );
            resultNode.set( "iterationInMs", timesNode );

        }

        return resultNode;

    }
}
