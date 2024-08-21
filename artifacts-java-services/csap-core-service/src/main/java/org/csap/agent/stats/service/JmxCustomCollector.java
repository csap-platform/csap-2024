package org.csap.agent.stats.service;

import java.util.concurrent.TimeUnit ;
import java.util.regex.Matcher ;
import java.util.stream.Collectors ;

import javax.management.MBeanServerConnection ;
import javax.management.ObjectName ;
import javax.management.openmbean.CompositeDataSupport ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
//import org.javasimon.CounterSample ;
//import org.javasimon.SimonManager ;
//import org.javasimon.Split ;
//import org.javasimon.StopwatchSample ;
//import org.javasimon.jmx.SimonManagerMXBean ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class JmxCustomCollector {

	public static final String TOMCAT_SERVLET_CONTEXT_TOKEN = "__CONTEXT__" ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private ObjectNode deltaLastCollected = jacksonMapper.createObjectNode( ) ;

	private ServiceCollector serviceCollector ;

	CsapApis csapApis ;

	public JmxCustomCollector ( ServiceCollector serviceCollector, CsapApis csapApis ) {

		this.serviceCollector = serviceCollector ;

		this.csapApis = csapApis ;

	}

	/**
	 *
	 * Each JVM can optionally specify additional attributes to collect
	 *
	 * @param instance
	 * @param serviceNamePort
	 * @param collectionResults
	 * @param mbeanConn
	 */
	public void collect (
							MBeanServerConnection jmxServerConnection ,
							ServiceCollectionResults collectionResults ) {

		var csapService = collectionResults.getServiceInstance( ) ;

		// System.err.println( "\n\n xxx logging issues\n\n " );
		logger.debug( "\n\n {} JMX Collection\n\n", csapService.getName( ) ) ;

		collectionResults
				.getServiceInstance( )
				.getServiceMeters( )
				.stream( )
				.filter( ServiceMeter::isMbean )
				.forEach( serviceMeter -> {

					Object attributeCollected = 0 ;
					var jmxAttributeTimer = csapApis.metrics( ).startTimer( ) ;

					boolean isCollectionSuccesful = false ;

					try {

						logger.debug( "serviceMeter: {}", serviceMeter ) ;

						if ( serviceMeter.getMeterType( ).isMbean( ) ) {

							attributeCollected = collectCustomMbean(
									serviceMeter,
									collectionResults,
									jmxServerConnection ) ;

							// } else if ( serviceMeter.getMeterType().isSimon() ) {
							//
							// attributeCollected = collectCustomSimon( serviceMeter, simonMgrMxBean,
							// mbeanConn, collectionResults ) ;

						} else {

							logger.warn( "Unexpected meter type: {}", serviceMeter.toString( ) ) ;
							throw new Exception( "Unknown metric type" ) ;

						}

						isCollectionSuccesful = true ;

					} catch ( Throwable e ) {

						if ( ! serviceMeter.isIgnoreErrors( ) ) {

							// SLA will monitor counts
							csapApis.metrics( ).incrementCounter( "csap.collect-jmx.service.failures" ) ;

							csapApis.metrics( ).incrementCounter( "collect-jmx.service.failures."
									+ csapService.getName( ) ) ;

							csapApis.metrics( ).incrementCounter( "collect-jmx.service-failures."
									+ csapService.getName( )
									+ "-" + serviceMeter.getCollectionId( ) ) ;

							if ( serviceCollector.isShowWarnings( ) ) {

								String reason = e.getMessage( ) ;

								if ( reason != null && reason.length( ) > 60 ) {

									reason = e
											.getClass( )
											.getName( ) ;

								}

								if ( ! csapApis.application( ).isDesktopProfileActiveOrSpringNull( ) ) {

									logger.warn( CsapApplication.header(
											"Failed to collect {} for service {}\n Reason: {}, Cause: {}" ),
											serviceMeter.getCollectionId( ),
											csapService.getName( ), reason,
											e.getCause( ) ) ;

								}

								logger.debug( "{}", CSAP.buildCsapStack( e ) ) ;

							}

						}

						logger.debug( "{} Failed getting custom metrics for: {}, reason: {}",
								csapService.getName( ),
								serviceMeter.getCollectionId( ),
								CSAP.buildCsapStack( e ) ) ;

					} finally {

						var resultLong = -1l ;

						if ( isCollectionSuccesful ) {

							processCollectedAttribute(
									collectionResults,
									attributeCollected,
									csapService,
									serviceMeter ) ;

//							resultLong = processCollectedAttribute(
//									attributeCollected,
//									csapService,
//									serviceMeter ) ;

						}

//						if ( serviceMeter.getCollectionId( ).equalsIgnoreCase( ServiceAlertsEnum.JAVA_HEARTBEAT ) ) {
//
//							// for hearbeats, store the time IF it has passed
//							if ( resultLong == 1 ) {
//
//								var nanos = csapApis.metrics( ).stopTimer(
//										jmxAttributeTimer,
//										"collect-jmx.service-attribute" ) ;
//								resultLong = TimeUnit.NANOSECONDS.toMillis( nanos ) ;
//
//								// some apps return very quickly due to not actually
//								// implementing. return 1 if that happens
//								if ( resultLong == 0 ) {
//
//									resultLong = 1 ; // minimum of 1 to indicate success
//
//								}
//
//							}
//
//							csapService.getDefaultContainer( ).setJmxHeartbeatMs( resultLong ) ;
//
//						}
//
//						collectionResults.addCustomResultLong( serviceMeter.getCollectionId( ), resultLong ) ;

					}

				} ) ;

	}

	private void processCollectedAttribute (
	                                        	ServiceCollectionResults collectionResults,
												Object attributeCollected ,
												ServiceInstance csapService ,
												ServiceMeter serviceMeter ) {

		var resultLong = 0l ;
		

		//
		// First check for composite types
		//
		if ( attributeCollected instanceof CompositeDataSupport ) {

			try {

				var subName = serviceMeter.getMbeanCollectSubAttributeName( ) ;

				if ( StringUtils.isNotEmpty( subName ) ) {

					var jmxCompositeData = (CompositeDataSupport) attributeCollected ;

					//showCompositeFields( jmxCompositeData ) ;

					logger.debug( "{}  Composite detected: {} ", csapService.getName( ), subName );
					attributeCollected = getIfAvailable( jmxCompositeData, subName, Long.valueOf( 0l ) ) ;

				}

				//
				// ref https://docs.azul.com/prime/ZingMXBeans_javadoc/index.html
				//

			} catch ( Exception e ) {

				logger.warn( "Failed to process field: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}
		if ( attributeCollected instanceof Double ) {

			processAsDouble( collectionResults, attributeCollected, csapService, serviceMeter ) ;
			
			return ;


		}

		if ( attributeCollected instanceof Long ) {

			resultLong = (Long) attributeCollected ;

		} else if ( attributeCollected instanceof Integer ) {

			resultLong = (Integer) attributeCollected ;

		} else if ( attributeCollected instanceof Boolean ) {

			logger.debug( "Got a boolean result" ) ;
			Boolean b = (Boolean) attributeCollected ;

			if ( b ) {

				resultLong = 1 ;

			} else {

				resultLong = 0 ;

			}

		}

		logger.debug( "{} metric: {} , jmxResultObject: {} , resultLong: {}",
				csapService.getName( ),
				serviceMeter.getCollectionId( ), attributeCollected, resultLong ) ;

		if ( ! ( attributeCollected instanceof Double ) ) {

			resultLong = resultLong * serviceMeter.getMultiplyBy( ) ;

		}

		resultLong = Math.round( resultLong / serviceMeter.getDivideBy( 
				serviceCollector.getCollectionIntervalSeconds( ) ) ) ;

		if ( serviceMeter.isDelta( ) ) {

			var last = resultLong ;
			var key = csapService.getName( ) + serviceMeter.getCollectionId( ) ;

			if ( deltaLastCollected.has( key ) ) {

				resultLong = resultLong - deltaLastCollected.get( key ).asLong( ) ;

				if ( resultLong < 0 ) {

					resultLong = 0 ;

				}

			} else {

				resultLong = 0 ;

			}

			deltaLastCollected.put( key, last ) ;

		}

		logger.debug( "\n\n{} ====> metricId: {}, resultLong: {} \n\n",
				csapService.getName( ),
				serviceMeter.getCollectionId( ),
				resultLong ) ;
		
		collectionResults.addCustomResultLong( serviceMeter.getCollectionId( ), resultLong ) ;

//		return resultLong ;

	}

	private void processAsDouble (
									ServiceCollectionResults collectionResults ,
									Object attributeCollected ,
									ServiceInstance csapService ,
									ServiceMeter serviceMeter ) {

		var collectedDouble = (Double) attributeCollected ;
		collectedDouble = collectedDouble * serviceMeter.getMultiplyBy( ) ;

		if ( serviceMeter.getCollectionId( ).equals( "SystemCpuLoad" )
				|| serviceMeter.getCollectionId( ).equals( "ProcessCpuLoad" ) ) {

			logger.debug( "Adding multiple by for cpu values: {}", serviceMeter
					.getCollectionId( ) ) ;
			collectedDouble = collectedDouble * 100 ;

		} else if ( collectedDouble < 1 ) {

			logger.debug( "{}: Multiplying {} by 1000 to store. Add divideBy 1000",
					csapService.getName( ),
					serviceMeter.getCollectionId( ) ) ;
			//d = d * 1000 ;

		}
		
		collectedDouble = collectedDouble / serviceMeter.getDivideBy( 
				serviceCollector.getCollectionIntervalSeconds( ) ) ;

		

		//logger.info( "Collected: {}  rounded: {}", collectedDouble, rounded ); 
		
		
		if ( serviceMeter.isDelta( ) ) {

			var last = collectedDouble ;
			if ( serviceMeter.getDecimals( ) > 0) {
				last = CSAP.roundIt( collectedDouble, serviceMeter.getDecimals( ) ) ;
			}
			var key = csapService.getName( ) + serviceMeter.getCollectionId( ) ;

			if ( deltaLastCollected.has( key ) ) {

				collectedDouble = collectedDouble - deltaLastCollected.get( key ).asDouble( ) ;

				if ( collectedDouble < 0 ) {
					collectedDouble = 0.0 ;
				}

			} else {

				collectedDouble = 0.0 ;

			}

			deltaLastCollected.put( key, last ) ;

		}
		

		var rounded = CSAP.roundIt( collectedDouble, serviceMeter.getDecimals( ) ) ;
		collectionResults.addCustomResultDouble( serviceMeter.getCollectionId( ), rounded ) ;
		
		return  ;

	}

//	private void showCompositeFields ( CompositeDataSupport jmxCompositeData ) {
//
//		var itemListing = jmxCompositeData.toString( )
//				.replaceAll( Matcher.quoteReplacement( "itemName=" ),
//						"\n\t" ) ;
//
//		logger.info( "Collected jmxCompositeData: {}", itemListing ) ;
//
//		var ableToUseContingencyMemory = getIfAvailable( jmxCompositeData, "ableToUseContingencyMemory" ) ;
//		var ableToUsePausePreventionMemory = getIfAvailable( jmxCompositeData,
//				"ableToUsePausePreventionMemory" ) ;
//
//		var garbageCollected = getIfAvailable( jmxCompositeData, "garbageCollected" ) ;
//
//		var hasContingencyMemoryBeenUsedSinceEndOfPreviousCollection = getIfAvailable( jmxCompositeData,
//				"hasContingencyMemoryBeenUsedSinceEndOfPreviousCollection" ) ;
//		var collectionDuration = getIfAvailable( jmxCompositeData, "collectionDuration" ) ;
//
//		var allocationRateBetweenEndOfPreviousAndStart = getIfAvailable( jmxCompositeData,
//				"allocationRateBetweenEndOfPreviousAndStart" ) ;
//		
//		var hasPausePreventionMemoryBeenUsedSinceEndOfPreviousCollection = getIfAvailable( jmxCompositeData,
//				"hasPausePreventionMemoryBeenUsedSinceEndOfPreviousCollection" ) ;
//		
//		
//
//		var peakPausePreventionMemoryUsedSinceEndOfPreviousCollection = getIfAvailable( jmxCompositeData,
//				"peakPausePreventionMemoryUsedSinceEndOfPreviousCollection" ) ;
//
//		logger.info( CSAP.buildDescription( "Collected CompositeDataSupport: ",
//				"collectionDuration", collectionDuration,
//				"garbageCollected", CSAP.printBytesWithUnits( (long) garbageCollected ),
//				"ableToUseContingencyMemory", ableToUseContingencyMemory,
//				"hasContingencyMemoryBeenUsedSinceEndOfPreviousCollection",
//				hasContingencyMemoryBeenUsedSinceEndOfPreviousCollection,
//				"allocationRateBetweenEndOfPreviousAndStart_MB_S",
//				allocationRateBetweenEndOfPreviousAndStart,
//				"ableToUsePausePreventionMemory", ableToUsePausePreventionMemory,
//				"hasPausePreventionMemoryBeenUsedSinceEndOfPreviousCollection",
//				hasPausePreventionMemoryBeenUsedSinceEndOfPreviousCollection,
//				"peakPausePreventionMemoryUsedSinceEndOfPreviousCollection",
//				peakPausePreventionMemoryUsedSinceEndOfPreviousCollection ) ) ;
//
//	}

	public static Object getIfAvailable ( CompositeDataSupport source , String key, Object defaultValue ) {

		if ( source.containsKey( key ) ) {

			return source.get( key ) ;

		}

		return defaultValue ;

	}

	private Object collectCustomMbean (
										ServiceMeter serviceMeter ,
										ServiceCollectionResults jmxResults ,
										MBeanServerConnection mbeanConn )
		throws Exception {

		Object jmxResultObject = 0 ;
		var mbeanNameCustom = serviceMeter.getMbeanName( ) ;

		if ( mbeanNameCustom.contains( TOMCAT_SERVLET_CONTEXT_TOKEN ) ) {

			// Some servlet metrics require version string in name
			// logger.info("****** version: " +
			// jmxResults.getInstanceConfig().getMavenVersion());
			var version = jmxResults
					.getServiceInstance( )
					.getMavenVersion( ) ;

			if ( jmxResults
					.getServiceInstance( )
					.isScmDeployed( ) ) {

				version = jmxResults
						.getServiceInstance( )
						.getScmVersion( ) ;
				version = version.split( " " )[0] ; // first word of
				// scm
				// scmVersion=3.5.6-SNAPSHOT
				// Source build
				// by ...

			}

			// WARNING: version must be updated when testing.
			var serviceContext = "//localhost/" + jmxResults
					.getServiceInstance( )
					.getContext( ) ;
			// if ( !jmxResults.getServiceInstance().is_springboot_server() ) {
			// serviceContext += "##" + version ;
			// }
			mbeanNameCustom = mbeanNameCustom.replaceAll( TOMCAT_SERVLET_CONTEXT_TOKEN, serviceContext ) ;
			logger.debug( "Using custom name: {} ", mbeanNameCustom ) ;

		}

		var mbeanAttributeName = serviceMeter.getMbeanCollectAttributeName( ) ;

		if ( mbeanAttributeName.equals( "SystemCpuLoad" ) ) {

			// Reuse already collected values (load is stateful)
			jmxResultObject = Long.valueOf( serviceCollector.getCollected_HostCpu( ).get( 0 ).asLong( ) ) ;

		} else if ( mbeanAttributeName.equals( "ProcessCpuLoad" ) ) {

			// Reuse already collected values
			jmxResultObject = Long.valueOf( jmxResults.getCpuPercent( ) ) ;

		} else if ( serviceMeter.getCollectionId( ).equalsIgnoreCase( ServiceAlertsEnum.JAVA_HEARTBEAT )
				&& ! serviceCollector.isPublishSummaryAndPerformHeartBeat( ) && ! serviceCollector
						.isTestHeartBeat( ) ) {

			// special case to avoid double heartbeats
			// reUse collected value from earlier interval.
			jmxResultObject = Long.valueOf( jmxResults.getServiceInstance( ).getDefaultContainer( )
					.getJmxHeartbeatMs( ) ) ;

		} else {

			logger.debug( "Collecting mbean: {}, attribute: {}", mbeanNameCustom, mbeanAttributeName ) ;
			jmxResultObject = mbeanConn.getAttribute( new ObjectName( mbeanNameCustom ),
					mbeanAttributeName ) ;

		}

		logger.debug( "Result for {} is: {}", mbeanAttributeName, jmxResultObject ) ;
		return jmxResultObject ;

	}

}
