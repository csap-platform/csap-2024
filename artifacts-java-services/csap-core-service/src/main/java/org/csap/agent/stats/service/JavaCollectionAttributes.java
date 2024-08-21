package org.csap.agent.stats.service;

import org.csap.agent.CsapApis ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public enum JavaCollectionAttributes {

	cpuPercent( "cpuPercent" ),

	httpConnections( JavaCollectionAttributes.TOMCAT_CONNECTIONS ),
	httpRequestCount( "httpRequestCount" ), httpProcessingTime( "httpProcessingTime" ),
	httpKbytesReceived( "httpKbytesReceived" ), httpKbytesSent( "httpKbytesSent" ),
	sessionsCount( "sessionsCount" ), sessionsActive( "sessionsActive" ),

	openFiles( "openFiles" ), openFilesDelta( "openFilesDelta" ),

	minorGcInMs( "minorGcInMs" ), majorGcInMs( "majorGcInMs" ),

	heapUsed( "heapUsed" ), heapMax( "heapMax" ),

	tomcatThreadsBusy( "tomcatThreadsBusy" ), tomcatThreadCount( "tomcatThreadCount" ),
	jvmThreadCount( JavaCollectionAttributes.JVM_THREAD_COUNT ),

	jvmThreadsMax( "jvmThreadsMax" ),
	jvmClassLoaded( "jvmClassLoaded" ), jvmTotalClassLoaded( "jvmTotalClassLoaded" ),
	jvmClassUnLoaded( "jvmClassUnLoaded" ),

	//
	// alternate JVM collections enabled via project settings
	//
	altNewGcPauseInS( "altNewGcPauseInS" , false ), altOldGcPauseInS( "altOldGcPauseInS" , false ),
	altNewGcRunPercent( "altNewGcRunPercent" , false ), altOldGcRunPercent( "altOldGcRunPercent" , false ),
	altNewGcAllocRate( "altNewGcAllocRate" , false ), altOldGcCollected( "altOldGcCollected" , false ),
	
	altObjectllocation( "altObjectAllocation" , false ), altPercentHeapAfterCollection( "altPercentHeapAfterCollections" , false ),

	altNonHeapMemory( "altNonHeapMemory", false ),
	altNewGcThreads( "altNewGcThreads" , false ), altOldGcThreads( "altOldGcThreads" , false ),
	altNewGcContingency( "altNewGcContingency" , false ), altNewGcPausePrevention( "altNewGcPausePrevention" , false ),
	
	altOldGcContingency( "altOldGcContingency" , false ), altOldGcPausePrevention( "altOldGcPausePrevention" , false ),
	;

	public String value ;
	public boolean isCore = true ;

	private JavaCollectionAttributes ( String value ) {

		this.value = value ;

	}

	private JavaCollectionAttributes ( String value, boolean isCoreCollection ) {

		this.value = value ;
		this.isCore = isCoreCollection ;

	}

	public final static String TOMCAT_CONNECTIONS = "tomcatConnections" ;
	public final static String JVM_THREAD_COUNT = "jvmThreadCount" ;

	public boolean isTomcatOnly ( ) {

		if ( this == httpConnections ||
				this == httpRequestCount ||
				this == httpProcessingTime ||
				this == httpKbytesReceived ||
				this == httpKbytesSent ||
				this == sessionsCount ||
				this == sessionsActive ||
				this == tomcatThreadCount ||
				this == tomcatThreadsBusy )
			return true ;

		return false ;

	}

	static ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public boolean isDisabled ( ) {

		if ( this.isCore ) return false ;

		return ! CsapApis.getInstance( )
				.application( )
				.environmentSettings( )
				.isIncludeAltJavaAttributes( ) ;

	}

	static public ObjectNode graphLabels ( ) {

		ObjectNode labels = jacksonMapper.createObjectNode( ) ;

		for ( var javaAttribute : JavaCollectionAttributes.values( ) ) {

			if ( javaAttribute.isDisabled( ) ) {

				continue ;

			}

			switch ( javaAttribute ) {

				case cpuPercent :
					labels.put( javaAttribute.value, "Cpu %" ) ;
					break ;
				case httpConnections :
					labels.put( javaAttribute.value, "Tomcat Connections" ) ;
					break ;
				case httpRequestCount :
					labels.put( javaAttribute.value, "Http Requests" ) ;
					break ;
				case httpProcessingTime :
					labels.put( javaAttribute.value, "Http Processing Time (ms)" ) ;
					break ;
				case httpKbytesReceived :
					labels.put( javaAttribute.value, "Http Bytes Received (KB)" ) ;
					break ;
				case httpKbytesSent :
					labels.put( javaAttribute.value, "Http Bytes Sent (KB)" ) ;
					break ;
				case sessionsCount :
					labels.put( javaAttribute.value, "New User Sessions" ) ;
					break ;
				case sessionsActive :
					labels.put( javaAttribute.value, "Users Active" ) ;
					break ;
				case openFiles :
					labels.put( javaAttribute.value, "Open Files" ) ;
					break ;
				case openFilesDelta :
					labels.put( javaAttribute.value, "Open Files Change" ) ;
					break ;
				case minorGcInMs :
					labels.put( javaAttribute.value, "GC: Young/Cycle/Minor Time (ms)" ) ;
					break ;
				case majorGcInMs :
					labels.put( javaAttribute.value, "GC: Old/Pause/Major Time (ms)" ) ;
					break ;
				case heapUsed :
					labels.put( javaAttribute.value, "Heap Used (MB)" ) ;
					break ;
				case heapMax :
					labels.put( javaAttribute.value, "Heap Max (MB)" ) ;
					break ;
				case tomcatThreadsBusy :
					labels.put( javaAttribute.value, "Tomcat Threads Busy" ) ;
					break ;
				case tomcatThreadCount :
					labels.put( javaAttribute.value, "Tomcat Thread Count" ) ;
					break ;


				case jvmThreadCount :
					labels.put( javaAttribute.value, "Java Threads" ) ;
					break ;
				case jvmThreadsMax :
					labels.put( javaAttribute.value, "Java Max Threads" ) ;
					break ;


				case jvmClassLoaded:
					labels.put( javaAttribute.value, "Classes Loaded" ) ;
					break ;
				case jvmClassUnLoaded:
					labels.put( javaAttribute.value, "Classes UnLoaded" ) ;
					break ;
				case jvmTotalClassLoaded:
					labels.put( javaAttribute.value, "Total Classes Loaded" ) ;
					break ;

				case altNewGcPauseInS :
					labels.put( javaAttribute.value, "Alt New GC Pause (s)" ) ;
					break ;
				case altOldGcPauseInS :
					labels.put( javaAttribute.value, "Alt Old GC Pause (s)" ) ;
					break ;

				case altNewGcRunPercent :
					labels.put( javaAttribute.value, "Alt New GC Run %" ) ;
					break ;

				case altOldGcRunPercent :
					labels.put( javaAttribute.value, "Alt Old GC Run %" ) ;
					break ;
				case altNewGcAllocRate :
					labels.put( javaAttribute.value, "Alt New GC Alloc (MB/s)" ) ;
					break ;
				case altOldGcCollected :
					labels.put( javaAttribute.value, "Alt Old GC Collected (mb)" ) ;
					break ;
				case altOldGcContingency :
					labels.put( javaAttribute.value, "Alt Old GC Contingency" ) ;
					break ;
				case altOldGcPausePrevention :
					labels.put( javaAttribute.value, "Alt Old GC Pause Prevention" ) ;
					break ;

				case altObjectllocation :
					labels.put( javaAttribute.value, "Alt Objects with Finalizer Count" ) ;
					break ;

				case altNonHeapMemory :
					labels.put( javaAttribute.value, "Alt Non Heap Memory (mb)" ) ;
					break ;

				case altPercentHeapAfterCollection :
					labels.put( javaAttribute.value, "Alt % Heap Occupied After Col" ) ;
					break ;
				case altNewGcThreads :
					labels.put( javaAttribute.value, "Alt New GC Threads" ) ;
					break ;
				case altOldGcThreads :
					labels.put( javaAttribute.value, "Alt Old GC Threads" ) ;
					break ;
				case altNewGcContingency :
					labels.put( javaAttribute.value, "Alt New GC Contingency" ) ;
					break ;
				case altNewGcPausePrevention :
					labels.put( javaAttribute.value, "Alt New GC Pause Prevention" ) ;
					break ;
					
					
					
				default:
					throw new AssertionError( javaAttribute.name( ) ) ;

			}

		}

		return labels ;

	}

};
