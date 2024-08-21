/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader;

import org.csap.agent.CsapBareTest;
import org.csap.agent.CsapConstants;
import org.csap.agent.TestServices;
import org.csap.agent.container.C7;
import org.csap.agent.linux.ServiceJobRunner;
import org.csap.agent.model.ProcessRuntime;
import org.csap.agent.model.Project;
import org.csap.agent.model.ProjectLoader;
import org.csap.agent.model.ServiceInstance;
import org.csap.helpers.CSAP;
import org.csap.helpers.CsapApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class Basic_Application extends CsapBareTest {

	String definitionPath = "/definitions/basic-definition.json" ;
	File testDefinition = new File( getClass( ).getResource( definitionPath ).getPath( ) ) ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		CSAP.setLogToInfo( Project.class.getName( ) ) ;
		assertThat( getApplication( ).load_junit_definition_start_collectors( false, testDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;

		getOsManager( ).wait_for_initial_process_status_scan( 10 ) ;

		CSAP.setLogToInfo( Project.class.getName( ) ) ;

	}

	String csapPackageName = "default-definition-package" ;



	@Test
	void verify_lifecycle_settings ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( getRootProjectSettings( ).getNumberWorkerThreads( ) ).isEqualTo( 5 ) ;

		assertThat( getRootProjectSettings( ).getAdminToAgentTimeoutSeconds( ) ).isEqualTo( 5 ) ;

		assertThat( getRootProjectSettings( ).getInfraTests( ).getCpuIntervalMinutes( ) ).isEqualTo( 30 ) ;

	}

	List<String> clusterHosts ( String clustername ) {

		var clusters = getApplication( ).getActiveProject( ).getClustersToHostMapInCurrentLifecycle( ) ;
		logger.info( "{} cluster has hosts: {}", clustername, clusters ) ;

		return clusters.get( clustername ) ;

	}



	@Test
	void verify_clusters ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( clusterHosts( "simple-cluster" ) ).containsExactly( "localhost" ) ;

	}


	@Test
	void verify_public_hosts ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var hostSettings = getApplication( ).environmentSettings( ).getHostSettings( )  ;
		logger.info( "hostSettings: {}",  CSAP.jsonPrint( hostSettings )) ;

		assertThat( hostSettings.path( "private-to-public" ).path( "localhost" ).asText() )
				.isEqualTo( "simple.public.com" ) ;

	}

	ServiceInstance service ( String name ) {

		return getApplication( ).findServiceByNameOnCurrentHost( name ) ;

	}

	@Test
	void verify_service_runtimes ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		//
		// Core
		//

		assertThat( service( TestServices.agent.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.springboot ) ;

		assertThat( service( TestServices.csap_verify.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.springboot ) ;


	}


}
