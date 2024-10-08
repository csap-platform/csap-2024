package org.csap.agent.ui.explorer;

import java.util.Map ;

import jakarta.inject.Inject ;

import org.csap.agent.CsapApis ;
import org.csap.agent.container.C7 ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.annotation.JsonInclude.Include ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController
@RequestMapping ( CrioExplorer.CRIO_URL )
public class CrioExplorer {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	final public static String CRIO_URL = ContainerExplorer.EXPLORER_URL + "/crio" ;

	Application csapApp ;
	OsManager osManager ;
	CsapApis csapApis ;
	CsapEvents csapEventClient ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	static ObjectMapper nonNullMapper ;
	static {

		nonNullMapper = new ObjectMapper( ) ;
		nonNullMapper.setSerializationInclusion( Include.NON_NULL ) ;

	}

	@Inject
	public CrioExplorer (
			Application csapApp,
			CsapApis csapApis,
			OsManager osManager,
			CsapEvents csapEventClient ) {

		this.csapApp = csapApp ;
		this.csapApis = csapApis ;
		this.osManager = osManager ;
		this.csapEventClient = csapEventClient ;

	}

	@GetMapping ( "/containers" )
	public JsonNode crioContainersListing ( )
		throws Exception {

		if ( ! csapApis.isCrioInstalledAndActive( ) ) {

			return csapApis.crio( ).build_not_configured_listing( ) ;

		}

		if ( Application.isRunningOnDesktop( ) ) {

			var remoteListing = csapApis.crio( ).buildRemoteListing( CRIO_URL + "/containers", null ) ;

			if ( remoteListing.path( 0 ).has( "error" ) ) {

				var stubbedListing = csapApis.crio( ).containerListing( ) ;

				var errorEntry = stubbedListing.addObject( ) ;
				errorEntry.put( "label", "Failed to perform remote listing: " + csapApis.containerHostConfigured( ) ) ;
				errorEntry.put( "folder", true ) ;
				errorEntry.putObject( "attributes" ) ;

				return stubbedListing ;

			}

		}

		return csapApis.crio( ).containerListing( ) ;

	}

	@GetMapping ( "/container/info" )
	public JsonNode crioContainerInfo ( String id )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;
		result.put( "csapNoSort", true ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			return csapApis.crio( ).buildRemoteListing( CRIO_URL + "/container/info", Map.of( "id", id ) ) ;

		}

		try {

			var info = csapApis.crio( ).containerInspect( id ) ;
			return nonNullMapper.convertValue( info, ObjectNode.class ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed getting info {}, {}", id, reason ) ;
			result.put( C7.error.val( ), "Docker API Failure: " + e.getClass( ).getName( ) ) ;
			// result.put( DockerJson.errorReason.json(), reason ) ;

		}

		return result ;

	}

}
