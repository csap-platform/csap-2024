package org.csap.agent.stats.service;

import java.util.Iterator ;
import java.util.Map ;

import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * Results pojo for storing and updating application results - typically used to
 * store JMX values, but also http collected results as well
 *
 * @author someDeveloper
 *
 */
public class ServiceCollectionResults {

	private int inMemoryCacheSize ;

	public ServiceCollectionResults ( ServiceInstance serviceInstance, int inMemoryCacheSize ) {

		this.serviceInstance = serviceInstance ;
		this.inMemoryCacheSize = inMemoryCacheSize ;
		logger.debug( "Custom meters: {}", serviceInstance.hasServiceMeters( ) ) ;

	}

	@Override
	public String toString ( ) {

		return "ApplicationResults [cpuPercent=" + cpuPercent + ", jvmThreadCount=" + jvmThreadCount + ", jvmThreadMax="
				+ jvmThreadMax + ", openFiles=" + openFiles + ", heapUsed=" + heapUsed + ", heapMax=" + heapMax
				+ ", minorGcInMs=" + minorGcInMs + ", majorGcInMs=" + majorGcInMs + ", httpConn=" + httpConn
				+ ", threadsBusy=" + tomcatThreadsBusy + ", threadCount=" + tomcatThreadCount + ", httpRequestCount="
				+ httpRequestCount + ", httpProcessingTime=" + httpProcessingTime + ", httpBytesReceived="
				+ httpBytesReceived + ", httpBytesSent=" + httpBytesSent + ", sessionsCount=" + sessionsCount
				+ ", sessionsActive=" + sessionsActive + "\nCustomCollection="
				+ customMap.toString( ) + "]" ;

	}

	final Logger logger = LoggerFactory.getLogger( ServiceCollectionResults.class ) ;

	private long cpuPercent = 0 ;
	private long jvmThreadCount = 0 ;
	private long jvmThreadMax = 0 ;


	private long jvmTotalClassesLoaded = 0 ;
	private long jvmClassesLoaded = 0 ;
	private long jvmClassesUnLoaded = 0 ;

	private long openFiles = 0 ;
	private long deltaOpenFiles = 0 ;
	private long heapUsed = 0 ;
	private long heapMax = 0 ;

	//  reference for alt collections
	// https://docs.azul.com/prime/ZingMXBeans_javadoc/index.html?com/azul/zing/management/MemoryPoolMXBean.html
	//
	double altNewGcPauseInS = 0.0 ;
	double altOldGcPauseInS = 0.0 ;

	double altNewGcRunPercent = 0.0 ;
	double altOldGcRunPercent = 0.0 ;
	double altNewGcAllocRate = 0.0 ;
	double altOldGcCollected = 0.0 ;
	long altOldGcContingency = 0l ;
	long altOldGcPausePrevention = 0l ;

	long altObjectAllocation = 0l ;

	long altNonHeapMemory = 0l ;
	double altPercentHeapAfterCollection = 0.0 ;
	long altNewGcThreads = 0l ;
	long altOldGcThreads = 0l ;
	long altNewGcContingency = 0l ;
	long altNewGcPausePrevention = 0l ;

	public long getMinorGcInMs ( ) {

		return minorGcInMs ;

	}

	public void setMinorGcInMs ( long minorGcInMs ) {

		this.minorGcInMs = minorGcInMs ;

	}

	public long getMajorGcInMs ( ) {

		return majorGcInMs ;

	}

	public void setMajorGcInMs ( long majorGcInMs ) {

		this.majorGcInMs = majorGcInMs ;

	}

	private long minorGcInMs = 0 ;
	private long majorGcInMs = 0 ;
	private long httpConn = 0 ;
	private long tomcatThreadsBusy = 0 ;
	private long tomcatThreadCount = 0 ; // threadCount = 0 means JMX is not responding

	private long httpRequestCount = 0 ;
	private long httpProcessingTime = 0 ;
	private long httpBytesReceived = 0 ;
	private long httpBytesSent = 0 ;

	// JEE Sessions
	private long sessionsCount = 0 ;
	private long sessionsActive = 0 ;

	public long getSessionsCount ( ) {

		return sessionsCount ;

	}

	public void setSessionsCount ( long sessionsCount ) {

		this.sessionsCount = sessionsCount ;

	}

	public long getSessionsActive ( ) {

		return sessionsActive ;

	}

	public void setSessionsActive ( long sessionsActive ) {

		this.sessionsActive = sessionsActive ;

	}

	public long getHttpProcessingTime ( ) {

		return httpProcessingTime ;

	}

	public void setHttpProcessingTime ( long httpProcessingTime ) {

		this.httpProcessingTime = httpProcessingTime ;

	}

	public long getHttpBytesReceived ( ) {

		return httpBytesReceived ;

	}

	public void setHttpBytesReceived ( long httpBytesReceived ) {

		this.httpBytesReceived = httpBytesReceived ;

	}

	public long getHttpBytesSent ( ) {

		return httpBytesSent ;

	}

	public void setHttpBytesSent ( long httpBytesSent ) {

		this.httpBytesSent = httpBytesSent ;

	}

	public long getHttpRequestCount ( ) {

		return httpRequestCount ;

	}

	private ServiceInstance serviceInstance ;

	public ServiceInstance getServiceInstance ( ) {

		return serviceInstance ;

	}

	public long getCpuPercent ( ) {

		return cpuPercent ;

	}

	public void setCpuPercent ( long cpuPercent ) {

		this.cpuPercent = cpuPercent ;

	}

	public long getJvmThreadCount ( ) {

		return jvmThreadCount ;

	}

	public void setJvmThreadCount ( long jvmThreadCount ) {

		this.jvmThreadCount = jvmThreadCount ;

	}

	public long getJvmThreadMax ( ) {

		return jvmThreadMax ;

	}

	public void setJvmThreadMax ( long jvmThreadMax ) {

		this.jvmThreadMax = jvmThreadMax ;

	}

	public long getOpenFiles ( ) {

		return openFiles ;

	}

	public void setOpenFiles ( long openFiles ) {

		this.openFiles = openFiles ;

	}

	public long getHeapUsed ( ) {

		return heapUsed ;

	}

	public void setHeapUsed ( long heapUsed ) {

		this.heapUsed = heapUsed ;

	}

	public long getHeapMax ( ) {

		return heapMax ;

	}

	public void setHeapMax ( long heapMax ) {

		this.heapMax = heapMax ;

	}

	public long getHttpConn ( ) {

		return httpConn ;

	}

	public void setHttpConn ( long httpConn ) {

		this.httpConn = httpConn ;

	}

	public long getTomcatThreadsBusy ( ) {

		return tomcatThreadsBusy ;

	}

	public void setTomcatThreadsBusy ( long threadsBusy ) {

		this.tomcatThreadsBusy = threadsBusy ;

	}

	public long getTomcatThreadCount ( ) {

		return tomcatThreadCount ;

	}

	public void setTomcatThreadCount ( long threadCount ) {

		this.tomcatThreadCount = threadCount ;

	}

	public void setHttpRequestCount ( long requestCount ) {

		this.httpRequestCount = requestCount ;

	}

	public void add_results_to_application_collection (
														Map<String, ObjectNode> latest_application_collection ,
														String serviceId ) {

		if ( ! serviceInstance.hasServiceMeters( ) ) {

			logger.debug( "No Meters found" ) ;
			return ;

		}

		ensure_application_cache_initialized( serviceId, latest_application_collection ) ;

		ObjectNode serviceCustomMetricNode = latest_application_collection.get( serviceId ) ;

		StringBuilder customStorage = new StringBuilder( serviceId + ": \t" ) ;

		CSAP.asStreamHandleNulls( customMap )
				.forEach( metricId -> {

					// logger.info("Adding Custom Results" + metricId) ;
					ArrayNode metricArray = (ArrayNode) serviceCustomMetricNode.get( metricId ) ;

					if ( metricArray == null ) {

						metricArray = serviceCustomMetricNode.putArray( metricId ) ;
						customStorage.append( metricId + ", " ) ;

						// isSomeNewItems = true ;
						// logger.warn(serviceNamePort + " metricArray not
						// initialized for custom attribute not found in result set:
						// " + metricId ) ;
						// continue ;
					}

					if ( ! customMap.has( metricId ) ) {

						logger.warn( "{} custom attribute not found in result set: {}", serviceId, metricId ) ;
						// continue ;
						metricArray.insert( 0, 0 ) ;

					} else if ( metricArray.size( ) == 0 ) {

						// initialize to 0 to support sampling deltas in simon and
						// jmx delta in csap
						metricArray.insert( 0, 0 ) ;

					} else {

						metricArray.insert( 0, customMap.get( metricId ) ) ;

					}

					if ( metricArray.size( ) > inMemoryCacheSize ) {

						metricArray.remove( metricArray.size( ) - 1 ) ;

					}

				} ) ;

		Iterator<String> keyIter = serviceCustomMetricNode.fieldNames( ) ;

		while ( keyIter.hasNext( ) ) {

			String metricName = keyIter.next( ) ;

			if ( ! serviceInstance.hasMeter( metricName ) ) {

				logger.warn( "{} :  Removing metricName: {} - assumed due to definition update.", serviceId,
						metricName ) ;
				keyIter.remove( ) ;

			}

		}

		// if ( isSomeNewItems )
		// logger.info("Custom storage allocated: " + customStorage);
	}

	private long collectedValue ( JavaCollectionAttributes metric ) {

		if ( metric == JavaCollectionAttributes.cpuPercent ) {

			return getCpuPercent( ) ;

		}

		if ( metric == JavaCollectionAttributes.heapMax ) {

			return getHeapMax( ) ;

		}

		if ( metric == JavaCollectionAttributes.heapUsed ) {

			return getHeapUsed( ) ;

		}

		if ( metric == JavaCollectionAttributes.httpConnections ) {

			return getHttpConn( ) ;

		}

		if ( metric == JavaCollectionAttributes.httpKbytesReceived ) {

			return getHttpBytesReceived( ) ;

		}

		if ( metric == JavaCollectionAttributes.httpKbytesSent ) {

			return getHttpBytesSent( ) ;

		}

		if ( metric == JavaCollectionAttributes.httpProcessingTime ) {

			return getHttpProcessingTime( ) ;

		}

		if ( metric == JavaCollectionAttributes.httpRequestCount ) {

			return getHttpRequestCount( ) ;

		}

		if ( metric == JavaCollectionAttributes.jvmThreadCount ) {

			return getJvmThreadCount( ) ;

		}

		if ( metric == JavaCollectionAttributes.jvmThreadsMax ) {

			return getJvmThreadMax( ) ;

		}

		if ( metric == JavaCollectionAttributes.jvmClassLoaded ) {

			return getJvmClassesLoaded( ) ;

		}

		if ( metric == JavaCollectionAttributes.jvmClassUnLoaded ) {

			return getJvmClassesUnLoaded( ) ;

		}

		if ( metric == JavaCollectionAttributes.jvmTotalClassLoaded ) {

			return getJvmTotalClassesLoaded( ) ;

		}

		if ( metric == JavaCollectionAttributes.majorGcInMs ) {

			return getMajorGcInMs( ) ;

		}

		if ( metric == JavaCollectionAttributes.minorGcInMs ) {

			return getMinorGcInMs( ) ;

		}

		if ( metric == JavaCollectionAttributes.openFiles ) {

			return getOpenFiles( ) ;

		}

		if ( metric == JavaCollectionAttributes.openFilesDelta ) {

			return getDeltaOpenFiles( ) ;

		}

		if ( metric == JavaCollectionAttributes.sessionsActive ) {

			return getSessionsActive( ) ;

		}

		if ( metric == JavaCollectionAttributes.sessionsCount ) {

			return getSessionsCount( ) ;

		}

		if ( metric == JavaCollectionAttributes.tomcatThreadCount ) {

			return getTomcatThreadCount( ) ;

		}

		if ( metric == JavaCollectionAttributes.tomcatThreadsBusy ) {

			return getTomcatThreadsBusy( ) ;

		}

		return 0 ;

	}

	public void add_results_to_java_collection ( Map<String, ObjectNode> latestCollection , String serviceId ) {

		ensure_java_collection_initialized( latestCollection, serviceId ) ;

		ObjectNode serviceCacheNode = latestCollection.get( serviceId ) ;


		//
		//  Java AND http collection might be enabled. To filter,  we need to merge results.
		//

		for ( var javaAttribute : JavaCollectionAttributes.values( ) ) {

			//
			// alternate collections are selectively enabled
			//
			if ( javaAttribute.isDisabled( ) ) continue ;

			// some jvms are not tomcat, so skip tomcat specific metrics
			if ( javaAttribute.isTomcatOnly( ) && ! serviceInstance.is_tomcat_collect( ) ) continue ;

			var metricResultsArray = ( (ArrayNode) serviceCacheNode.path( javaAttribute.value + "_" + serviceId ) ) ;

			if ( javaAttribute == JavaCollectionAttributes.altNewGcPauseInS ) {

				metricResultsArray.insert( 0, getAltNewGcPauseInS( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altOldGcPauseInS ) {

				metricResultsArray.insert( 0, getAltOldGcPauseInS( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altNewGcRunPercent ) {

				metricResultsArray.insert( 0, getAltNewGcRunPercent( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altOldGcRunPercent ) {

				metricResultsArray.insert( 0, getAltOldGcRunPercent( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altNewGcAllocRate ) {

				metricResultsArray.insert( 0, getAltNewGcAllocRate( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altOldGcCollected ) {

				metricResultsArray.insert( 0, getAltOldGcCollected( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altOldGcPausePrevention ) {

				metricResultsArray.insert( 0, getAltOldGcPausePrevention( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altOldGcContingency ) {

				metricResultsArray.insert( 0, getAltOldGcContingency( ) ) ;

				//
				//
				//

			} else if ( javaAttribute == JavaCollectionAttributes.altObjectllocation ) {

				metricResultsArray.insert( 0, getAltObjectAllocation( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altNonHeapMemory ) {

				metricResultsArray.insert( 0, getAltNonHeapMemory() ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altPercentHeapAfterCollection ) {

				metricResultsArray.insert( 0, getAltPercentHeapAfterCollection( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altNewGcThreads ) {

				metricResultsArray.insert( 0, getAltNewGcThreads( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altOldGcThreads ) {

				metricResultsArray.insert( 0, getAltOldGcThreads( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altNewGcContingency ) {

				metricResultsArray.insert( 0, getAltNewGcContingency( ) ) ;

			} else if ( javaAttribute == JavaCollectionAttributes.altNewGcPausePrevention ) {

				metricResultsArray.insert( 0, getAltNewGcPausePrevention( ) ) ;

			} else {

				metricResultsArray.insert( 0, collectedValue( javaAttribute ) ) ;

			}

			if ( metricResultsArray.size( ) > inMemoryCacheSize ) {

				metricResultsArray.remove( metricResultsArray.size( ) - 1 ) ;

			}

		}

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private void ensure_java_collection_initialized ( Map<String, ObjectNode> latestCollection , String serviceId ) {

		if ( ! latestCollection.containsKey( serviceId ) ) {

			logger.debug( "Creating jmx results storage for: {}", serviceId ) ;

			ObjectNode serviceMetricNode = jacksonMapper.createObjectNode( ) ;
			serviceMetricNode.putArray( "timeStamp" ) ;

			for ( JavaCollectionAttributes jmxMetric : JavaCollectionAttributes.values( ) ) {

				// some jvms are not tomcat, so skip tomcat specific metrics
				if ( jmxMetric.isTomcatOnly( ) && ! serviceInstance.is_tomcat_collect( ) ) {

					continue ;

				}

				String metricFullName = jmxMetric.value + "_"
						+ serviceId ;
				serviceMetricNode.putArray( metricFullName ) ;

			}

			latestCollection.put( serviceId,
					serviceMetricNode ) ;

		}

	}

	/**
	 * Used to store application/service specific data. Mostly via JMX, but http as
	 * well
	 * 
	 * @param serviceId
	 *
	 * @param latest_application_collection
	 */
	private void ensure_application_cache_initialized (
														String serviceId ,
														Map<String, ObjectNode> latest_application_collection ) {
		// Check if custom metrics will be collected, and not needing init
		// This needs to by dynamic - based on any changes to service

		// logger.info("Config String" + metricsConfig.toString());
		if ( ! latest_application_collection.containsKey( serviceId )
				|| latest_application_collection.get( serviceId )
						.size( ) != serviceInstance.getServiceMeters( ).size( ) ) {

			ObjectNode serviceCustomMetricNode = latest_application_collection.get( serviceId ) ;

			if ( serviceCustomMetricNode == null ) {

				serviceCustomMetricNode = jacksonMapper.createObjectNode( ) ;

			}

			latest_application_collection.put( serviceId,
					serviceCustomMetricNode ) ;

		}

	}

	// private HashMap<String, Long> customMap = new HashMap<String, Long>();
	private ObjectNode customMap = jacksonMapper.createObjectNode( ) ;

	public void addCustomResultLong ( String metricId , long resultLong ) {

		customMap.put( metricId, resultLong ) ;

	}

	public void addCustomResultDouble ( String metricId , double resultDouble ) {

		customMap.put( metricId, resultDouble ) ;

	}

	public long getCustomResult ( String metricId ) {

		return customMap.get( metricId )
				.asLong( ) ;

	}

	public double getAltNewGcPauseInS ( ) {

		return altNewGcPauseInS ;

	}

	public void setAltNewGcPauseInS ( double deltaMinorPause ) {

		this.altNewGcPauseInS = CSAP.roundIt( deltaMinorPause, 2 ) ;

	}

	public double getAltOldGcPauseInS ( ) {

		return altOldGcPauseInS ;

	}

	public void setAltOldGcPauseInS ( double deltaMajorPause ) {

		this.altOldGcPauseInS = CSAP.roundIt( deltaMajorPause, 2 ) ;

	}

	public long getDeltaOpenFiles ( ) {

		return deltaOpenFiles ;

	}

	public void setDeltaOpenFiles ( long deltaOpenFiles ) {

		this.deltaOpenFiles = deltaOpenFiles ;

	}

	public double getAltNewGcRunPercent ( ) {

		return altNewGcRunPercent ;

	}

	public void setAltNewGcRunPercent ( double altNewGcRunPercent ) {

		this.altNewGcRunPercent = CSAP.roundIt( altNewGcRunPercent, 2 ) ;

	}

	public double getAltOldGcRunPercent ( ) {

		return altOldGcRunPercent ;

	}

	public void setAltOldGcRunPercent ( double altOldGcRunPercent ) {

		this.altOldGcRunPercent = CSAP.roundIt( altOldGcRunPercent, 2 ) ;

	}

	public double getAltNewGcAllocRate ( ) {

		return altNewGcAllocRate ;

	}

	public void setAltNewGcAllocRate ( double altNewGcAllocRate ) {

		this.altNewGcAllocRate = CSAP.roundIt( altNewGcAllocRate, 2 ) ;

	}

	public double getAltOldGcCollected ( ) {

		return altOldGcCollected ;

	}

	public void setAltOldGcCollected ( double altOldGcCollected ) {

		this.altOldGcCollected = CSAP.roundIt( altOldGcCollected / 1048576, 2 ) ;

	}

	public long getAltOldGcContingency ( ) {

		return altOldGcContingency ;

	}

	public void setAltOldGcContingency ( long altOldGcContingency ) {

		this.altOldGcContingency = altOldGcContingency ;

	}

	public long getAltOldGcPausePrevention ( ) {

		return altOldGcPausePrevention ;

	}

	public void setAltOldGcPausePrevention ( long altOldGcPausePrevention ) {

		this.altOldGcPausePrevention = altOldGcPausePrevention ;

	}

	public long getAltObjectAllocation ( ) {

		return altObjectAllocation ;

	}

	public void setAltObjectAllocation ( long altObjectllocation ) {

		this.altObjectAllocation = altObjectllocation ;

	}

	public long getAltNonHeapMemory() {
		return altNonHeapMemory;
	}

	public void setAltNonHeapMemory(long altNonHeapMemory) {
		this.altNonHeapMemory = altNonHeapMemory;
	}


	public double getAltPercentHeapAfterCollection ( ) {

		return altPercentHeapAfterCollection ;

	}

	public void setAltPercentHeapAfterCollection ( double altPercentHeapAfterCollection ) {

		this.altPercentHeapAfterCollection = CSAP.roundIt( altPercentHeapAfterCollection, 2 ) ;

	}

	public long getAltNewGcThreads ( ) {

		return altNewGcThreads ;

	}

	public void setAltNewGcThreads ( long altNewGcThreads ) {

		this.altNewGcThreads = altNewGcThreads ;

	}

	public long getAltOldGcThreads ( ) {

		return altOldGcThreads ;

	}

	public void setAltOldGcThreads ( long altOldGcThreads ) {

		this.altOldGcThreads = altOldGcThreads ;

	}

	public long getAltNewGcContingency ( ) {

		return altNewGcContingency ;

	}

	public void setAltNewGcContingency ( long altNewGcContingency ) {

		this.altNewGcContingency = altNewGcContingency ;

	}

	public long getAltNewGcPausePrevention ( ) {

		return altNewGcPausePrevention ;

	}

	public void setAltNewGcPausePrevention ( long altNewGcPausePrevention ) {

		this.altNewGcPausePrevention = altNewGcPausePrevention ;

	}

	public long getJvmTotalClassesLoaded( ) {
		return jvmTotalClassesLoaded;
	}

	public void setJvmTotalClassesLoaded( long jvmTotalClassesLoaded ) {
		this.jvmTotalClassesLoaded = jvmTotalClassesLoaded;
	}

	public long getJvmClassesLoaded( ) {
		return jvmClassesLoaded;
	}

	public void setJvmClassesLoaded( long jvmClassesLoaded ) {
		this.jvmClassesLoaded = jvmClassesLoaded;
	}

	public long getJvmClassesUnLoaded( ) {
		return jvmClassesUnLoaded;
	}

	public void setJvmClassesUnLoaded( long jvmClassesUnLoaded ) {
		this.jvmClassesUnLoaded = jvmClassesUnLoaded;
	}


}
