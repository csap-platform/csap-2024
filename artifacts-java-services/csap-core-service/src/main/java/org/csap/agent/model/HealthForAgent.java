package org.csap.agent.model;

import java.io.IOException ;
import java.util.List ;
import java.util.TreeMap ;
import java.util.concurrent.atomic.AtomicInteger ;

import org.csap.agent.CsapApis ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.JsonParseException ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class HealthForAgent {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	CsapApis csapApis ;
	ObjectMapper jacksonMapper ;

	public HealthForAgent ( CsapApis csapApis, ObjectMapper jsonMapper ) {

		this.csapApis = csapApis ;
		this.jacksonMapper = jsonMapper ;

	}

	public void service_summary (
									ObjectNode summaryJson ,
									boolean blocking ,
									String clusterFilter ,
									ObjectNode activeServiceReport ,
									TreeMap<String, Integer> serviceTotalCountMap ,
									TreeMap<String, String> serviceTypeMap ,
									TreeMap<String, String> serviceRuntimeMap ,
									ObjectNode processGroupReport,
									ObjectNode hostMapNode )
		throws IOException ,
		JsonParseException ,
		JsonMappingException {

		logger.debug( "clusterFilter: {}", clusterFilter ) ;

		// int totalServicesActive = 0;
		AtomicInteger totalServicesActive = new AtomicInteger( 0 ) ;

		if ( blocking ) {

			csapApis.application( ).markServicesForFileSystemScan( false ) ;
			csapApis.application( ).run_application_scan( ) ;

		} else {

			csapApis.application( ).run_application_scan( ) ;

		}

		List<String> unregisteredContainers = csapApis.osManager( ).findUnregisteredContainerNames( ) ;

		if ( unregisteredContainers.size( ) > 0 ) {

			serviceTotalCountMap.put( ProcessRuntime.unregistered.getId( ), unregisteredContainers.size( ) ) ;
			serviceTypeMap.put( ProcessRuntime.unregistered.getId( ), ProcessRuntime.unregistered.getId( ) ) ;
			serviceRuntimeMap.put( ProcessRuntime.unregistered.getId( ), ProcessRuntime.os.getId( ) ) ;
			activeServiceReport.put( ProcessRuntime.unregistered.getId( ), unregisteredContainers.size( ) ) ;

		}

		csapApis.application( )
				.getActiveProject( )
				.getServicesWithKubernetesFiltering( csapApis.application( ).getCsapHostName( ) )
				.filter( service -> {

					return service.getLifecycle( ).startsWith( clusterFilter ) ;

				} )
				.forEach( serviceInstance -> {

					logger.debug( "{} : Active {}", serviceInstance.getName( ), serviceInstance.getDefaultContainer( )
							.isActive( ) ) ;

					var serviceName = serviceInstance.getName( ) ;
					var serviceWithConfiguration = csapApis.application( ).findServiceByNameOnCurrentHost(
							serviceName ) ;

					// k8s has multiple containers, everything is 1
					var numberToAdd = serviceInstance.getContainerStatusList( ).size( ) ;

					if ( serviceWithConfiguration != null
							&& serviceWithConfiguration.isAggregateContainerMetrics( ) ) {

						// only count aggregegated service as one
						numberToAdd = 1 ;

					}

					var totalServicesNow = 0 ;

					if ( serviceTotalCountMap.containsKey( serviceName ) ) {

						totalServicesNow = serviceTotalCountMap.get( serviceName ) ;

					} else {

						serviceTypeMap.put( serviceName, serviceInstance.getServerUiIconType( ) ) ;

						serviceRuntimeMap.put( serviceName, serviceInstance.getUiRuntime( ) ) ;

						activeServiceReport.put( serviceName, 0 ) ;

					}

					serviceTotalCountMap.put( serviceName, numberToAdd + totalServicesNow ) ;

					if ( serviceInstance.is_cluster_kubernetes( ) && serviceInstance.isKubernetesMaster( ) ) {

						// check for override.
						int totalServiceCount = serviceInstance.getKubernetesReplicaCount( ).asInt( numberToAdd
								+ totalServicesNow ) ;
						serviceTotalCountMap.put( serviceName, totalServiceCount ) ;

					}

					if ( serviceInstance.hasProcessGroup( ) ) {

						var cluster = serviceWithConfiguration.getCluster( ) ;
						ObjectNode clusterReport ;

						if ( processGroupReport.has( cluster ) ) {
							clusterReport = (ObjectNode)processGroupReport.path( cluster ) ;
						} else {
							clusterReport = processGroupReport.putObject( cluster ) ;
						}
						
						if ( serviceInstance.getDefaultContainer( ).isActive( )  ) {
							clusterReport.put( "active", 1 + clusterReport.path( "active" ).asInt( ) ) ;
						} else {
							clusterReport.put( "stopped", 1 + clusterReport.path( "stopped" ).asInt( ) ) ;
						}
						
					}

					if ( serviceInstance.is_files_only_package( )
							|| serviceInstance.getDefaultContainer( ).isActive( ) ) {

						int runningNow = activeServiceReport.path( serviceName ).asInt( ) ;
						totalServicesActive.incrementAndGet( ) ;
						activeServiceReport.put( serviceName, numberToAdd + runningNow ) ;

					}

				} ) ;

		ObjectNode hostStatusNode = hostMapNode.putObject( csapApis.application( ).getCsapHostName( ) ) ;
		hostStatusNode.put( "serviceTotal", csapApis.application( ).getServicesOnHost( ).size( ) ) ;
		hostStatusNode.put( "serviceActive", totalServicesActive.get( ) ) ;
		ObjectNode hostReport = csapApis.application( ).healthManager( ).build_host_status_using_cached_data( ) ;

		hostStatusNode.set( "vmLoggedIn", hostReport.path( "vmLoggedIn" ) ) ;

		hostStatusNode.put( "cpuCount",
				Integer.parseInt( hostReport.path( "cpuCount" ).asText( ) ) ) ;
		double newKB = Math
				.round( Double.parseDouble( hostReport.path( "cpuLoad" ).asText( ) ) * 10.0 ) / 10.0 ;
		hostStatusNode.put( "cpuLoad", newKB ) ;

		hostStatusNode.put( "du", hostReport.path( "du" ).longValue( ) ) ;

		int totalServices = csapApis.application( ).getServicesOnHost( ).size( ) ;
		int totalHosts = 1 ;

		summaryJson.put( "totalHostsActive", totalServicesActive.get( ) ) ;

		summaryJson.put( "totalServices", totalServices ) ;

		summaryJson.put( "totalServicesActive", totalServicesActive.get( ) ) ;

		summaryJson.put( "totalHosts", totalHosts ) ;

		return ;

	}

}
