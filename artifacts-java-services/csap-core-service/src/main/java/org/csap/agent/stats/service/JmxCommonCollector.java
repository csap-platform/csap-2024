package org.csap.agent.stats.service;

import java.io.IOException ;
import java.util.HashMap ;
import java.util.Map ;
import java.util.Set ;

import javax.management.AttributeNotFoundException ;
import javax.management.InstanceNotFoundException ;
import javax.management.MBeanException ;
import javax.management.MBeanServerConnection ;
import javax.management.MalformedObjectNameException ;
import javax.management.ObjectInstance ;
import javax.management.ObjectName ;
import javax.management.ReflectionException ;
import javax.management.openmbean.CompositeData ;
import javax.management.openmbean.CompositeDataSupport ;

import org.csap.agent.CsapApis ;
import org.csap.agent.model.Application;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class JmxCommonCollector {

	final Logger logger = LoggerFactory.getLogger( JmxCommonCollector.class ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	/**
	 *
	 * Key Attributes are collected from every JVM unless configuration explicity
	 * disables for service
	 *
	 *
	 */
	public void collect ( MBeanServerConnection mbeanConn , ServiceCollectionResults collectionResults )
		throws Exception {

		String serviceId = collectionResults
				.getServiceInstance( )
				.getName( ) ;
		// .getServiceName_Port() ;

		collectJavaCoreMetrics( mbeanConn, collectionResults, serviceId ) ;

		collectJavaHeapMetrics( mbeanConn, collectionResults, serviceId ) ;

		if ( CsapApis.getInstance( ).application( ).environmentSettings( ).isIncludeAltJavaAttributes( ) ) {

			collectAltMetrics( mbeanConn, collectionResults, serviceId ) ;

		}

		if ( collectionResults
				.getServiceInstance( )
				.is_java_application_server( ) ) {

			collectTomcatConnections( mbeanConn, collectionResults, serviceId ) ;

			collectTomcatRequestData( mbeanConn, collectionResults, serviceId ) ;

		}

	}

	Map<String, Long> lastOpenFiles = new HashMap<>( ) ;
	Map<String, Integer> lastClassesLoaded = new HashMap<>( );
	Map<String, Long> lastClassesUnLoaded = new HashMap<>( );

	private void collectJavaCoreMetrics (
											MBeanServerConnection mbeanConn ,
											ServiceCollectionResults collectionResults ,
											String serviceId )
		throws Exception {

		// cpu
		// http://docs.oracle.com/javase/7/docs/jre/api/management/extension/com/sun/management/OperatingSystemMXBean.html
		String mbeanName = "java.lang:type=OperatingSystem" ;

		//
		// Seems to be race condition when same ProcessCpuLoad is
		// pulled from the same connection.
		String attributeName = "ProcessCpuLoad" ;
		Double cpuDouble ;

		try {

			cpuDouble = (Double) mbeanConn.getAttribute( new ObjectName( mbeanName ), attributeName ) ;

			collectionResults.setCpuPercent( Math.round( cpuDouble * 100 ) ) ;

			logger.debug( "cpuDouble: {}", cpuDouble ) ;

		} catch ( Exception e ) {

			logger.debug( "Failed to get ProcessCpuLoad", e ) ;

		}

		// **************** Open Files
		// logger.error("\n\n\t ************** Sleeping for testing JMX timeouts
		// ***********");
		// Thread.sleep(5000); // For testing timeouts only
		try {

			mbeanName = "java.lang:type=OperatingSystem" ;
			attributeName = "OpenFileDescriptorCount" ;

			var openFileCount = (Long) mbeanConn.getAttribute(
					new ObjectName( mbeanName ), attributeName ) ;
			collectionResults.setOpenFiles( openFileCount ) ;

			var deltaOpenFiles = 0l ;
			if ( lastOpenFiles.containsKey( serviceId ) ) {
				deltaOpenFiles = openFileCount - lastOpenFiles.get( serviceId ) ;
			}

			lastOpenFiles.put( serviceId, openFileCount ) ;
			collectionResults.setDeltaOpenFiles( deltaOpenFiles ) ;

		} catch ( Exception e ) {

			logger.debug( "When run on Windows - this does not exist." ) ;

		}

		// **************** JVM threads
		mbeanName = "java.lang:type=Threading" ;
		attributeName = "ThreadCount" ;
		collectionResults.setJvmThreadCount( (int) mbeanConn.getAttribute(
				new ObjectName( mbeanName ), attributeName ) ) ;

		mbeanName = "java.lang:type=Threading" ;
		attributeName = "PeakThreadCount" ;
		collectionResults.setJvmThreadMax( (int) mbeanConn.getAttribute( new ObjectName(
				mbeanName ), attributeName ) ) ;


		// **************** JVM Classes
		mbeanName = "java.lang:type=ClassLoading" ;
		attributeName = "LoadedClassCount" ;
		var currentLoadedClasses = (int) mbeanConn.getAttribute(
				new ObjectName( mbeanName ), attributeName ) ;
		var deltaLoadedClasses = 0l ;
		if ( lastClassesLoaded.containsKey( serviceId ) ) {
			deltaLoadedClasses = currentLoadedClasses - lastClassesLoaded.get( serviceId ) ;
			if ( CsapApis.getInstance().application().isJunit() ) {
				deltaLoadedClasses = currentLoadedClasses ;
			}

		}
		collectionResults.setJvmClassesLoaded( deltaLoadedClasses );
		lastClassesLoaded.put( serviceId, currentLoadedClasses ) ;


		attributeName = "UnloadedClassCount" ;
		var currentUnLoadedClasses = (long) mbeanConn.getAttribute(
				new ObjectName( mbeanName ), attributeName ) ;
		var deltaUnLoadedClasses = 0l ;
		if ( lastClassesUnLoaded.containsKey( serviceId ) ) {
			deltaUnLoadedClasses = currentUnLoadedClasses - lastClassesUnLoaded.get( serviceId ) ;
			if ( CsapApis.getInstance().application().isJunit() ) {
				deltaUnLoadedClasses = currentUnLoadedClasses ;
			}
		}
		collectionResults.setJvmClassesUnLoaded( deltaUnLoadedClasses );
		lastClassesUnLoaded.put( serviceId, currentUnLoadedClasses ) ;


		attributeName = "TotalLoadedClassCount" ;
		collectionResults.setJvmTotalClassesLoaded( (long) mbeanConn.getAttribute(
				new ObjectName( mbeanName ), attributeName ) );

	}

	private void collectTomcatRequestData (
											MBeanServerConnection mbeanConn ,
											ServiceCollectionResults jmxResults ,
											String serviceNamePort )
		throws IOException ,
		MalformedObjectNameException ,
		MBeanException ,
		AttributeNotFoundException ,
		InstanceNotFoundException ,
		ReflectionException {

		// **************** Tomcat Global processor: collect http stats
		// Multiple connections ajp and http, add all together for graphs
		String tomcatJmxRoot = jmxResults.getServiceInstance( ).getTomcatJmxName( ) ;

		String mbeanName = tomcatJmxRoot + ":type=GlobalRequestProcessor,name=*" ;

		Set<ObjectInstance> tomcatGlobalRequestBeans = mbeanConn.queryMBeans(
				new ObjectName( mbeanName ), null ) ;

		for ( ObjectInstance tomcatConnectionInstance : tomcatGlobalRequestBeans ) {

			logger.debug( "Service: {} ObjectName: {}", serviceNamePort, tomcatConnectionInstance.getObjectName( ) ) ;

			String frontKey = serviceNamePort + tomcatConnectionInstance.getObjectName( ) ;

			long deltaCollected = javaLongDeltaCalculation( frontKey + "requestCount",
					(int) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "requestCount" ) ) ;

			jmxResults.setHttpRequestCount( jmxResults.getHttpRequestCount( ) + deltaCollected ) ;

			deltaCollected = javaLongDeltaCalculation( frontKey + "processingTime",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "processingTime" ) ) ;

			jmxResults.setHttpProcessingTime( jmxResults.getHttpProcessingTime( ) + deltaCollected ) ;

			deltaCollected = javaLongDeltaCalculation( frontKey + "bytesReceived",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "bytesReceived" ) ) ;
			jmxResults.setHttpBytesReceived( jmxResults.getHttpBytesReceived( ) + ( deltaCollected / 1024 ) ) ;

			deltaCollected = javaLongDeltaCalculation( frontKey + "bytesSent",
					(long) mbeanConn.getAttribute( tomcatConnectionInstance.getObjectName( ), "bytesSent" ) ) ;
			jmxResults.setHttpBytesSent( jmxResults.getHttpBytesSent( ) + ( deltaCollected / 1024 ) ) ;

		}

		// There may be multiple wars deployed...so add them all together
		String sessionMbeanName = tomcatJmxRoot + ":type=Manager,host=localhost,context=*" ;

		Set<ObjectInstance> tomcatManagerBeans = mbeanConn.queryMBeans(
				new ObjectName( sessionMbeanName ), null ) ;

		for ( ObjectInstance warDeployedInstance : tomcatManagerBeans ) {

			logger.debug( "Service: {} ObjectName: {}", serviceNamePort, warDeployedInstance.getObjectName( ) ) ;

			try {

				long sessionsActive = (int) mbeanConn
						.getAttribute( warDeployedInstance.getObjectName( ), "activeSessions" ) ;

				// active http sessions
				jmxResults.setSessionsActive( sessionsActive + jmxResults.getSessionsActive( ) ) ;

				// Use deltas, then we can track sessions per day
				String frontKey = serviceNamePort + warDeployedInstance.getObjectName( ) ;

				long sessionCount = (long) mbeanConn.getAttribute( warDeployedInstance.getObjectName( ),
						"sessionCounter" ) ;
				long deltaCollected = javaLongDeltaCalculation( frontKey + "sessionCounter", sessionCount ) ;

				logger
						.debug( "{}  sessionsActive: {} sessionCount: {} delta: {}", frontKey, sessionsActive,
								sessionCount, deltaCollected ) ;

				jmxResults.setSessionsCount( jmxResults.getSessionsCount( ) + deltaCollected ) ;

			} catch ( Exception e ) {

				logger.error( "Failed to collect session data for service: {}, reason: {}",
						serviceNamePort, CSAP.buildCsapStack( e ) ) ;

			}

		}

	}

	private void collectTomcatConnections (
											MBeanServerConnection mbeanConn ,
											ServiceCollectionResults jmxResults ,
											String serviceNamePort )
		throws IOException ,
		MalformedObjectNameException ,
		MBeanException ,
		AttributeNotFoundException ,
		InstanceNotFoundException ,
		ReflectionException {

		String mbeanName ;

		var tomcatJmxRoot = jmxResults.getServiceInstance( ).getTomcatJmxName( ) ;

		// **************** Tomcat connections
		mbeanName = tomcatJmxRoot + ":type=ThreadPool,name=*" ;
		Set<ObjectInstance> tomcatThreadPoolBeans = mbeanConn.queryMBeans(
				new ObjectName( mbeanName ), null ) ;

		for ( ObjectInstance objectInstance : tomcatThreadPoolBeans ) {

			logger.debug( "Service: {} ObjectName: {}", serviceNamePort, objectInstance.getObjectName( ) ) ;

			if ( jmxResults.getHttpConn( ) < 0 ) {

				// get rid of -10 init
				jmxResults.setHttpConn( 0 ) ;
				jmxResults.setTomcatThreadsBusy( 0 ) ;
				jmxResults.setTomcatThreadCount( 0 ) ;

			}

			try {

				jmxResults.setHttpConn( jmxResults.getHttpConn( ) + (Long) mbeanConn.getAttribute(
						objectInstance.getObjectName( ),
						"connectionCount" ) ) ;

			} catch ( Exception e ) {

				// tomcat 6 might not have.
				logger.debug( "Failed to get jmx data for service: {} Reason: {}", serviceNamePort, e.getMessage( ) ) ;

			}

			jmxResults.setTomcatThreadsBusy( jmxResults.getTomcatThreadsBusy( ) + (int) mbeanConn.getAttribute(
					objectInstance.getObjectName( ),
					"currentThreadsBusy" ) ) ;

			jmxResults.setTomcatThreadCount( jmxResults.getTomcatThreadCount( ) + (int) mbeanConn.getAttribute(
					objectInstance.getObjectName( ),
					"currentThreadCount" ) ) ;

		}

	}

	private ObjectNode deltaLastCollected = jacksonMapper.createObjectNode( ) ;

	private long javaLongDeltaCalculation ( String key , long collectedMetricAsLong ) {

		logger.debug( "Service: {} , collectedMetricAsLong: {}", key, collectedMetricAsLong ) ;

		long last = collectedMetricAsLong ;

		if ( deltaLastCollected.has( key ) ) {

			collectedMetricAsLong = collectedMetricAsLong - deltaLastCollected
					.get( key )
					.asLong( ) ;

			if ( collectedMetricAsLong < 0 ) {

				collectedMetricAsLong = 0 ;

			}

		} else {

			collectedMetricAsLong = 0 ;

		}

		deltaLastCollected.put( key, last ) ;

		return collectedMetricAsLong ;

	}

	Map<String, Double> lastCollectedDoubleValues = new HashMap<>( ) ;

	static String ALT_NEW_GC = "com.azul.zing:type=GarbageCollector,name=GPGC New" ;
	static String ALT_OLD_GC = "com.azul.zing:type=GarbageCollector,name=GPGC Old" ;

	private void collectAltMetrics (
										MBeanServerConnection mbeanConn ,
										ServiceCollectionResults serviceCollectionResults ,
										String serviceNamePort ) {

		try {

			var mNames = mbeanConn.queryMBeans( new ObjectName( ALT_NEW_GC ),
					null ) ;

			if ( mNames.size( ) <= 0 ) {

				logger.debug( "{} Skipping alt metrics collection due to not finding beans: {}", serviceNamePort, ALT_NEW_GC ) ;
				return ;

			}


			serviceCollectionResults.setAltNewGcThreads(
					(Integer) mbeanConn.getAttribute(
							new ObjectName( ALT_NEW_GC ),
							"GarbageCollectorThreadCount" ) ) ;
			
			serviceCollectionResults.setAltOldGcThreads(
					(Integer) mbeanConn.getAttribute(
							new ObjectName( ALT_OLD_GC ),
							"GarbageCollectorThreadCount" ) ) ;
			

			serviceCollectionResults.setAltNewGcPauseInS(
					getDeltaValue(
							ALT_NEW_GC,
							"CollectorCumulativePauseTimeSec",
							mbeanConn,
							serviceCollectionResults,
							serviceNamePort + "NewGcPause" ) ) ;

			serviceCollectionResults.setAltOldGcPauseInS(
					getDeltaValue(
							ALT_OLD_GC,
							"CollectorCumulativePauseTimeSec",
							mbeanConn,
							serviceCollectionResults,
							serviceNamePort + "OldGcPause" ) ) ;

			serviceCollectionResults.setAltNewGcRunPercent(
					(Double) mbeanConn.getAttribute(
							new ObjectName( ALT_NEW_GC ),
							"PercentageOfTimeCollectorIsRunning" ) ) ;

			serviceCollectionResults.setAltOldGcRunPercent(
					(Double) mbeanConn.getAttribute(
							new ObjectName( ALT_OLD_GC ),
							"PercentageOfTimeCollectorIsRunning" ) ) ;
			
			

//			long altObjectllocation = 0l ;
//			double altPercentHeapAfterCollection = 0.0 ;

			var deltaAllocationCount = javaLongDeltaCalculation( serviceNamePort + "altAllocation",
					(Long) mbeanConn.getAttribute(
						new ObjectName( "com.azul.zing:type=Memory" ),
					"ObjectsWithFinalizerCumulativeAllocationCount" ) ) ;

			serviceCollectionResults.setAltObjectAllocation( deltaAllocationCount ) ;

			var nonHeapBytes = (Long) mbeanConn.getAttribute(
					new ObjectName( "com.azul.zing:type=Memory" ),
					"NonJavaHeapUse" ) ;
			logger.debug( "{} collected nonHeapBytes: {}", serviceNamePort, nonHeapBytes ) ;
			serviceCollectionResults.setAltNonHeapMemory( nonHeapBytes / 1024 / 1024 ); ;


			serviceCollectionResults.setAltPercentHeapAfterCollection(
					(Double) mbeanConn.getAttribute(
							new ObjectName( "com.azul.zing:type=Memory" ),
							"PercentJavaHeapOccupiedAfterCollection" ) ) ;

			var compositeBean = mbeanConn.getAttribute(
					new ObjectName( ALT_NEW_GC ),
					"LastGCDetails" ) ;

			var newLastGcDetails = (CompositeDataSupport) compositeBean ;

			serviceCollectionResults.setAltNewGcAllocRate(
					(Double) JmxCustomCollector.getIfAvailable(
							newLastGcDetails,
							"allocationRateBetweenEndOfPreviousAndStart",
							Double.valueOf( 0.0 ) ) ) ;
			

			serviceCollectionResults.setAltNewGcPausePrevention(
					boolToInt( (Boolean) JmxCustomCollector.getIfAvailable(
							newLastGcDetails,
							"hasContingencyMemoryBeenUsedSinceEndOfPreviousCollection",
							Boolean.valueOf( false ) ) ) ) ;

			serviceCollectionResults.setAltNewGcContingency(
					boolToInt( (Boolean) JmxCustomCollector.getIfAvailable(
							newLastGcDetails,
							"hasPausePreventionMemoryBeenUsedSinceEndOfPreviousCollection",
							Long.valueOf( 0 ) ) ) ) ;

			var oldLastGcDetailsRaw = mbeanConn.getAttribute(
					new ObjectName( ALT_OLD_GC ),
					"LastGCDetails" ) ;

			var oldLastGcDetails = (CompositeDataSupport) oldLastGcDetailsRaw ;

			serviceCollectionResults.setAltOldGcCollected(
					(Long) JmxCustomCollector.getIfAvailable(
							oldLastGcDetails,
							"garbageCollected",
							Long.valueOf( 0 ) ) ) ;


			serviceCollectionResults.setAltOldGcPausePrevention(
					boolToInt( (Boolean) JmxCustomCollector.getIfAvailable(
							oldLastGcDetails,
							"hasContingencyMemoryBeenUsedSinceEndOfPreviousCollection",
							Boolean.valueOf( false ) ) ) ) ;

			serviceCollectionResults.setAltOldGcContingency(
					boolToInt( (Boolean) JmxCustomCollector.getIfAvailable(
							oldLastGcDetails,
							"hasPausePreventionMemoryBeenUsedSinceEndOfPreviousCollection",
							Long.valueOf( 0 ) ) ) ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed to detect alt collections {}", CSAP.buildCsapStack( e ) ) ;
			return ;

		}

	}

	public long boolToInt ( boolean b ) {

		return b ? 1 : 0 ;

	}

	double getDeltaValue (
							String mbeanName ,
							String attributeName ,
							MBeanServerConnection mbeanConn ,
							ServiceCollectionResults serviceCollectionResults ,
							String serviceIdAndAttKey ) {

		var deltaCollected = 0.0 ;

		try {

			var collectedValue = (Double) mbeanConn.getAttribute(
					new ObjectName( mbeanName ),
					attributeName ) ;

			logger.debug( "collectedValue: {}", collectedValue ) ;

			if ( lastCollectedDoubleValues.containsKey( serviceIdAndAttKey ) ) {

				deltaCollected = collectedValue - lastCollectedDoubleValues.get( serviceIdAndAttKey ) ;

			}

			lastCollectedDoubleValues.put( serviceIdAndAttKey, collectedValue ) ;

		} catch ( Exception e ) {

			logger.debug( "Failed to collectGC {}", CSAP.buildCsapStack( e ) ) ;

		}

		return deltaCollected ;

	}

	Map<String, Long> lastMinorGcMap = new HashMap<String, Long>( ) ;
	Map<String, Long> lastMajorGcMap = new HashMap<String, Long>( ) ;

	private void collectJavaHeapMetrics (
											MBeanServerConnection mbeanConn ,
											ServiceCollectionResults serviceCollectionResults ,
											String serviceNamePort )
		throws Exception {

		// **************** Memory
		String mbeanName = "java.lang:type=Memory" ;
		String attributeName = "HeapMemoryUsage" ;

		CompositeData resultData = (CompositeData) mbeanConn
				.getAttribute( new ObjectName( mbeanName ),
						attributeName ) ;

		serviceCollectionResults.setHeapUsed( Long
				.parseLong( resultData
						.get( "used" )
						.toString( ) )
				/ 1024 / 1024 ) ;

		serviceCollectionResults.setHeapMax( Long.parseLong( resultData
				.get( "max" )
				.toString( ) )
				/ 1024 / 1024 ) ;

		
		//
		//  Variety of GC collectors available: iterate over ALL found and apply to old and new
		//
		var gcBeans = mbeanConn.queryMBeans(
				new ObjectName( "java.lang:type=GarbageCollector,name=*" ), null ) ;

		for ( var objectInstance : gcBeans ) {

			long gcCount = 0 ;
			long gcCollectionTime = 0 ;

			try {

				gcCount = (Long) mbeanConn.getAttribute(
						objectInstance.getObjectName( ),
						"CollectionCount" ) ;
				gcCollectionTime = (Long) mbeanConn.getAttribute(
						objectInstance.getObjectName( ),
						"CollectionTime" ) ;

				// jmxResults.setHttpConn();
			} catch ( Exception e ) {

				// tomcat 6 might not have.
				logger.debug( "Failed to get jmx data for service: {}, Reason: {}", serviceNamePort, e.getMessage( ) ) ;

			}

			//
			// There are several different GC algorithms, the name is used to ID
			// if current object is major or minor
			//
			var isMajor = false ;
			var gcBeanName = objectInstance.getObjectName( ).toString( ).toLowerCase( ) ;

			if ( gcBeanName.contains( "mark" ) 
					|| gcBeanName.contains( "old" )
					|| gcBeanName.contains( "pauses" ) ) {

				isMajor = true ;

			}

			logger.debug( "Service: {} , gcBean: {} , gcCount: {}, isMajor: {}, gcCollectionTime: {} ",
					serviceNamePort, gcBeanName, gcCount, isMajor, gcCollectionTime ) ;

			// We show incremental times on UI - making any activity show up as
			// greater then 0
			long lastTime = gcCollectionTime ;

			if ( isMajor ) {

				if ( lastMajorGcMap.containsKey( serviceNamePort ) ) {

					lastTime = lastMajorGcMap.get( serviceNamePort ) ;

				}

				long delta = gcCollectionTime - lastTime ;

				if ( delta < 0 ) {

					delta = 0 ;

				}

				serviceCollectionResults.setMajorGcInMs( delta ) ;
				lastMajorGcMap.put( serviceNamePort, gcCollectionTime ) ;

			} else {

				if ( lastMinorGcMap.containsKey( serviceNamePort ) ) {

					lastTime = lastMinorGcMap.get( serviceNamePort ) ;

				}

				long delta = gcCollectionTime - lastTime ;

				if ( delta < 0 ) {

					delta = 0 ;

				}

				serviceCollectionResults.setMinorGcInMs( delta ) ;

				lastMinorGcMap.put( serviceNamePort, gcCollectionTime ) ;

			}

		}

	}

}
