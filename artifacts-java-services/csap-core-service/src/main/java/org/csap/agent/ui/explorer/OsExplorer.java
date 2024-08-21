package org.csap.agent.ui.explorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.csap.agent.CsapApis;
import org.csap.agent.container.C7;
import org.csap.agent.model.Application;
import org.csap.helpers.CSAP;
import org.csap.security.config.CsapSecurityRoles;
import org.csap.security.config.CsapSecuritySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping ( OsExplorer.EXPLORER_URL + "/os" )
public class OsExplorer {

    final Logger logger = LoggerFactory.getLogger( this.getClass( ) );
    ObjectMapper jacksonMapper = new ObjectMapper( );

    public final static String EXPLORER_URL = "/explorer";

    @Inject
    public OsExplorer(
            CsapApis csapApis
    ) {

        this.csapApis = csapApis;

    }

    CsapApis csapApis;

    @Autowired ( required = false )
    CsapSecuritySettings securitySettings;


    @GetMapping ( "/memory" )
    public JsonNode memory( )
            throws Exception {


        return csapApis.osManager( ).getCachedMemoryMetrics( );

    }

    @GetMapping ( "/network/devices" )
    public JsonNode networkDevices( )
            throws Exception {

        ArrayNode networkListing = jacksonMapper.createArrayNode( );

        csapApis.osManager( )
                .networkInterfaces( ).stream( )
                .forEach( interfaceLine -> {

                    String[] interfaceFields = interfaceLine.split( " ", 3 );
                    ObjectNode item = networkListing.addObject( );
                    var state = "";
                    var stateSplit = interfaceFields[ 2 ].split( "state", 2 );

                    if ( stateSplit.length == 2 ) {

                        state = stateSplit[ 1 ].trim( ).split( " ", 2 )[ 0 ];

                    }

                    var inetSplit = interfaceFields[ 2 ].split( "inet", 2 );

                    if ( inetSplit.length == 2 ) {

                        state = inetSplit[ 1 ].trim( ).split( " ", 2 )[ 0 ] + ":" + state;

                    }

                    var index = interfaceFields[ 0 ];

                    if ( index.length( ) == 2 ) {

                        index = "0" + index;

                    }

                    item.put( "label", index + " " + interfaceFields[ 1 ] + " (" + state + ")" );
                    item.put( "description", interfaceFields[ 2 ] );
                    item.put( "folder", false );
                    item.put( "lazy", false );

                } );

        return networkListing;

    }

    @GetMapping ( "/systemctl" )
    public ObjectNode systemctl(
            ModelMap modelMap,
            HttpSession session
    ) {

        var report = jacksonMapper.createObjectNode( );

        String commandOutput = csapApis.osManager( ).systemStatus( );

        report.put( C7.response_plain_text.val( ), commandOutput );
        return report;

    }

    @PostMapping ( "/cli" )
    public ObjectNode cli(
            String parameters,
            HttpSession session
    ) {

        var report = jacksonMapper.createObjectNode( );

        if ( StringUtils.isEmpty( parameters ) ) {

            parameters = "print_section 'no command specified'";

        }
        report.put( "command", parameters );

        parameters = parameters.replaceAll( "calicoctl", "calico" );

        String commandOutput;

        if ( !securitySettings.getRoles( ).getAndStoreUserRoles( session, null )
                .contains( CsapSecurityRoles.ADMIN_ROLE ) ) {

            commandOutput = "*Permission denied: only admins may access journal entries";

        } else {

            issueAudit( "running cli: " + parameters, null );
            commandOutput = csapApis.osManager( ).cli( parameters );

        }

        report.put( C7.response_yaml.val( ), commandOutput );
        return report;

    }

    @GetMapping ( "/disk" )
    public JsonNode disk( )
            throws Exception {

        return csapApis.osManager( ).getCachedFileSystemInfo( );

    }

    @GetMapping ( "/socket/connections" )
    public JsonNode socketConnections( @RequestParam ( defaultValue = "true" ) boolean summaryReport )
            throws Exception {

        ArrayNode portListing = jacksonMapper.createArrayNode( );

        CSAP.jsonStream( csapApis.osManager( ).socketConnections( summaryReport ) )
                .forEach( portAttributes -> {

                    // //String[] interfaceFields = interfaceLine.split( " ", 4 ) ;
                    ObjectNode item = portListing.addObject( );

                    var label = portAttributes.path( "processName" ).asText( ) + ": " + portAttributes.path( "port" )
                            .asText( );

                    var description = portAttributes.path( "peer" ).asText( );
                    var relatedItems = portAttributes.path( "related" );

                    if ( relatedItems.isArray( ) ) {

                        description += "<span class='more-items'>" + relatedItems.size( ) + "</span> related items";

                    }

                    item.put( "label", label );
                    item.put( "description", description );
                    item.put( "folder", false );
                    item.put( "lazy", false );
                    item.set( "attributes", portAttributes );

                } );

        return portListing;

    }

    @GetMapping ( "/socket/listeners" )
    public JsonNode socketListeners( @RequestParam ( defaultValue = "true" ) boolean summaryReport )
            throws Exception {

        ArrayNode portListing = jacksonMapper.createArrayNode( );

        CSAP.jsonStream( csapApis.osManager( ).socketListeners( summaryReport ) )
                .forEach( portAttributes -> {

                    // //String[] interfaceFields = interfaceLine.split( " ", 4 ) ;
                    ObjectNode item = portListing.addObject( );
                    var portId = portAttributes.path( "port" ).asText( );

                    var processInfo = portAttributes.path( "details" ).asText( );
                    var users = processInfo.split( "\"", 3 );

                    if ( users.length == 3 ) {

                        processInfo = users[ 1 ];

                    }

                    var relatedItems = portAttributes.path( "related" );
                    var desc = "";

                    if ( relatedItems.isArray( ) ) {

                        desc = "<span class='more-items'>" + relatedItems.size( ) + "</span> related items";

                    }

                    item.put( "label", processInfo + ": " + portId );
                    item.put( "description", desc );
                    item.put( "folder", false );
                    item.put( "lazy", false );
                    item.set( "attributes", portAttributes );

                } );

        return portListing;

    }

    @GetMapping ( "/cpu" )
    public JsonNode cpu( )
            throws Exception {

        return csapApis.osManager( ).buildServiceStatsReportAndUpdateTopCpu( true ).get( "mp" );

    }

    @GetMapping ( "/csap/services" )
    public ArrayNode servicesCsap( )
            throws Exception {

        ArrayNode serviceListing = jacksonMapper.createArrayNode( );

        // do a listing
        ObjectNode serviceMetricsJson = csapApis.osManager( ).buildServiceStatsReportAndUpdateTopCpu( true );

        JsonNode processItems = serviceMetricsJson.get( "ps" );

        processItems.fieldNames( ).forEachRemaining( name -> {

            JsonNode processAttributes = processItems.get( name );
            ObjectNode item = serviceListing.addObject( );
            item.put( "label", name );
            item.set( "attributes", processAttributes );
            item.put( "folder", true );
            item.put( "lazy", true );

        } );

        return serviceListing;

    }

    @GetMapping ( "/csap/definition" )
    public ArrayNode csapDefinition( )
            throws Exception {

        ArrayNode result = jacksonMapper.createArrayNode( );

        // do a listing
        JsonNode activeDefinition = csapApis.application().getActiveProject( ).getSourceDefinition( );

        activeDefinition.fieldNames( ).forEachRemaining( name -> {

            JsonNode processAttributes = activeDefinition.get( name );
            ObjectNode item = result.addObject( );
            item.put( "label", name );
            item.set( "attributes", processAttributes );
            item.put( "folder", true );
            item.put( "lazy", true );

        } );

        return result;

    }

    @GetMapping ( "/packages/linux" )
    public ArrayNode packagesLinux( )
            throws Exception {

        ArrayNode result = jacksonMapper.createArrayNode( );

        List<String> packages = csapApis.osManager( ).getLinuxPackages( );

        packages.stream( ).forEach( name -> {

            // JsonNode processAttributes = processItems.get( name );
            ObjectNode item = result.addObject( );
            item.put( "label", name );

            // item.set( "attributes", processAttributes );

            item.put( "folder", false );
            item.put( "lazy", false );

        } );

        return result;

    }

    final static String REPLACE_SPACES = "\\s+";

    @RequestMapping ( "/packages/linux/info" )
    public ObjectNode packagesLinuxInfo( String name )
            throws Exception {

        String info = csapApis.osManager( ).getLinuxPackageInfo( name );

        String url = "";
        StringBuilder description = new StringBuilder( );
        boolean isDescription = false;

        for ( String line : info.split( "\n" ) ) {

            if ( isDescription ) {

                description.append( line );
                description.append( "\n" );

            } else {

                String[] words = line
                        .replaceAll( REPLACE_SPACES, " " )
                        .split( " " );

                switch ( words[ 0 ] ) {

                    case "URL":
                        url = words[ 2 ];
                        break;

                    case "Description":
                        isDescription = true;
                        break;

                }

            }

        }

        ObjectNode result = jacksonMapper.createObjectNode( );

        result.put( "result", "name: " + name );
        result.put( "url", url );
        result.put( "description", description.toString( ) );
        result.put( "details", info );

        return result;

    }


    @GetMapping ( "/chef" )
    public ArrayNode chefListing( )
            throws Exception {

        var result = jacksonMapper.createArrayNode( );

        List<String> cookbooks = csapApis.osManager( ).getChefListing();

        cookbooks.stream( ).forEach( name -> {

            // JsonNode processAttributes = processItems.get( name );
            ObjectNode item = result.addObject( );
            item.put( "label", name );

            // item.set( "attributes", processAttributes );

            item.put( "folder", false );
            item.put( "lazy", false );

        } );

        return result;

    }

    @GetMapping ( "/chef/info" )
    public ObjectNode chefInfo( String name )
            throws Exception {

        var chefCookbooks = jacksonMapper.createArrayNode( );

        var chefFolder = new File( csapApis.application( ).configuration( ).getChefCookbookPath( ) );
        var cookbookFolder = new File( chefFolder, name );
        var readMeFile = new File( cookbookFolder, "README.md" );

        if ( !readMeFile.canRead( ) ) {
            csapApis.fileUtils().addUserReadPermissions( readMeFile );
        }
        var readMeMarkDown = Application.readFile( readMeFile );


        ObjectNode chefReadmeReport = jacksonMapper.createObjectNode( );
        chefReadmeReport.put("name", name ) ;
        chefReadmeReport.put("path", Application.FileToken.ROOT.value +  cookbookFolder.getAbsolutePath() ) ;


        chefReadmeReport.put( C7.response_html.val( ), csapApis.fileUtils( ).convertMarkdownToHtml( readMeMarkDown, readMeFile.getAbsolutePath( ) ) );


        return chefReadmeReport;

    }

    @GetMapping ( "/services/linux" )
    public ArrayNode servicesLinux( )
            throws Exception {

        ArrayNode result = jacksonMapper.createArrayNode( );

        List<String> services = csapApis.osManager( ).getLinuxServices( );

        services.stream( ).forEach( name -> {

            // JsonNode processAttributes = processItems.get( name );
            ObjectNode item = result.addObject( );
            item.put( "label", name );

            // item.set( "attributes", processAttributes );

            item.put( "folder", false );
            item.put( "lazy", false );

        } );

        return result;

    }

    @GetMapping ( "/services/linux/info" )
    public ObjectNode servicesLinuxStatus( String name )
            throws Exception {

        ObjectNode result = jacksonMapper.createObjectNode( );

        String info = csapApis.osManager( ).getLinuxServiceStatus( name );
        result.put( "result", "name: " + name );
        result.put( "description", info );
        result.put( "details", info );

        return result;

    }

    @GetMapping ( "/services/linux/logs" )
    public JsonNode servicesLinuxLogs( String name )
            throws Exception {

        ObjectNode result = jacksonMapper.createObjectNode( );

        result.put( "result", "name: " + name );
        result.put( "plainText", csapApis.osManager( ).getJournal( name, "", "500", false, false ) );

        return result;

    }

    @DeleteMapping ( "/processes/{pid}/{signal}" )
    public ObjectNode processKill(
            @PathVariable int pid,
            @PathVariable String signal
    )
            throws Exception {

        issueAudit( "Killing pid: " + pid + " signal: " + signal, null );

        return csapApis.osManager( ).killProcess( pid, signal );

    }

    private void issueAudit(
            String commandDesc,
            String details
    ) {

        csapApis.events( ).publishUserEvent( "osExplorer",
                securitySettings.getRoles( ).getUserIdFromContext( ),
                commandDesc, details );

    }

    @GetMapping ( "/processes" )
    public ArrayNode processesList( )
            throws Exception {

        var processesGroupedByCommandPath = jacksonMapper.createArrayNode( );

        csapApis.osManager( ).checkForProcessStatusUpdate( );

        ArrayNode processStatusItems = csapApis.osManager( ).processStatus( );
        ObjectNode processKeys = jacksonMapper.createObjectNode( );

        processStatusItems.forEach( processAttributes -> {

            String processCommandPath = "";
            String[] params = processAttributes.get( "parameters" ).asText( ).split( " " );

            if ( params.length >= 0 ) {

                processCommandPath = params[ 0 ];

                if ( processCommandPath.startsWith( "[kworker" ) ) {

                    processCommandPath = "system: kernel workers";

                } else if ( processCommandPath.startsWith( "[scsi" ) ) {

                    processCommandPath = "system: scsi";

                } else if ( processCommandPath.startsWith( "[watchdog" ) ) {

                    processCommandPath = "system: watchdogs";

                } else if ( processCommandPath.startsWith( "[xfs" ) ) {

                    processCommandPath = "system: xfs";

                } else if ( processCommandPath.startsWith( "[" ) ) {

                    processCommandPath = "system: miscellaneous";

                }

            }

            if ( processCommandPath.trim( ).length( ) == 0 ) {

                // every process should have a command path - but just in case a
                // parising error.
                processCommandPath += "Pid: " + processAttributes.get( "pid" ).asText( );

            }

            if ( !processKeys.has( processCommandPath ) ) {

                ObjectNode keyItem = processesGroupedByCommandPath.addObject( );

                processKeys.set( processCommandPath, keyItem );
                keyItem.put( "label", processCommandPath );

                keyItem.putArray( "attributes" );
                keyItem.put( "folder", true );
                keyItem.put( "lazy", true );

            }

            ArrayNode keyList = ( ArrayNode ) processKeys.get( processCommandPath ).get( "attributes" );
            keyList.add( processAttributes );

        } );

        return processesGroupedByCommandPath;

    }
}
