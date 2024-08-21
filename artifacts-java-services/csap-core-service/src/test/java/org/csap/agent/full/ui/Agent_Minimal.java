package org.csap.agent.full.ui;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;

import jakarta.inject.Inject ;

import org.csap.agent.CsapApis ;
import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.CustomProperty ;
import org.csap.agent.model.Application ;
import org.csap.agent.ui.rest.ApplicationBrowser ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Tag ;
import org.junit.jupiter.api.Test ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc ;
import org.springframework.test.annotation.DirtiesContext ;

@Tag ( "full" )
@CsapBareTest.Agent_Full
@DirtiesContext
@AutoConfigureMockMvc
class Agent_Minimal {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static {

		CsapApplication.initialize( "Test Setup Complete" ) ;

	}

	@Inject
	Application csapApp ;

	@Inject
	ApplicationBrowser appBrowser ;

	@Inject
	CsapApis csapApis ;

	File minimalApplication = new File(
			Agent_Minimal.class.getResource( "minimal-project.json" ).getPath( ) ) ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		// CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;
		assertThat( csapApis.application( ).load_junit_definition( false, minimalApplication, false ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;
		// CSAP.setLogToInfo( ProjectLoader.class.getName( ) ) ;

		logger.info( CSAP.buildDescription( "test definition", "loaded", minimalApplication ) ) ;

	}

	CustomProperty findProperty ( String name ) {

		return csapApis.application( ).environmentSettings( ).getCustomProperties( ).stream( )
				.filter( prop -> prop.getRawName( ).equals( name ) )
				.findFirst( )
				.get( ) ;

	}

	@Test
	public void verify_custom_template_variables ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;


		var customProperties = csapApis.application( ).environmentSettings( ).getCustomProperties( ) ;
		logger.info( "customProperties: {}", customProperties ) ;

		assertThat( customProperties.size( ) ).isEqualTo( 9 ) ;

		var CP_demoFolder1 = findProperty( "demo-folder-1" ) ;
		var CP_universalPassword = findProperty( "universal-password" ) ;
		var CP_demoPropertyFolder = findProperty( "demo-property-folder" ) ;
		var CP_prometheusPort = findProperty( "prometheus-port" ) ;
		var CP_demoOverKey = findProperty( "demo-over-key" ) ;
		

		//
		// Basic value checks
		//
		assertThat( CP_demoFolder1.getName( ) )
				.isEqualTo( CsapConstants.CSAP_VARIABLE_PREFIX + "demo-folder-1" ) ;
		
		assertThat( CP_universalPassword.getValue( ) )
				.as( "verify property value can be read from file" )
				.isEqualTo( "Un8FYMCUw5wMZ77cLOfdAg47pruoF" ) ;
		
		assertThat( CP_demoOverKey.getValue( ) )
				.as( "verify environment value overrides base value" )
				.isEqualTo( "demo-env-val" ) ;
		

		//
		//  verify agent template variables are updated
		//
		var agentService = csapApis.application( ).findFirstServiceInstanceInLifecycle( CsapConstants.AGENT_NAME ) ;

		assertThat( agentService.toString( ) ).contains( CsapConstants.AGENT_NAME ) ;
		assertThat( agentService.resolveRuntimeVariables(
				CP_demoFolder1.getName( ) ) )
						.isEqualTo( CP_demoFolder1.getValue( ) ) ;

		assertThat( agentService.getLaunchCred( ) )
				.isEqualTo( CP_universalPassword.getValue( ) ) ;
		
		var launchers = appBrowser.launchers( ) ;

		logger.info( "launchers: {}", CSAP.jsonPrint( launchers ) ) ;

		assertThat( launchers.path( 2 ).path( "label" ).asText( ) ).isEqualTo( "tenant-demo" ) ;

		var agentLauncher = appBrowser.serviceLauncher( CsapConstants.AGENT_NAME, 2 ) ;
//		logger.info( "agentLauncher: {}", CSAP.jsonPrint( agentLauncher ) ) ;
		logger.info( CSAP.buildDescription(  "agentLauncher", agentLauncher  )) ;

		assertThat( agentLauncher.path( "launchCred" ).asText( ) )
				.isEqualTo( CP_universalPassword.getValue( ) ) ;
		assertThat( agentLauncher.path( "location" ).asText( ) )
				.isEqualTo( "http://localhost.csap.org:8011/app-browser?defaultService=csap-agent" ) ;


		var simpleServiceLauncher = appBrowser.serviceLauncher("simple-service", 1 ) ;
		logger.info( CSAP.buildDescription(  "simpleServiceLauncher", simpleServiceLauncher  )) ;
		assertThat( simpleServiceLauncher.path( "location" ).asText( ) )
				.isEqualTo( "http://localhost.csap.org:8241/simple-service" ) ;
		assertThat( simpleServiceLauncher.path( "reason" ).asText( ) )
				.contains( "request host not found:" ) ;

		//var folderProperty3 = customProperties.get( 2 ) ;

		logger.info( "CP_demoPropertyFolder: {}", CP_demoPropertyFolder ) ;

		assertThat( agentService
				.resolveRuntimeVariables(
						CP_demoPropertyFolder.getName( ) ) )
								.isEqualTo( CP_demoPropertyFolder.getValue( ) ) ;


		logger.info( "universalPasswordCustomProperty: {}", CP_universalPassword ) ;

		assertThat( agentService
				.resolveRuntimeVariables(
						CP_universalPassword.getName( ) ) )
								.isEqualTo( CP_universalPassword.getValue( ) ) ;


		logger.info( "prometheusPortCustomProperty: {}", CP_prometheusPort ) ;

		assertThat( agentService
				.resolveRuntimeVariables(
						CP_prometheusPort.getName( ) ) )
								.isEqualTo( "12346" ) ;

	}

}
