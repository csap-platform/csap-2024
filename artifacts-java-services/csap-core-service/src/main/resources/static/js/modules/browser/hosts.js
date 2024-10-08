
import _dom from "../utils/dom-utils.js";
import _net from "../utils/net-utils.js";


import utils from "./utils.js"


const hostView = hosts_tab();

export default hostView


function hosts_tab() {

    _dom.logHead( "Module loaded 12" );


    const $hostsContent = $( "#hosts-tab-content" );
    const $showContainerFs = $( "#show-container-fs", $hostsContent );


    const $hostFilter = $( "#host-filter", $hostsContent );
    const $tagLaunchCheckbox = $( "#use-host-tag", $hostsContent );
    let _statusFilterTimer;

    let _lastProject = null;
    let _lastHostReports;

    let applyFilterFunction;

    let _tableSorterActive = true ;

    return {

        initialize: function () {
            initialize();
        },

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            $showContainerFs.parent().hide();
            $( ".options", $menuContent.parent() ).show();

            return hostsGet( $menuContent, forceHostRefresh );

        }

    };

    function initialize() {

        applyFilterFunction = utils.addTableFilter( $hostFilter, $hostsContent );

        $tagLaunchCheckbox.change( function () {
            hostsGet( $hostsContent ) ;
        })

        $( 'button', '#launch-trends' ).click( function () {

            let report = $( this ).data( "report" );

            let targetUrl = `${ analyticsUrl }&report=${ report }&project=${ utils.getActiveProject( false ) }&appId=${ APP_ID }`;
            openWindowSafely( targetUrl, "_blank" );

        } );

        $( "#copy-host-table", $hostsContent ).click( function () {

            let $activeTable = $( "table:visible", $hostsContent ) ;
            utils.copyItemToClipboard( $activeTable );
        } );

        $( '#launch-all-hosts', $hostsContent ).click( function () {

            console.log( `launching all hosts` ) ;

            if ( _lastHostReports ) {
                for ( let hostReport of _lastHostReports ) {

                    if ( hostReport.name ) {
                        // openWindowSafely( utils.agentUrl( hostReport.name ) , "_blank" );
                        if ( utils.getHostTag( hostReport.name ) ) {
                            let osParams = { defaultService: utils.getHostTag( hostReport.name ) };
                            utils.openAgentWindow( hostReport.name, "/app-browser#agent-tab,system", osParams );
                        } else {
                            utils.openAgentWindow( hostReport.name, "/app-browser#agent-tab" );
                        }
                    }
                }
            }

            //openWindowSafely( targetUrl, "_blank" );

        } );

    }



    function hostsGet( $displayContainer, forceHostRefresh ) {

        let currentProject = utils.getActiveProject();

        //        if ( currentProject !== _lastProject ) {
        //            forceHostRefresh = true ;
        //        }

        let parameters = {
            project: currentProject,
            blocking: forceHostRefresh
        };


        let viewLoadedPromise = _net.httpGet( APP_BROWSER_URL + "/hosts", parameters );

        viewLoadedPromise
            .then( hostReports => {
                _lastHostReports = hostReports;
                if ( $displayContainer ) {
                    updateHostsTable( $displayContainer, hostReports );
                }

                applyFilterFunction();
            } )
            .catch( ( e ) => {
                console.warn( e );
            } );;


        return viewLoadedPromise;

    }

    function updateHostsTable( $displayContainer, hostReports ) {

        let $hostContainer = $( ".hosts", $displayContainer );

        let activeMenuId = $displayContainer.attr( "id" );
        console.log( `updateHostsTable() menu: ${ activeMenuId },  length: ${ hostReports.length } `, hostReports[ 0 ] );

        //        $hostContainer.empty().css("white-space", "pre").append( JSON.stringify(hostReport, "\n", "  ")) ;

        let $hostTable = $( "table", $displayContainer );
        let $hostTableBody = $( "tbody", $hostTable );
        let $hostTableHeader = $( "thead", $hostTable );

        let currentProject = utils.getActiveProject();

        if ( currentProject !== _lastProject ) {
            $hostTableBody.empty();
        }
        _lastProject = currentProject;

        let isNewTable = $( "tr", $hostTableBody ).length == 0;

        for ( let hostReport of hostReports ) {

            let rowId = utils.buildValidDomId( "hr-" + hostReport.name );
            let $row = $( `#${ rowId }`, $hostTableBody );

            if ( $row.length == 0 ) {

                $row = jQuery( '<tr/>', {
                    id: rowId
                } );

                $( "th", $hostTableHeader ).each( function () {
                    let fieldPath = $( this ).data( "path" );
                    jQuery( '<td/>', {
                        "data-path": fieldPath,
                        "data-show-object": $( this ).data( "show-object" )
                    } ).appendTo( $row );
                } );


                $row.appendTo( $hostTableBody );
            }

            $( "td", $row ).each( function () {
                let $column = $( this );
                let fieldPath = $column.data( "path" );
                let showObject = $column.data( "show-object" );
                let fieldValue = utils.json( fieldPath, hostReport );

                if ( Array.isArray( fieldValue ) ) {
                    if ( !showObject ) {
                        fieldValue = fieldValue.length;

                    } else {
                        let $arrayContainer = jQuery( '<div/>' );
                        for ( let item of fieldValue ) {
                            jQuery( '<span/>', { text: item } ).appendTo( $arrayContainer );
                        }
                        fieldValue = $arrayContainer.html();

                    }
                } else if ( utils.isObject( fieldValue ) ) {

                    //console.log( `object processing: `, Object.keys( fieldValue ) ) ;

                    if ( !showObject ) {
                        fieldValue = Object.keys( fieldValue ).length;

                    } else if ( fieldPath == "hostStats.df" ) {
                        fieldValue = fileSystem( fieldValue, hostReport.name );

                    } else {
                        let $wrapper = jQuery( '<div/>' );
                        let $itemContainer = jQuery( '<div/>', { class: "object-details" } ).appendTo( $wrapper );

                        for ( let itemName of Object.keys( fieldValue ) ) {
                            let $item = jQuery( '<div/>', { class: "host-pair" } );
                            jQuery( '<span/>', { text: "" } ).appendTo( $item );
                            jQuery( '<span/>', { text: itemName } ).appendTo( $item );
                            $itemContainer.append( $item );
                        }
                        fieldValue = $wrapper.html();
                    }
                }

                if ( fieldPath == "name" ) {
                    $column.empty();
                    let action = "scripts";
                    let params = {};
                    if ( activeMenuId.includes( "file" ) ) {
                        action = "files";
                        params = { fromFolder: `/opt/csap` };
                    } else if ( activeMenuId.includes( "infrastructure" )
                        || activeMenuId.includes( "network" )
                        || activeMenuId.includes( "summary" ) ) {
                        action = "host-dash";
                    }



                    console.debug( `building link: ${ fieldValue }` );

                    let $helperUrl = utils.getHostHelperUrl( fieldValue );
                    if ( $helperUrl != null ) {
                        $column.append( $helperUrl );
                    }

                    // utils.getHostShortName( fieldValue )

                    let hostName = fieldValue ;
                    //console.log( `activeMenuId ${activeMenuId} ` ) ;
                    if ( utils.getHostTag( hostName )
                        && $tagLaunchCheckbox.prop( "checked" )  ) {
                        params.defaultService = utils.getHostTag( hostReport.name ) ;
                        //utils.openAgentWindow( hostReport.name, "/app-browser#agent-tab,system", osParams );
                    } 

                    let $link = utils.buildAgentLink( fieldValue, action, null, params );

                    $column.append( $link );

                } else if ( fieldPath == "hostStats.kubernetes.eventCount" ) {
                    if ( fieldValue ) {
                        $column.html( `${ fieldValue } (0)` );
                        if ( fieldValue ) {
                            let restarts = utils.json( "hostStats.kubernetes.podReport.restarts", hostReport );
                            if ( restarts ) {
                                $column.html( `${ fieldValue } (${ restarts })` );
                            }
                        }
                    }


                } else if ( fieldPath.includes( "memoryInMb" ) ) {
                    if ( fieldValue ) {
                        $column.html( `${ utils.bytesFriendlyDisplay( fieldValue * 1024 * 1024 ) } ` );
                    }

                } else {
                    $column.html( fieldValue );
                }
            } );

        }

        if ( _tableSorterActive ) {
            if ( isNewTable ) {
                // themes: csap , csapSummary
                $hostTable.tablesorter( {
                    sortList: [ [ 0, 0 ] ],
                    cancelSelection:false,
                    theme: 'csapSummary'
                } );
            }

            $hostTable.trigger( "update" );
        }

        applyFilterFunction( $hostContainer );

    }

    function fileSystem( fieldValue, hostName ) {
        $showContainerFs.parent().show();
        $showContainerFs.off().change( function () {
            console.log( `refreshing active tab` );
            utils.launchMenu( "hosts-tab,file-systems" );
        } );

        let $wrapper = jQuery( '<div/>' );
        let $fsContainer = jQuery( '<div/>', { class: "object-details" } ).appendTo( $wrapper );

        for ( let fsName of Object.keys( fieldValue ) ) {
            let $fs = jQuery( '<div/>', { class: "host-pair" } );
            if ( !$showContainerFs.is( ":checked" )
                && ( fsName.includes( "/var/lib/docker/" )
                    || fsName.includes( "/var/lib/kubelet/" ) ) ) {
                $fs.hide();
            } else {
                $fs.show();
            }

            jQuery( '<span/>', { text: fieldValue[ fsName ] } ).appendTo( $fs );

            let $linkSpan = jQuery( '<span/>' );
            $linkSpan.appendTo( $fs );

            let $link = utils.buildAgentLink( hostName, "files", fsName, { fromFolder: fsName } );

            $linkSpan.append( $link );

            $fsContainer.append( $fs );
        }
        fieldValue = $wrapper.html();
        return fieldValue;
    }

}