// define( [ "browser/utils", "services/instances", "file/file-monitor" ], function ( utils, instances, fileMonitor ) {

//     console.log( "Module loaded" ) ;


import _dom from "../../utils/dom-utils.js";



import utils from "../utils.js"

import instances from "./instances.js"
import fileMonitor from "../file/file-monitor.js"

const svcLogs = service_logs();

export default svcLogs


function service_logs() {

    _dom.logHead( "Module loaded" );

    let fileManager;

    let lastServiceName;

    const $logsPanel = utils.findContent( "#services-tab-logs" );

    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {


            return loadHostFileMonitor( forceHostRefresh );

        }

    };



    function loadHostFileMonitor( forceHostRefresh, logHost, logService, logFile ) {


        let selectedService = instances.getSelectedService();

        if ( selectedService === "not-initialized-yet" ) {
            utils.launchMenu( "services-tab,status" );
        }

        if ( logService ) {
            selectedService = logService;
        }

        let allHosts = instances.getAllHosts();
        let $selectedInstanceRows = instances.getSelectedRows();


        let targetHost = allHosts[ 0 ];
        let theFile = null;
        let theContainer = null;
        let masters = instances.getKubernetesMasters();

        let isK8Monitor = instances.isKubernetesNamespaceMonitor();

        console.log( `loadHostFileMonitor() selectedService ${ selectedService } isK8Monitor: '${ isK8Monitor }' `,
            ` allHosts: ${ allHosts }, masters: ${ masters }` );

        if ( logHost ) {
            targetHost = logHost;
            theFile = logFile;

        } else if ( !isK8Monitor
            && Array.isArray( masters )
            && masters.length > 0 ) {
            console.log( `Kubernetes Services detected using first master for listing`, masters );
            targetHost = masters[ 0 ];

        } else if ( $selectedInstanceRows.length > 0 ) {

            let $row = $selectedInstanceRows.first();
            targetHost = $row.data( "host" );
            theContainer = $row.data( "container" );
            console.log( `selected container: ${ theContainer }` )
        }



        forceHostRefresh = true;

        lastServiceName = selectedService;


        if ( !forceHostRefresh && ( fileManager != null ) ) {
            return;
        }

        let listingUrl = `${ FILE_URL }/remote/listing`;

        if ( utils.isAgent() ) {
            listingUrl = `${ FILE_URL }/FileMonitor`;
        }

        let parameters = {
            serviceName: selectedService,
            hostName: targetHost,
            containerName: theContainer
            //            podName: thePodName
        };

        //        if ( !instances.isKubernetes() &&
        //                ( !utils.isUnregistered( selectedService ) ) ) {
        //            delete parameters.containerName ;
        //        }

        console.log( `loading: ${ listingUrl }`, parameters );

        utils.loading( `loading log listing: ${ targetHost } ` )

        let $contentLoaded = new $.Deferred();

        $.get( listingUrl,
            parameters,
            function ( lifeDialogHtml ) {
                // console.log( ` content: `, lifeDialogHtml ) ;


                utils.loadingComplete();


                let $logListing = $( "<html/>" ).html( lifeDialogHtml ).find( '.csap-file-monitor' );

                if ( lifeDialogHtml.startsWith( "error" ) ) {
                    $logListing = jQuery( '<div/>', {
                        class: "warning",
                        text: lifeDialogHtml
                    } );

                    $logsPanel.html( $logListing );

                } else {

                    $logsPanel.html( $logListing );
                    $( "#log-service-label", $logsPanel ).hide();


                    updateLogSelects( targetHost, selectedService );

                    let $logFileSelect = $( "#logFileSelect", $logsPanel );

                    console.log( `loadHostFileMonitor: starting log tailing theFile: ${ theFile }` );

                    if ( theFile ) {
                        // maintain selected file IF it is not kuberntes
                        $logFileSelect.val( theFile );

                        if ( !$logFileSelect.val() ) {
                            // theFIle is not found in the server side listing - processGroup or diff paths...match by name only
                            $( "option", $logFileSelect ).each( function () {

                                let $option = $( this );
                                let fileName = utils.baseName( theFile ) ;
                                let optionValue = $option.attr("value") ;
                                console.debug( `checking ${ fileName } in ${ optionValue }` ) ;

                                if ( !$logFileSelect.val() ) {

                                    if ( optionValue.endsWith( fileName ) ) {
                                        $logFileSelect.val( optionValue );
                                    }
                                }
                            } )
                        }
                    }

                    fileMonitor.show( $logsPanel, targetHost, selectedService, $logFileSelect );
                }

                $contentLoaded.resolve();
            },
            'text' );


        return $contentLoaded;


    }

    function updateLogSelects( selectedHost, selectedService ) {

        console.log( `addHostSelection() ${ selectedHost } ${ selectedService }` );

        let $logFileFilters = $( "#log-file-filters", $logsPanel );
        $logFileFilters.empty();



        let $hostLabel = jQuery( '<label/>', {
            title: "Retrieve Listing from selected host",
            class: "csap"
        } )
            .appendTo( $logFileFilters );

        let $selectSpan = jQuery( '<span/>', {
            text: "Host:"
        } ).appendTo( $hostLabel );



        let latestReport = instances.getLatestReport();
        let allServices = instances.getAllServices();

        let logHosts = instances.getAllHosts();
        let masters = instances.getKubernetesMasters();
        if ( Array.isArray( masters )
            && masters.length > 0 ) {
            logHosts = masters;
            updateFileSelectionWithPods( false );
            $hostLabel.text( "Master:" );
            $hostLabel.addClass( `log-filter` );
        }

        let $hostSelect = jQuery( '<select/>', {
            id: "host-log-select"
        } )
            .appendTo( $hostLabel );

        // let selectedService = instances.getSelectedService() ;
        if ( selectedService === "unregistered" ) {
            updateFileSelectionWithPods( true );
            $hostLabel.addClass( `log-filter` );
        }

        if ( latestReport.processGroup ) {

            _dom.logArrow( `processGroup2 selects: selectedService: ${ selectedService } allServices: ${ allServices }` );
            $selectSpan.text( "Service:" );
            for ( let serviceName of allServices ) {
                let attributes = {
                    value: serviceName,
                    "data-service": serviceName,
                    "data-host": selectedHost,
                    html: serviceName
                };
                if ( serviceName === selectedService ) {
                    attributes.selected = "selected";
                }
                jQuery( '<option/>', attributes ).appendTo( $hostSelect );
            }

        } else {

            let de_dupped_hosts = new Set( logHosts );
            _dom.logArrow( `hostProcessing: selectedService: ${ selectedService } de_dupped_hosts: `, de_dupped_hosts );
            for ( let host of de_dupped_hosts ) {
                let attributes = {
                    value: host,
                    "data-host": host,
                    "data-service": selectedService,
                    html: utils.getHostWithTag( host )
                };
                if ( host === selectedHost ) {
                    attributes.selected = "selected";
                }
                jQuery( '<option/>', attributes ).appendTo( $hostSelect );
            }

        }


        $hostSelect.change( function () {

            let $optionSelected = $( `option:selected`, $( this ) );

            console.log()

            let hostName = $optionSelected.data( "host" );
            let serviceName = $optionSelected.data( "service" );

            console.log( `log host selected ${ hostName }  serviceName: ${ serviceName }` );
            let $logFileSelect = $( "#logFileSelect", $logsPanel );


            loadHostFileMonitor( true, hostName, serviceName, $logFileSelect.val() );



        } );

        addFileFilters( $logFileFilters );

        return;
    }

    function updateFileSelectionWithPods( isUnregistered ) {

        let groupLabel = "Containers";
        if ( isUnregistered ) {
            groupLabel = "Discovered Containers";
        }

        let $logFileSelect = $( "#logFileSelect", $logsPanel );

        // placeholder in the file monitor API
        let $podPlaceholder = $( 'option[value="kubernetes-pods-detected"]', $logFileSelect );
        let markFirstPod = false;
        let selectedPod;
        if ( $podPlaceholder.length > 0 ) {
            $podPlaceholder.remove();
            markFirstPod = true;

            let $selectedInstanceRows = instances.getSelectedRows();
            if ( $selectedInstanceRows.length > 0 ) {
                let $row = $selectedInstanceRows.first();
                let testPod = $row.data( "pod" );
                if ( testPod ) {
                    selectedPod = testPod;
                    markFirstPod = false;
                    $logFileSelect.val( "" );
                }
            }
        }
        $( 'option[value="unregistered-detected"]', $logFileSelect ).remove();

        let $podGroup = jQuery( '<optgroup/>', {
            label: groupLabel
        } ).prependTo( $logFileSelect );

        let logToShow;
        let unSortedOptions = new Array();
        instances.getAllRows().each( function () {
            let $row = $( this );
            //console.debug( `row: ${ $row.html() }`) ;
            let host = $row.data( "host" );

            let containerName = $row.data( "container" );

            let optionValue = "__docker__" + containerName;
            let optionText = containerName;

            let podName = $row.data( "pod" );
            if ( podName ) {
                let podFields = podName.split( "-" );
                let podSuffix = podFields[ podFields.length - 1 ];
                optionValue = `${ containerName }-${ podSuffix }`;
                optionText = `${ containerName } (${ podSuffix })`;
            } else {
                if ( !isUnregistered ) {
                    // kubernetes master without service
                    console.log( `Skipping container - kubernetes master` );
                    return;
                }
            }

            let attributes = {
                value: optionValue,
                "data-host": host,
                "data-container": containerName,
                "data-pod": podName,
                "data-namespace": $row.data( "namespace" ),
                text: optionText
            };

            console.log( `podName: ${ podName }  selectedPod: ${ selectedPod } ` );
            if ( markFirstPod
                || podName === selectedPod ) {
                console.log( `Selecting podName: ${ podName }  selectedPod: ${ selectedPod } ` );
                markFirstPod = false;
                //attributes.selected = "selected" ;
                logToShow = optionValue;
            }

            // 
            unSortedOptions.push( attributes );

        } );

        let sortedOptions = ( unSortedOptions ).sort( ( a, b ) => ( a.text > b.text ) ? 1 : -1 );
        for ( let optionItem of sortedOptions ) {
            jQuery( '<option/>', optionItem ).appendTo( $podGroup );
        }

        if ( logToShow ) {
            $logFileSelect.val( logToShow );
        }

    }

    function addFileFilters( $logFileFilters ) {


        let $logFileSelect = $( "#logFileSelect", $logsPanel );

        let filtersAdded = false;

        let namespaces = new Set();
        namespaces.add( "all" );
        let pods = new Set();
        pods.add( "all" );

        instances.getAllRows().each( function () {
            let $row = $( this );
            let namespace = $row.data( "namespace" );
            if ( namespace ) {
                namespaces.add( namespace );
            }
            let pod = $row.data( "pod" );
            if ( pod ) {
                pods.add( pod );
            }
        } );

        console.log( `namespaces: `, namespaces );
        if ( namespaces.size > 2 ) {
            filtersAdded = true;

            let $container = jQuery( '<label/>', {
                title: "Filter file selection using namespace",
                class: "csap log-filter",
                text: "Namespace:"
            } )
                .prependTo( $logFileFilters );


            let $namespaceFilter = jQuery( '<select/>', {
                title: "Filters files by namespace"
            } )
                .change( function () {
                    let filterNamespace = $( this ).val();
                    console.log( `filtering: ${ filterNamespace } ` );

                    $( "option", $logFileSelect ).each( function () {
                        let $option = $( this );
                        let namespace = $option.data( "namespace" );
                        if ( namespace === filterNamespace
                            || "all" === filterNamespace ) {
                            $option.show();
                        } else {
                            $option.hide();
                        }
                    } )


                    utils.flash( $logFileSelect, true, 2 );

                } );

            $container.append( $namespaceFilter );

            for ( let namespace of namespaces ) {
                jQuery( '<option/>', { text: namespace } ).appendTo( $namespaceFilter );
            }

        }

        console.log( `pods: `, pods );
        if ( pods.size > 2 ) {
            filtersAdded = true;

            let $container = jQuery( '<label/>', {
                title: "Filter file selection using pod",
                class: "csap log-filter",
                text: "Pod:"
            } )
                .prependTo( $logFileFilters );

            let $podFilter = jQuery( '<select/>', {
                title: "Filters files by namespace"
            } )
                .change( function () {
                    let filterPodName = $( this ).val();
                    console.log( `filtering: ${ filterPodName } ` );

                    $( "option", $logFileSelect ).each( function () {
                        let $option = $( this );
                        let optionPodName = $option.data( "pod" );
                        if ( optionPodName === filterPodName
                            || "all" === filterPodName ) {
                            $option.show();
                        } else {
                            $option.hide();
                        }
                    } )

                    utils.flash( $logFileSelect, true, 2 );

                } );

            $container.append( $podFilter );

            for ( let pod of pods ) {
                jQuery( '<option/>', { text: pod } ).appendTo( $podFilter );
            }

        }

        if ( filtersAdded ) {
            jQuery( '<label/>', {
                title: "Filter files",
                class: "csap",
                text: "Filters"
            } )
                .prependTo( $logFileFilters );
        }
    }


}

