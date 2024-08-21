package org.csap.agent.ui.editor;

import java.io.BufferedWriter ;
import java.io.File ;
import java.io.IOException ;
import java.nio.charset.Charset ;
import java.nio.file.Files ;
import java.util.ArrayList ;
import java.util.List ;
import java.util.Optional ;

import org.apache.commons.io.FileUtils ;
import org.csap.agent.model.ServiceAttributes ;
import org.csap.agent.model.ServiceAttributes.FileAttributes ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.core.type.TypeReference ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class PendingDefinitionManager {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	PendingOperations pendingOperations ;
	ServiceResources serviceResources ;
	private ObjectMapper jacksonMapper ;

	public PendingDefinitionManager ( ServiceResources serviceResources, ObjectMapper jacksonMapper ) {

		this.jacksonMapper = jacksonMapper ;
		this.serviceResources = serviceResources ;
		pendingOperations = new PendingOperations( serviceResources ) ;
		clearAll( ) ;

	}

	public void apply_to_checked_out_folder ( BufferedWriter writer , File checkedOutSourceFolder )
		throws IOException {

		logger.info( "queued_operations: {}", queued_operations ) ;
		writer.write( "\n\t Queued Operations: " + queued_operations ) ;

		for ( PendingOperations.FileOperation fileOperation : queued_operations ) {

			try {

				String results = fileOperation.apply_to_checked_out_folder( writer, checkedOutSourceFolder ) ;
				writer.write( "\n\t" + results ) ;

			} catch ( Exception e ) {

				logger.warn( "Failed applying operations: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		logger.info( "Removing: {}", serviceResources.workingFolder( ).getAbsolutePath( ) ) ;
		writer.write( "\n\t Removing: " + serviceResources.workingFolder( ).getAbsolutePath( ) ) ;
		FileUtils.deleteQuietly( serviceResources.workingFolder( ) ) ;

	}

	List<PendingOperations.FileOperation> queued_operations = new ArrayList<>( ) ;

	public List<PendingOperations.FileOperation> getQueued_operations ( ) {

		return queued_operations ;

	}

	public void addServiceCopy ( String currentName , String copyName ) {

		logger.info( "currentName: '{}', copyName: '{}'", currentName, copyName ) ;

		queued_operations.add( pendingOperations.new ServiceCopy( currentName, copyName ) ) ;

	}

	public void addServiceRename ( String currentName , String newName ) {

		logger.info( "currentName: '{}', newName: '{}'", currentName, newName ) ;

		queued_operations.add( pendingOperations.new ServiceRename( currentName, newName ) ) ;

	}

	public void addServiceDelete ( String currentName ) {

		logger.info( "deleting: '{}'", currentName ) ;

		queued_operations.add( pendingOperations.new ServiceDelete( currentName ) ) ;

	}

	public void clearAll ( ) {

		logger.info( "clearing resource request backlog" ) ;
		FileUtils.deleteQuietly( serviceResources.workingFolder( ) ) ;

		queued_operations.clear( ) ;

	}

	public List<String> processServiceResourceEdits ( ObjectNode serviceDefinition , String serviceName ) {

		var ignoredFiles = new ArrayList<String>( ) ;
		logger.debug( "{} serviceDefinition: {}", serviceName, CSAP.jsonPrint( serviceDefinition ) ) ;
		// File resourceDir = csapApp.getResourcesFolder( serviceName );
		var serviceResourceDir = serviceResources.workingFolder( ) ;

		// only internal files are stored.
		var fileDefinitions = (ArrayNode) serviceDefinition.path( DefinitionRequests.DEFINITION_FILES ) ;
		// ArrayNode internalOnly = extractEmbeddedServiceFiles( files ) ;
		// serviceDefinition.set( DEFINITION_FILES, internalOnly ) ;

		fileDefinitions.forEach( fileDefinition -> {

			var targetLife = fileDefinition.path( ServiceAttributes.FileAttributes.lifecycle.json ).asText( "common" ) ;
			var targetName = fileDefinition.path( ServiceAttributes.FileAttributes.name.json ).asText( "default-name" ) ;
			// String targetPath = serviceName + "/resources/" + targetLife + "/" +
			// targetName ;
			var targetPath = serviceName + "/" + targetLife + "/" + targetName ;

			if ( fileDefinition.path( ServiceAttributes.FileAttributes.external.json ).asBoolean( true ) ) {

				if ( fileDefinition.has( ServiceAttributes.FileAttributes.newFile.json )
						&& fileDefinition.path( ServiceAttributes.FileAttributes.newFile.json ).asBoolean( true ) ) {

					storeFilesToDisk( serviceResourceDir, targetPath, fileDefinition ) ;

					queued_operations.add( pendingOperations.new ServiceResourceAdd( targetPath ) ) ;

				} else if ( fileDefinition.has( ServiceAttributes.FileAttributes.deleteFile.json )
						&& fileDefinition.get( ServiceAttributes.FileAttributes.deleteFile.json ).asBoolean( ) ) {

					queued_operations.add( pendingOperations.new ServiceResourceDelete( targetPath ) ) ;

				} else if ( fileDefinition.has( ServiceAttributes.FileAttributes.contentUpdated.json )
						&& fileDefinition.get( ServiceAttributes.FileAttributes.contentUpdated.json ).asBoolean( ) ) {

					storeFilesToDisk( serviceResourceDir, targetPath, fileDefinition ) ;
					queued_operations.add( pendingOperations.new ServiceResourceAdd( targetPath ) ) ;

				} else {

					logger.info( "File did not contain any state: {}", targetPath ) ;
					
					var fileDefinitionsOnDisk = jacksonMapper.createObjectNode( ) ;
					
					serviceResources.addResourceFilesToDefinition( fileDefinitionsOnDisk, serviceName, ".*", ".*" ) ;
					
					var testFiles = fileDefinitionsOnDisk.path( DefinitionRequests.DEFINITION_FILES ) ;
					boolean addTargetFile = testFiles.isMissingNode( ) ;

					if ( testFiles.isArray( ) ) {

						logger.debug( "testFiles: {}", testFiles ) ;
						
						Optional<String> matchName = CSAP.jsonStream( testFiles )
								.map( fileDef -> fileDef.path( FileAttributes.name.json ).asText( ) )
								.filter( testName -> testName.equals( targetName ) )
								.findFirst( ) ;
						
						addTargetFile = matchName.isEmpty( ) ;

					}

					if ( addTargetFile ) {

						logger.info( "No existing file with same name found, adding as new file: {}", targetPath ) ;
						storeFilesToDisk( serviceResourceDir, targetPath, fileDefinition ) ;
						queued_operations.add( pendingOperations.new ServiceResourceAdd( targetPath ) ) ;

					} else {

						logger.warn( "Ignoring, found an existing file: {}", targetPath ) ;
						ignoredFiles.add( targetPath ) ;

					}

				}

			} else {

				logger.warn( "did not find true: {}", ServiceAttributes.FileAttributes.external.json ) ;

			}

		} ) ;

		logger.info( "Pending Operations: {}", queued_operations ) ;
		return ignoredFiles ;

	}

	private void storeFilesToDisk ( File serviceResourceFolder , String path , JsonNode propFile ) {

		logger.debug( "creating: {} in: {}", path, serviceResourceFolder.getAbsolutePath( ) ) ;

		try {

			File targetFile = new File( serviceResourceFolder.getCanonicalFile( ), path ) ;

			targetFile.getParentFile( ).mkdirs( ) ;
			logger.info( "Creating: {}", targetFile.getCanonicalPath( ) ) ;
			ArrayList<String> lines = jacksonMapper.readValue(
					propFile.path( ServiceAttributes.FileAttributes.content.json ).traverse( ),
					new TypeReference<ArrayList<String>>( ) {
					} ) ;
			Files.write( targetFile.toPath( ), lines, Charset.forName( "UTF-8" ) ) ;

		} catch ( IOException e ) {

			logger.error( "Failed creating resource file {} source: \n{}, \n {} ", path, CSAP.jsonPrint( propFile ),
					CSAP.buildCsapStack( e ) ) ;

		}

	}

}
