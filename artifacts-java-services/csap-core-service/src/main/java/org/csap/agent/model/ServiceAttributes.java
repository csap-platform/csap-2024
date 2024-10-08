/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.model;

import java.util.Arrays ;
import java.util.List ;
import java.util.stream.Stream ;

import org.csap.agent.container.C7 ;

/**
 *
 * @author someDeveloper
 */
public enum ServiceAttributes {

	serviceType( "server" ), port( "port" ),
	description( "description" ), deploymentNotes( "deploymentNotes" ), documentation( "docUrl" ),
	serviceUrl( "url" ),
	//
	// adhoc configuration for rare cases. Placed up front to enable semantic
	// checks
	//
	metaData( "metaData" ),
	isDataStore( "isDataStore" ), isMessaging( "isMessaging" ), isTomcatAjp( "isTomcatAjp" ),

	//
	// Core OS
	//
	startOrder( "autoStart" ), folderToMonitor( "disk" ),
	readme( "read-me" ), // critical ordering
	parameters( "parameters" ), // critical ordering
	environmentVariables( "environmentVariables" ),
	environmentOverload( "environment-overload" ),

	//
	// match multiple instances on a single host
	//
	osProcessPriority( "osProcessPriority" ), processFilter( "processFilter" ), processChildren( "processChildren" ),
	processGroup( "processGroup" ),
	autoKillEnabled( "autoKillEnabled" ),

	//
	// logging
	//
	logFolder( "logDirectory" ), logDefaultFile( "defaultLogToShow" ), logFilter( "logRegEx" ), logJournalServices(
			"logJournalServices" ),
	applicationFolder( "appDirectory" ), statsFolder( "statsDirectory" ), propertyFolder( "propDirectory" ), libraryFolder( "libDirectory" ),

	//
	// remoteCollection - hosts assigned in order
	//
	remoteCollections( "remoteCollections" ),
	//
	// deployment
	//
	deployFromSource( "source" ), deployFromRepository( "maven" ), versionCommand( "versionCommand" ),
	deployTimeMinutes( "deployTimeoutMinutes" ),
	//
	//
	dockerSettings( C7.definitionSettings.val( ) ), runUsingDocker( "runUsingDocker" ),
	//
	// monitoring and performance
	//
	osAlertLimits( "alerts" ), notifications( "notifications" ), javaAlertWarnings( "javaWarnings" ),
	javaJmxPort( "jmxPort" ),
	performanceApplication( "performance" ),
	//
	// service jobs
	//
	scheduledJobs( "scheduledJobs" ),

	//
	// tomcat only
	//
	// servletContext( "context" ),
	// servletThreads( "servletThreadCount" ), servletAccept( "servletAccept" ),
	// servletMaxConnections( "servletMaxConnections" ), servletTimeoutMs(
	// "servletTimeoutMs" ),
	// cookieName( "cookieName" ), cookiePath( "cookiePath" ), cookieDomain(
	// "cookieDomain" ),
	// httpCompression( "compression" ), httpCompressTypes( "compressableMimeType"
	// ),

	// ref. http://tomcat.apache.org/connectors-doc/reference/workers.html
	webServerTomcat( "apacheModJk" ), webServerReWrite( "apacheModRewrite" );

	private String jsonKey ;

	private ServiceAttributes ( String jsonKey ) {

		this.jsonKey = jsonKey ;

	}

	public String json ( ) {

		return jsonKey ;

	}

	public static Stream<ServiceAttributes> stream ( ) {

		return Arrays.stream( ServiceAttributes.values( ) ) ;

	}

	public enum FileAttributes {
		name( "name" ), content( "content" ), external( "external" ), lifecycle( "lifecycle" ),
		newFile( "newFile" ), deleteFile( "deleteFile" ), contentUpdated( "contentUpdated" );

		public String json = "" ;

		private FileAttributes ( String json ) {

			this.json = json ;

		}
	}

	static public List<String> preferredOrder ( ) {

		return List.of(
				Project.DEFINITION_SOURCE,
				ServiceAttributes.serviceType.json( ),
				ServiceAttributes.serviceType.json( ) + "-about",
				ServiceAttributes.versionCommand.json( ),
				ServiceAttributes.startOrder.json( ),
				ServiceAttributes.port.json( ),
				ServiceAttributes.javaJmxPort.json( ),
				ServiceAttributes.description.json( ),
				ServiceAttributes.readme.json( ),
				ServiceAttributes.documentation.json( ),
				ServiceAttributes.serviceUrl.json( ),
				ServiceAttributes.processFilter.json( ),
				ServiceAttributes.processGroup.json( ),
				ServiceAttributes.autoKillEnabled.json( ),
				ServiceAttributes.applicationFolder.json( ),
				ServiceAttributes.statsFolder.json( ),
				ServiceAttributes.propertyFolder.json( ),
				ServiceAttributes.osAlertLimits.json( ),
				ServiceAttributes.isDataStore.json( ),
				ServiceAttributes.isMessaging.json( ),
				ServiceAttributes.isTomcatAjp.json( ),
				ServiceAttributes.osProcessPriority.json( ),
				ServiceAttributes.parameters.json( ),
				ServiceAttributes.parameters.json( )+"-alt",
				ServiceAttributes.parameters.json( )+"-about",
				ServiceAttributes.parameters.json( )+"-notes",
				ServiceAttributes.environmentVariables.json( ),
				ServiceAttributes.environmentVariables.json( )+"-alt",
				ServiceAttributes.environmentVariables.json( )+"-notes",
				ServiceAttributes.environmentOverload.json( ),
				ServiceAttributes.deployTimeMinutes.json( ),
				ServiceAttributes.deployFromSource.json( ),
				ServiceAttributes.deployFromRepository.json( ),
				ServiceAttributes.logFolder.json( ),
				ServiceAttributes.logDefaultFile.json( ),
				ServiceAttributes.logFilter.json( ),
				ServiceAttributes.logJournalServices.json( ),
				ServiceAttributes.logFilter.json( ),
				ServiceAttributes.runUsingDocker.json( ),
				ServiceAttributes.dockerSettings.json( ),
				ServiceAttributes.scheduledJobs.json( ),
				ServiceAttributes.performanceApplication.json( ) ) ;

	}

}
