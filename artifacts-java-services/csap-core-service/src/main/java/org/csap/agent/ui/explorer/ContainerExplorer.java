package org.csap.agent.ui.explorer;

import java.util.Optional ;

import jakarta.inject.Inject ;
import jakarta.servlet.http.HttpServletResponse ;

import org.csap.agent.CsapApis ;
import org.csap.agent.container.C7 ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.helpers.CSAP ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.MediaType ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.annotation.JsonInclude.Include ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.github.dockerjava.api.DockerClient ;
import com.github.dockerjava.api.command.InspectContainerResponse ;
import com.github.dockerjava.api.model.Container ;
import com.github.dockerjava.api.model.Info ;

@RestController
@RequestMapping ( ContainerExplorer.EXPLORER_URL + "/docker" )
public class ContainerExplorer {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;
	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	public final static String EXPLORER_URL = "/explorer" ;

	@Inject
	public ContainerExplorer (
			CsapApis csapApis,
			CsapEvents csapEventClient ) {

		this.csapApis = csapApis ;
		this.csapEventClient = csapEventClient ;

	}

	CsapApis csapApis ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	CsapEvents csapEventClient ;

	// https://github.com/docker-java/docker-java
	@Autowired ( required = false )
	DockerClient dockerClient ;

	@GetMapping ( "/configuration" )
	public JsonNode dockerConfiguration ( ) {

		if ( ! csapApis.isContainerProviderInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		Info info = dockerClient.infoCmd( ).exec( ) ;
		ObjectNode result = jacksonMapper.convertValue( info, ObjectNode.class ) ;

		return result ;

	}

	private ArrayNode build_not_configured_listing ( ) {

		ArrayNode listing = jacksonMapper.createArrayNode( ) ;
		ObjectNode item = listing.addObject( ) ;
		item.put( C7.list_label.val( ), C7.error.val( ) + "Docker not configured" ) ;
		item.put( "folder", false ) ;
		item.put( C7.error.val( ), "Docker not configured" ) ;
		return listing ;

	}

	@GetMapping ( "/networks" )
	public JsonNode networks ( ) {

		if ( ! csapApis.isContainerProviderInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = csapApis.containerIntegration( ).networkList( ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( C7.error.val( ), "No networks defined" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/volumes" )
	public JsonNode volumes ( ) {

		if ( ! csapApis.isContainerProviderInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = csapApis.containerIntegration( ).volumeList( ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( C7.error.val( ), "No volumes defined" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/images" )
	public JsonNode imagesList (
									boolean showFilteredItems ) {

		if ( ! csapApis.isContainerProviderInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		return csapApis.containerIntegration( ).imageListWithDetails( showFilteredItems ) ;

	}

	@GetMapping ( "/image/info" )
	public ObjectNode imageInfo (
									String id ,
									String name ) {

		return csapApis.containerIntegration( ).imageInfo( name ) ;

	}

	@GetMapping ( "/image/pull/progress" )
	public String imagePullProgress (
										@RequestParam ( defaultValue = "0" ) int offset ) {

		return csapApis.containerIntegration( ).getLastResults( offset ) ;

	}

	@PostMapping ( "/image/pull" )
	public ObjectNode imagePull (
									String id ,
									String name,
									String repoUser,
									String repoPass)
		throws Exception {

		issueAudit( "Pulling Image: " + name, null ) ;

		csapApis.application().setCachedRepo( repoUser, repoPass );

		return csapApis.containerIntegration( ).imagePull( name,  null, 600, 1 ) ;

	}

	@PostMapping ( "/image/remove" )
	public ObjectNode imageRemove (
									boolean force ,
									String id ,
									String name )
		throws Exception {

		logger.info( "force: {} ,id: {}, name: {}", force, id, name ) ;

		issueAudit( "Removing Image: " + name, id ) ;

		return csapApis.containerIntegration( ).imageRemove( force, id, name ) ;

	}

	@DeleteMapping ( "/image/clean/{days}/{minutes}" )
	public ObjectNode imageClean (
									@PathVariable int days ,
									@PathVariable int minutes )
		throws Exception {

		logger.info( "Cleaning images older then days: {}, minutes: {}", days, minutes ) ;

		issueAudit( "Cleaning Images older then: " + days + " Days and " + minutes + " minutes", null ) ;

		return csapApis.osManager( ).cleanImages( days, minutes ) ;

	}

	@GetMapping ( "/containers" )
	public ArrayNode containersListing ( boolean showFilteredItems )
		throws Exception {

		if ( ! csapApis.isContainerProviderInstalledAndActive( ) ) {

			return build_not_configured_listing( ) ;

		}

		return csapApis.containerIntegration( ).containerListing( showFilteredItems ) ;

	}

	static ObjectMapper nonNullMapper ;
	static {

		nonNullMapper = new ObjectMapper( ) ;
		nonNullMapper.setSerializationInclusion( Include.NON_NULL ) ;

	}

	@GetMapping ( "/container/info" )
	public ObjectNode containerInfo (
										String id ,
										String name )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;
		result.put( "csapNoSort", true ) ;

		try {

			InspectContainerResponse info = csapApis.containerIntegration( ).containerConfiguration( name ) ;
			return nonNullMapper.convertValue( info, ObjectNode.class ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed getting info {}, {}", name, reason ) ;
			result.put( C7.error.val( ), "Docker API Failure: " + e.getClass( ).getName( ) ) ;
			// result.put( DockerJson.errorReason.json(), reason ) ;

			var inspectDetails = csapApis.containerIntegration( ).inspectByCli( name ) ;

			result.setAll( inspectDetails ) ;

		}

		return result ;

	}

	@GetMapping ( "/container/sockets" )
	public ObjectNode containerSockets (
											String id ,
											String name )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		try {

			InspectContainerResponse info = csapApis.containerIntegration( ).containerConfiguration( name ) ;

			String socketInfo = csapApis.containerIntegration( ).dockerOpenSockets( info.getState( ).getPid( ) + "" ) ;
			result.put( "result", "socket info for pid: " + info.getState( ).getPid( ) ) ;
			result.put( C7.response_plain_text.val( ), socketInfo ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed starting {}, {}", name, reason ) ;
			result.put( C7.error.val( ), "Failed starting: " + name ) ;
			result.put( C7.errorReason.val( ), reason ) ;

		}

		return result ;

	}

	@GetMapping ( "/container/processTree" )
	public ObjectNode containerProcessTree (
												String id ,
												String name )
		throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		try {

			InspectContainerResponse info = csapApis.containerIntegration( ).containerConfiguration( name ) ;

			result = csapApis.osManager( ).buildProcessReport( info.getState( ).getPid( ) + "", name ) ;
			result.put( "result", "process tree for pid: " + info.getState( ).getPid( ) ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed starting {}, {}", name, reason ) ;
			result.put( C7.error.val( ), "Failed starting: " + name ) ;
			result.put( C7.errorReason.val( ), reason ) ;

		}

		return result ;

	}

	@GetMapping ( value = "/container/tail" , produces = MediaType.TEXT_HTML_VALUE )
	public void containerTailStream (
										String id ,
										String name ,
										HttpServletResponse response ,
										@RequestParam ( defaultValue = "500" ) int numberOfLines )
		throws Exception {

		csapApis.containerIntegration( ).containerTailStream( id, name, response, numberOfLines ) ;

	}

	@GetMapping ( "/container/tail" )
	public ObjectNode containerTail (
										String id ,
										String name ,
										@RequestParam ( defaultValue = "500" ) int numberOfLines ,
										@RequestParam ( defaultValue = "0" ) int since )
		throws Exception {

		return csapApis.containerIntegration( ).containerTail( id, name, numberOfLines, since ) ;

	}

	private void issueAudit ( String commandDesc , String details ) {

		csapEventClient.publishUserEvent( C7.definitionSettings.val( ),
				securitySettings.getRoles( ).getUserIdFromContext( ),
				commandDesc, details ) ;

	}

	@PostMapping ( "/container/cpuQuota" )
	public ObjectNode containerCpuQuota (
											String name ,
											Integer periodMs ,
											Integer quotaMs )
		throws Exception {

		issueAudit( name + ": Updating CPU quota: " + quotaMs + "ms, period: " + periodMs, null ) ;

		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		try {

			result.put( C7.response_plain_text.val( ),
					csapApis.containerIntegration( ).updateContainerCpuAllow( periodMs, quotaMs,
							csapApis.containerIntegration( ).findContainerByName( name ).get( ).getId( ) ) ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed updating {}, {}", name, reason ) ;

			result.put( C7.error.val( ), "Failed updaing: " + name ) ;
			result.put( C7.errorReason.val( ), reason ) ;

		}

		return result ;

	}

	@PostMapping ( "/container/create" )
	public ObjectNode containerCreate (
										boolean start ,
										String image ,
										String name ,
										String command ,
										String entry ,
										String workingDirectory ,
										String network ,
										String restartPolicy ,
										String runUser ,
										String ports ,
										String volumes ,
										String environmentVariables ,
										String limits )
		throws Exception {

		issueAudit( "creating container: " + name + " from image: " + image, null ) ;

		return csapApis.containerIntegration( ).containerCreate(
				null, start, image, name,
				command, entry, workingDirectory,
				network, restartPolicy, runUser,
				ports, volumes, environmentVariables,
				limits ) ;

	}

	@PostMapping ( "/container/start" )
	public ObjectNode containerStart (
										String id ,
										String name )
		throws Exception {

		issueAudit( "starting container: " + name + "id: " + id, null ) ;
		ObjectNode result = jacksonMapper.createObjectNode( ) ;

		try {

			String targetId = getContainerId( id, name ) ;

			if ( targetId != null && ! targetId.isEmpty( ) ) {

				dockerClient.startContainerCmd( targetId ).exec( ) ;
				result.put( "result", "Started container: " + name + " id:" + targetId ) ;
				InspectContainerResponse info = dockerClient.inspectContainerCmd( targetId ).exec( ) ;

				result.set( "state", jacksonMapper.convertValue( info.getState( ), ObjectNode.class ) ) ;

			} else {

				result.put( C7.error.val( ), "Container not found: " + name ) ;

			}

		} catch ( Exception e ) {

			String reason = CSAP.buildCsapStack( e ) ;
			logger.warn( "Failed starting {}, {}", name, reason ) ;

			if ( e.getClass( ).getSimpleName( ).toLowerCase( ).contains( "notmodified" ) ) {

				result.put( "result", "Container was already running: " + name ) ;

			} else {

				result.put( C7.error.val( ), "Failed starting: " + name ) ;
				result.put( C7.errorReason.val( ), reason ) ;

			}

		}

		return result ;

	}

	private String getContainerId ( String id , String name ) {

		String targetId = id ;

		if ( id == null || id.isEmpty( ) ) {

			Optional<Container> matchContainer = csapApis.containerIntegration( ).findContainerByName( name ) ;
			targetId = matchContainer.get( ).getId( ) ;

		}

		return targetId ;

	}

	@PostMapping ( "/container/stop" )
	public ObjectNode containerStop (
										String id ,
										String name ,
										boolean kill ,
										int stopSeconds )
		throws Exception {

		issueAudit( "starting container: " + name, null ) ;

		return csapApis.containerIntegration( ).containerStop( id, name, kill, stopSeconds ) ;

	}

	@PostMapping ( "/container/remove" )
	public ObjectNode containerRemove (
										String id ,
										String name ,
										boolean force )
		throws Exception {

		issueAudit( "removeing container: " + name, null ) ;
		return csapApis.containerIntegration( ).containerRemove( id, name, force ) ;

	}
}
