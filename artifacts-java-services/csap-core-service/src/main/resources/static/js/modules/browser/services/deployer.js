// define( [ "browser/utils", "ace/ace", ], function ( utils, aceEditor ) {

//     console.log( "Module loaded" ) ;


import "../../../../../webjars/jquery-form/4.2.2/jquery.form.min.js"

import _dom from "../../utils/dom-utils.js";

import _dialogs from "../../utils/dialog-utils.js";


import utils from "../utils.js"


import { aceEditor } from "../../libs/file-editor.js"


const svcDeployer = service_deployer();

export default svcDeployer


function service_deployer() {

    _dom.logHead( "Module loaded" );

    const $dialog = $( ".alertify-content" );

    const $servicesContent = utils.findContent( "#services-tab-content" );

    const $serviceParameters = $( "#serviceParameters", $servicesContent );
    const $resultsPre = $( "#results" );

    const $instancePanel = $( "#services-tab-instances" );
    const $tableOperations = utils.findContent( "#table-operations", $instancePanel );
    const $instanceMenu = $( "#instance-actions-menu", $instancePanel );
    const $optionsPanel = $( "div.options", $instancePanel );

    const $killOptions = $( "#killOptions" );
    const $killButton = $( '#killButton', $killOptions );
    const $stopButton = $( '#stopButton', $killOptions );

    const $deployOptions = $( "#deployOptions" );
    const $osDeploy = $( "#osDeployOptions", $deployOptions );


    const $deploySourceCommands = $( "#deploy-source-commands", $deployOptions );
    const defaultScmCommand = $( "#scmCommand" ).val();


    const $kubernetesMastersSelect = $( "#master-deploy-hosts", $tableOperations );

    let _deploySuccess;
    let _postBacklogRefreshes = 0;

    let _kubernetesEditor = null;
    let latest_instanceReport = null;
    let serverType = null;
    let primaryHost = null;
    let selectedHosts = null, _selectedServices;
    let primaryInstance = null;
    let serviceName = null;

    let isBuild = false;

    let instancesRefreshFunction;
    let refreshTimer;


    let _deployDialog = null;
    let fileOffset = "-1";
    const LOG_CHECK_INTERVAL = 2 * 1000;

    let _dockerStartParamsEditor;

    let _jobOutputEditor, _jobResizeTimer;


    let servicePerformanceId, serviceShortName;

    return {

        initialize: function ( getInstances ) {
            initialize();
            instancesRefreshFunction = getInstances;
        },

        update_view_for_service: function ( instanceReport ) {
            update_view_for_service( instanceReport );
        },

        showStartDialog: function ( hosts, selectedServices ) {
            console.log( `hosts: ${ hosts }\n selectedServices ${ selectedServices }` );
            selectedHosts = hosts;
            _selectedServices = selectedServices;
            primaryHost = hosts[ 0 ];
            showStartDialog();
        },

        showStopDialog: function ( hosts, selectedServices ) {
            console.log( `hosts: ${ hosts }\n selectedServices ${ selectedServices }` );
            selectedHosts = hosts;
            _selectedServices = selectedServices;
            primaryHost = hosts[ 0 ];
            showStopDialog();
        },

        showDeployDialog: function ( hosts, selectedServices ) {
            selectedHosts = hosts;
            _selectedServices = selectedServices;
            primaryHost = hosts[ 0 ];
            showDeployDialog();
        },

        executeOnHosts: function ( hosts, selectedServices, command, paramObject ) {
            console.log( `hosts: ${ hosts }\n selectedServices ${ selectedServices }` );

            if ( !hosts || !selectedServices || hosts.length <= 0 || selectedServices.length <= 0 ) {
                alertify.csapWarning( `Select 1 or more instances to run command: ${ command }` );
                return;
            }
            selectedHosts = hosts;
            primaryHost = hosts[ 0 ];
            _selectedServices = selectedServices;
            executeOnSelectedHosts( command, paramObject );
        }


    };

    function initialize() {

        console.log( `defaultScmCommand: ${ defaultScmCommand }` )

        $deploySourceCommands.change( function () {
            $( "#scmCommand" ).val( $deploySourceCommands.val() );
            $deploySourceCommands.val( none );
        } )

        $( "#dockerImageVersion" ).change( function () {
            $serviceParameters.val( "{}" );
            if ( latest_instanceReport.dockerSettings ) {
                latest_instanceReport.dockerSettings.image = $( this ).val();
                if ( _dockerStartParamsEditor != null ) {
                    _dockerStartParamsEditor.setValue( JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" ) );
                }
            }
        } );

        $( "#isClean" ).change( function () {
            if ( latest_instanceReport.kubernetes ) {
                if ( $( "#isClean" ).is( ':checked' ) ) {
                    alertify.csapWarning( "Caution: deleting volumes will permanently delete service data." );
                }
            }
        } );

        $( "#clean-docker-volumes" ).change( function () {
            if ( $( "#clean-docker-volumes" ).is( ':checked' ) ) {
                $( '#isClean' )
                    .prop( 'checked', true )
                    .prop( 'disabled', true );
                alertify.csapWarning( "Caution: deleting volumes will permanently delete service data." );
            } else {
                $( '#isClean' )
                    .prop( 'disabled', false );
            }
        } );
    }


    function update_view_for_service( instanceReport ) {

        latest_instanceReport = instanceReport;
        primaryInstance = instanceReport.instances[ 0 ]
        serviceName = primaryInstance.serviceName;
        serverType = primaryInstance.serverType;

        console.log( `update_view_for_service: ${ serviceName }, ${ serverType } ` );

        _dom.csapDebug( `primaryInstance`, primaryInstance );


        let noteText = instanceReport.deploymentNotes;
        let sentenceInNotesLocation = ( instanceReport.deploymentNotes ).indexOf( "." );
        if ( sentenceInNotesLocation > 0 ) {
            noteText = instanceReport.deploymentNotes.substring( 0, sentenceInNotesLocation );
        }
        let $notes = jQuery( '<div/>' );
        jQuery( '<span/>', {
            text: noteText
        } ).appendTo( $notes );
        $( ".deployment-notes" ).html( $notes.html() );
        if ( sentenceInNotesLocation > 0 ) {
            $notes.append( instanceReport.deploymentNotes.substring( sentenceInNotesLocation ) );
        }


        $( ".service-live", $instanceMenu ).hide();
        if ( primaryInstance.serviceHealth && ( primaryInstance.serviceHealth != "" ) ) {
            $( ".service-live", $instanceMenu ).show();
        }

        $( ".instance-tools", $instanceMenu ).hide();
        if ( latest_instanceReport.javaJmx ) {
            $( ".instance-tools", $instanceMenu ).show();
        }


        $( "#service-description .text" ).text( instanceReport.description );
        if ( utils.isUnregistered( serverType ) ) {
            $( "#service-description .text" ).text( `To collect and trend os resources, add containers to the application definition` );
        }
        if ( instanceReport.docUrl !== "" ) {

            jQuery( '<a/>', {
                href: "#" + instanceReport.docUrl,
                class: "csap-link-icon csap-help",
                target: "_blank",
                text: "learn more"
            } )
                .click( function () {
                    let urlArray = instanceReport.docUrl.split( ',' );
                    for ( let targetUrl of urlArray ) {
                        console.log( `launch url: targetUrl` );
                        utils.launch( targetUrl );
                    }
                    return false;
                } )
                .appendTo( $( "#service-description .text" ) );
        }

        let profile = serverType;
        if ( profile === "script" ) {
            profile = "OS Files";
        } else if ( profile === "os" ) {
            profile = "Process Monitor";
        } else if ( ( profile === "docker" )
            && ( latest_instanceReport.kubernetes ) ) {
            profile = "kubernetes";
        }
        $( "#profile", $instancePanel ).text( `[${ profile }]` );


        $( ".is-java-server" ).hide();
        $serviceParameters.val( "" );
        if ( instanceReport.parameters ) {
            $serviceParameters.val( instanceReport.parameters );
            $( ".is-java-server" ).show();
        }
        //$( "#serviceParameters" ).text( instanceReport.parameters ) ;
        $( "#mavenArtifact" ).val( instanceReport.mavenId );
        $( "#scmLocation" ).text( instanceReport.scmLocation );
        $( "#scmLocation" ).attr( "href", instanceReport.scmLocation );
        $( "#scmFolder" ).text( instanceReport.scmFolder );
        $( "#scmBranch" ).val( instanceReport.scmBranch );


        //
        // Main buttons
        //
        $( "#deploy-buttons", $optionsPanel ).show();
        $( "button.start, button.stop", $optionsPanel ).show();

        if ( latest_instanceReport.kubernetes ) {
            $( "button.start, button.stop", $optionsPanel ).hide();
            $( " button.remove", $optionsPanel ).show();
        } else {
            $( " button.remove", $optionsPanel ).hide();
        }
        $( ".hquote", $optionsPanel ).remove();
        if ( serverType === "os" ) {

            let message = "<span>OS Process Monitor</span> - deployment operations not available"
            jQuery( '<div/>', {
                class: "hquote",
                html: message
            } ).css( "margin", "10px" ).css( "font-size", "10pt" ).css( "padding", "0em" ).appendTo( $optionsPanel );

            $( "#deploy-buttons", $optionsPanel ).hide();
            $( "#meters" ).hide();
        }


        //
        //  dialog buttons
        //
        $stopButton.show();
        $( "#clean-docker-volumes-container" ).hide();
        let killText = "Stop (kill -9)";
        if ( latest_instanceReport.filesOnly || latest_instanceReport.kubernetes ) {
            $stopButton.hide();
        }
        if ( serviceName == AGENT_NAME ) {
            killText = "Restart CSAP Agent";

        } else if ( latest_instanceReport.filesOnly ) {
            killText = "Stop and Remove: CSAP package";

        } else if ( latest_instanceReport.kubernetes ) {
            killText = "Delete Kubernetes Resource(s)";

        } else if ( serverType == "docker" ) {
            killText = "Stop and Remove: container";
            $( "#clean-docker-volumes-container" ).show();
        }
        $( "span", $killButton ).text( killText );

        //
        //  Menu Customization
        //
        $( "button.java", $instanceMenu ).hide();
        if ( latest_instanceReport.javaCollection ) {
            $( "button.java", $instanceMenu ).show();
        }

        $( "button.application", $instanceMenu ).hide();
        if ( latest_instanceReport.performanceConfiguration ) {
            $( "button.application", $instanceMenu ).show();
        }
        $( "button.launch", $instanceMenu ).show();
        if ( serverType == "unregistered" ) {
            $( "button.launch", $instanceMenu ).hide();
        }

        //
        //  Deployment dialog
        //
        $( ">div", $deployOptions ).hide();
        if ( serverType == "docker" ) {
            console.log( `Showing Docker deploy items` );
            if ( latest_instanceReport.kubernetes ) {
                $( "#kubernetesDeployOptions" ).show();
            } else {

                $( "#dockerDeployOptions" ).show();


                $( "#dockerImageVersion" ).val( "" );
                if ( latest_instanceReport.dockerSettings ) {
                    if ( latest_instanceReport.dockerSettings.image ) {
                        $( "#dockerImageVersion" ).val( latest_instanceReport.dockerSettings.image );
                    }

                }
            }

            if ( _dockerStartParamsEditor != null
                && latest_instanceReport.dockerSettings ) {
                _dockerStartParamsEditor.setValue( JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" ) );
            }
        } else {
            $osDeploy.show();
        }


        if ( latest_instanceReport.filesOnly ) {
            $( "#service-cpu" ).hide();
            $( "#osChart" ).hide();
            $( "#osLearnMore" ).show();
        } else {
            $( "#service-cpu" ).show();
            $( "#osChart" ).show();
            $( "#osLearnMore" ).hide();
        }


        $( "#deployStart" ).prop( "checked", true );
        $( "#deployStart" ).show();
        if ( serviceName == AGENT_NAME ) {
            $( "#deployStart" ).prop( 'checked', false );
            $stopButton.hide();
        }

        servicePerformanceId = serviceName;
        serviceShortName = serviceName;
        if ( latest_instanceReport.kubernetes ) {
            let instanceK8sHosts = primaryInstance[ "kubernetes-masters" ];

            if ( Array.isArray( instanceK8sHosts ) ) {

                $kubernetesMastersSelect.empty();

                instanceK8sHosts.forEach( k8Master => {
                    console.log( `\n\n\n master : ${ k8Master } ` );
                    let $optionItem = jQuery( '<option/>', {
                        text: k8Master
                    } );
                    $kubernetesMastersSelect.append( $optionItem );

                } )
            }

            //            _is_clear_instance_on_refresh = true ;
            servicePerformanceId += "-" + ( primaryInstance.containerIndex + 1 );
            serviceShortName = serviceName + "-" + ( primaryInstance.containerIndex + 1 )
            console.log( `cluster is kubernetes, performance id updated: '${ servicePerformanceId }'`
                + ` \t serviceShortName is: '${ serviceShortName }'` );

            console.log( `Hiding startButton` );
            $( "#killOptionsButton span" ).text( "Remove..." );

            $( "#docker-clean-description" ).text( "Clean will remove associated persistent volumes" );
            if ( latest_instanceReport.dockerSettings[ "deployment-files-use" ] == "true" ) {
                $( "#docker-clean-description" ).text( "Clean will include any specs named '*deploy-only*.yaml'" );
            }
            $( "#service-runtime" ).html( 'Kubernetes<img src="images/k8s.png">' );
            $killButton.attr( "title", "Service delete will be done by running kubectl delete -f spec_file(s)" );

            $( "#deployStart" ).prop( "checked", false );
            $( "#deployStart" ).hide();
            $( "#admin-deploy-note" ).text( "Kubernetes deployments will auto select host, pull docker image(s), and start the container(s)" );

        }

        $( ".is-tomcat" ).hide();
        if ( instanceReport.tomcat ) {
            $( ".is-tomcat" ).show();
        }

        const javaParamChecks = function () {

            const selectedButton = $( this ).attr( "id" );
            console.log( `javaParamChecks: ${ selectedButton }` )

            if ( selectedButton == `isDebug`
                && !$serviceParameters.val().contains( "agentlib" ) ) {

                // paramObject.push({noDeploy: "noDeploy"}) ;
                $serviceParameters.val( $serviceParameters.val()
                    + " -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" );
            }

            if ( selectedButton == `is-java-nmt`
                && !$serviceParameters.val().contains( "NativeMemoryTracking" ) ) {

                // paramObject.push({noDeploy: "noDeploy"}) ;
                $serviceParameters.val( $serviceParameters.val()
                    + " -XX:NativeMemoryTracking=summary" );
            }

            if ( selectedButton == `isJmc`
                && !$serviceParameters.val().contains( "FlightRecorder" ) ) {

                $serviceParameters.val( $serviceParameters.val()
                    + " -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints" );

            }

        }

        $( ".java-start-parameter" ).hide();
        if ( instanceReport.springboot || instanceReport.tomcat ) {
            $( ".java-start-parameter" ).show();

            $( "#isDebug" ).off().click( javaParamChecks );
            $( "#is-java-nmt" ).off().click( javaParamChecks );
            $( "#isJmc" ).off().click( javaParamChecks );

        }


        $( ".is-datastore" ).hide();
        if ( instanceReport.datastore ) {
            $( ".is-datastore" ).show();
        }


        $( ".is-kubernetes" ).hide();
        $( ".is-not-kubernetes" ).show();
        if ( latest_instanceReport.kubernetes ) {
            $( ".is-kubernetes" ).show();
            $( ".is-not-kubernetes" ).hide();
        }


        //
        //
        //

        $( ".is-docker" ).hide();
        $( ".is-not-docker" ).show();
        $( '#isClean' ).prop( 'checked', true );
        //alert(`utils.getActiveEnvironment() ${utils.getActiveEnvironment()}`) ;


        $( '#isSaveLogs' ).prop( 'checked', true );
        if ( utils.getActiveEnvironment() == ( `dev` ) ) {
            $( '#isSaveLogs' ).prop( 'checked', false );
        }
        if ( serverType == "docker" ) {
            $( ".is-docker" ).show();
            $( ".is-not-docker" ).hide();
            $( '#isClean' ).prop( 'checked', false );
        }

        $( ".is-csap-api" ).hide();
        $( ".is-not-csap-api" ).show();
        $( "#scmCommand" ).val( defaultScmCommand );
        if ( latest_instanceReport.csapApi ) {
            $( ".is-csap-api" ).show();
            $( ".is-not-csap-api" ).hide();

            //
            $( "#scmCommand" ).val( "--info" );
        }


        $( ".is-files-only" ).hide();
        $( ".is-note-files-only" ).show();
        if ( latest_instanceReport.filesOnly ) {
            $( ".is-files-only" ).show();
            $( ".is-not-files-only" ).hide();
        }


    }

    function showStartDialog() {

        // Lazy create
        if ( !alertify.start ) {
            let startDialogFactory = function factory() {
                return {
                    build: function () {
                        // Move content from template
                        this.setContent( $( "#startOptions" ).show()[ 0 ] );
                        this.setting( {
                            'onok': startService,
                            'oncancel': function () {
                                console.log( "Cancelled Request" );
                            }
                        } );
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Start Service", className: alertify.defaults.theme.ok, key: 0 },
                                { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                            ],
                            options: buildAlertifyOptions( "Start Service Dialog" )
                        };
                    }

                };
            };
            alertify.dialog( 'start', startDialogFactory, false, 'confirm' );
        }

        let startDialog = alertify.start().show();

        if ( serverType == "docker" ) {
            maximizeDialog( startDialog );
            if ( _dockerStartParamsEditor == null ) {

                let params = "{}";
                if ( latest_instanceReport.dockerSettings ) {
                    params = JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" );
                }

                $( "#docker-params-definition" ).text( params );
                resize_alertify_element( $( "#docker-params-definition" ), 900 );

                setTimeout( function () {

                    _dockerStartParamsEditor = aceEditor.edit( "docker-params-definition" );
                    _dockerStartParamsEditor.setOptions( utils.getAceDefaults( "ace/mode/json" ) );

                }, 200 );

            } else {
                // refresh latest
                _dockerStartParamsEditor.setValue( JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" ) );
            }

        } else {
            _dialogs.resizeDialog( startDialog, $( "#startOptions" ) );
        }

        setAlertifyTitle( "Starting", startDialog );

        if ( serviceName.indexOf( AGENT_NAME ) != -1 ) {
            let message = "Warning - csap agent should be killed which will trigger auto restart."
                + "<br> Do not issue start on csap agent unless you have confirmed in non production environment.";

            alertify.csapWarning( message );
        }


    }

    function maximizeDialog( dialog ) {

        let targetWidth = $( window ).outerWidth( true ) - 50;
        let targetHeight = $( window ).outerHeight( true ) - 50;

        dialog.resizeTo( targetWidth, targetHeight );
    }

    function resize_alertify_element( $element, additionalHeight, alertifyExtra = 300 ) {

        let $alertifyDialog = $element.closest( ".alertify" ).parent();
        //let $alertifyDialog = $( ".alertify" ).parent() ; 

        let alertifyHeight = Math.round( $alertifyDialog.outerHeight( true ) - alertifyExtra );
        let targetHeight = alertifyHeight;
        let currentHeight = $element[ 0 ].scrollHeight + additionalHeight;

        if ( currentHeight < alertifyHeight ) {
            targetHeight = currentHeight;
        }
        $element.height( targetHeight );
        //$element.height( Math.round( $( ".alertify" ).parent().outerHeight( true ) - 400 ) ) ;
        $element.width( Math.round( $alertifyDialog.outerWidth( true ) - 200 ) );
    }

    function buildAlertifyOptions( title ) {
        let options = {
            title: title,
            movable: true,
            maximizable: true,
            resizable: true,
            autoReset: false
        }

        return options;
    }


    /**
     *
     * This sends an ajax http get to server to start the service
     *
     */
    function startService() {


        let paramObject = new Object();

        let startParams = "";
        if ( serverType == "docker" ) {

            if ( _dockerStartParamsEditor != null ) {
                startParams = _dockerStartParamsEditor.getValue();
            } else {
                startParams = JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" );
            }
        } else {
            if ( $( "#noDeploy" ).is( ':checked' ) ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;
                $.extend( paramObject, {
                    noDeploy: "noDeploy"
                } );
            }

            startParams = $serviceParameters.val();
        }

        $.extend( paramObject, {
            commandArguments: startParams
        } );

        if ( $serviceParameters.val().length && ( $serviceParameters.val().indexOf( "agentlib" ) != -1 ) ) {
            let debugIndex = $serviceParameters.val().indexOf( "agentlib" );
            let message = $serviceParameters.val().substr( debugIndex )
            alertify.csapInfo( `Debug is enabled: \n\n ${ message }` )
        }


        executeOnSelectedHosts( "startServer", paramObject );

    }


    function executeOnSelectedHosts( command, paramObject ) {

        console.log( `Running '${ command }':`, paramObject );
        //$busyPanel.show() ;

        let message = `running ${ command }`;
        if ( command == 'killServer' ) {
            message = "Scheduling removal of service";
        } else if ( command == 'runServiceJob' ) {
            message = ` Running service job ....`;
        }
        utils.loading( message );

        // alert("numSelected: " + numHosts) ;
        // nothing to do on a single node build
        if ( !isBuild ) {
            $resultsPre.html( "" );
        }

        $resultsPre.append( "\n\nStarting Request: " + command + "\n" );
        $( 'body' ).css( 'cursor', 'wait' );

        let numResults = 0;

        // Now run through the additional hosts selected
        let postCommandToServerFunction = function (
            serviceInstance,
            serviceHost,
            totalCommandsToRun ) {

            let hostParamObject = new Object();

            if ( $( "#isHotDeploy" ).is( ':checked' ) ) {
                $.extend( paramObject, {
                    hotDeploy: "hotDeploy"
                } );
            }


            $.extend( hostParamObject, paramObject, {
                hostName: serviceHost,
                serviceName: serviceInstance
            } );

            let commandUrl = SERVICE_URL + "/" + command;
            console.log( `Posting to: '${ commandUrl }'` );

            $.post( commandUrl, hostParamObject, totalCommandsToRun )
                .done(
                    function ( results ) {
                        // displayResults(results);
                        numResults++;


                        displayHostResults(
                            serviceHost,
                            serviceInstance,
                            command, results,
                            numResults,
                            totalCommandsToRun );


                        isBuild = false;

                        if ( numResults >= totalCommandsToRun ) {
                            $( 'body' ).css( 'cursor', 'default' );
                            utils.loadingComplete();
                            //refresh_service_instances() ;
                        }
                    } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    //console.log( JSON.stringify( jqXHR, null, "\t" ));
                    //console.log( JSON.stringify( errorThrown, null, "\t" ));

                    utils.loadingComplete();

                    console.log(errorThrown) ;

                    _dialogs.showJsonError(  command, jqXHR );
                } );


        }


        if ( latest_instanceReport.kubernetes ) {
            postCommandToServerFunction( serviceName, $kubernetesMastersSelect.val(), 1 );

        } else {

            if ( latest_instanceReport.processGroup ) {

                //
                // process group services on a selected host
                //
                let totalCommandsToRun = _selectedServices.length;
                for ( let serviceName of _selectedServices ) {
                    postCommandToServerFunction( serviceName, primaryHost, totalCommandsToRun );
                }
            } else {

                //
                //  Normal services
                //
                let totalCommandsToRun = selectedHosts.length;
                for ( let host of selectedHosts ) {
                    postCommandToServerFunction( serviceName, host, totalCommandsToRun );
                }
            }
        }

        // show logs
        if ( command != "runServiceJob" ) {

            if ( utils.isLaunchServiceLogs() ) {
                utils.launchServiceLogs( command );
            } else {
                alertify.notify( ` ${ command } has been requested, monitor via backlog queue or switch to log view` );
            }
        }

    }

    function showJobDialog( hostIndex, host, serviceName, hostOutput ) {

        let dialogId = "jobResultDialog";

        if ( !alertify.jobResultInfo ) {

            const getContentContainer = function () {
                return $( `#${ dialogId } #job-output-editor` );
            }


            let resizeFunction = function ( dialogWidth, dialogHeight ) {

                console.log( ` dialogWidth: ${ dialogWidth }  dialogHeight: ${ dialogHeight } ` )

                let maxWidth = dialogWidth - 10;
                let $jobPre = getContentContainer();
                $jobPre.css( "width", maxWidth );

                let maxHeight = dialogHeight
                    - Math.round( $( "#job-ouput-header" ).outerHeight( true ) )
                    - 40;
                $jobPre.css( "height", maxHeight );

                console.log( `kubernetes_yaml_dialog() launched/resizing yaml editor` );
                // if ( _jobOutputEditor ) {
                //     _jobOutputEditor.resize();
                // }

                // }, 500 );

            };

            console.log( "Building: dialogId: " + dialogId );

            let configuration = {
                content: '<div id="' + dialogId + '"><div id="job-ouput-header"><button class="csap-button-icon csap-remove">Close Job Output</button></div><pre id=job-output-editor></pre></div>',

                onresize: resizeFunction,

                onclose: function () {
                    _jobOutputEditor.getSession().setValue( "" );
                }
            }
            let csapDialogFactory = _dialogs.dialog_factory_builder( configuration );

            alertify.dialog( 'jobResultInfo', csapDialogFactory, false, 'alert' );


        }

        let settingsDialog = alertify.jobResultInfo();

        // let $content = getContentContainer() ;

        let outputWithHeader = "";

        // Settings from associated ResourceGraph moved into dialog
        //$content.text( combinedOutput );


        if ( hostIndex == 1 ) {
            settingsDialog.show();
        }

        if ( hostOutput.includes( "grep" ) ) {
            hostOutput = hostOutput.replaceAll( "\n--\n", "\n\n\n#\n## NEXT_GREP_MATCH \n#\n--\n" );
        }

        outputWithHeader = `\n\n\n#\n##\n### Host: ${ host } Service: ${ serviceName } \n##\n#\n`
            + hostOutput;


        if ( _jobOutputEditor == null ) {
            $( "#job-output-editor" ).text( outputWithHeader );

            setTimeout( function () {

                $( "#job-ouput-header button" ).off().click( function () {
                    settingsDialog.close();
                } );

                _jobOutputEditor = aceEditor.edit( "job-output-editor" );
                //editor.session.setMode("ace/mode/yaml");
                let aceOptions = utils.getAceDefaults( "ace/mode/yaml", true );
                // aceOptions.theme = "ace/theme/merbivore_soft";
                // aceOptions.theme = "ace/theme/kuroir";

                _jobOutputEditor.setOptions( aceOptions );
            }, 100 );

        } else {

            // _jobOutputEditor.getSession().setValue( hostOutput ) ;
            _jobOutputEditor.getSession().insert( {
                row: _jobOutputEditor.getSession().getLength(),
                column: 0
            }, outputWithHeader );

        }

    }


    function displayHostResults( commandHost, serviceName, command, commandResultReport, currentCount, totalCount ) {

        // console.log( `displayHostResults() ${ serviceName } `, resultsJson ) ;

        // let hostPath = `clusteredResults.${ commandHost }`;
        // let hostResponse = utils.json( hostPath, resultsJson );

        // support for fqdn hosts
        let hostResponse = null;
        if ( commandResultReport
            && commandResultReport.clusteredResults
            && commandResultReport.clusteredResults[ commandHost ] ) {
            hostResponse = commandResultReport.clusteredResults[ commandHost ];
        }


        let directResponse = commandResultReport.results;
        //        console.log( `${ commandHost }: ${hostResponse}` ) ;
        console.log( `${ currentCount } displayHostResults() host: ${ commandHost } : ${ command }` );

        //

        if ( command == "runServiceJob" ) {

            let serviceResponse = utils.json( serviceName, hostResponse );
            if ( serviceResponse && serviceResponse.includes( utils.getErrorIndicator() ) ) {
                alertify.csapWarning( `Error in Output: \n\n ${ serviceResponse }` );

            } else if ( serviceResponse ) {
                // alertify.csapInfo( serviceResponse ) ;

                let delay = 100;
                if ( currentCount > 1 ) {
                    delay = 700;
                }
                setTimeout( function () {
                    showJobDialog( currentCount, commandHost, serviceName, serviceResponse )
                }, delay );


            } else {

                let serviceDirectResponse = utils.json( serviceName, commandResultReport );
                if ( serviceDirectResponse ) {
                    // alertify.csapInfo( serviceDirectResponse ) ;

                    let delay = 100;
                    if ( currentCount > 1 ) {
                        delay = 700;
                    }
                    setTimeout( function () {
                        showJobDialog( currentCount, commandHost, serviceName, serviceDirectResponse );
                    }, delay );
                } else {
                    alertify.csapWarning( JSON.stringify( commandResultReport, "\n", "\t" ) );
                }

            }

        } else {

            let response = utils.json( "results", hostResponse );
            if ( ( response == "Request queued" )
                || ( directResponse == "Request queued" )
                || ( command == "stopServer" ) ) {
                // we switched to log view to view output via tail
                if ( !utils.isLaunchServiceLogs() ) {
                    alertify.csapInfo( response );
                }

            } else if ( directResponse ) {
                alertify.csapInfo( `${ commandHost }:  ${ directResponse }` );

            } else {
                // JSON.stringify( resultsJson, "\n", "\t" )
                //let hostResponse = utils.json( `clusteredResults.${ commandHost }`, resultsJson ) ;

                console.log( `processing hostReponse: `, hostResponse );

                if ( hostResponse && hostResponse.results ) {
                    hostResponse = hostResponse.results;
                }

                if ( !hostResponse ) {
                    // direct commands will not have clusterResutls
                    hostResponse = utils.json( `${ commandHost }`, commandResultReport );
                }
                alertify.csapInfo( `${ commandHost } - ${ command }:  ${ hostResponse }` );
            }
        }
        _postBacklogRefreshes = 0;
        update_status_until_backlog_empty();


    }

    function update_status_until_backlog_empty() {

        console.log( `update_status_until_backlog_empty: backlog: ${ $( "#backlog-count" ).text() } postRefreshes: ${ _postBacklogRefreshes }` );
        clearTimeout( refreshTimer );
        refreshTimer = setTimeout( function () {

            $.when( utils.refreshStatus( true ) ).done( function () {
                instancesRefreshFunction();
                let backlog = $( "#backlog-count" ).text();
                // console.log( `update_status_until_backlog_empty backlog: ${ backlog }, _postBacklogRefreshes: ${ _postBacklogRefreshes }` ) ;

                if ( backlog > 0 ) {
                    refreshTimer = setTimeout( update_status_until_backlog_empty, 1 * 5000 );
                    _postBacklogRefreshes = 0;
                } else {
                    // add a couple of lagging
                    if ( _postBacklogRefreshes++ < 3 ) {
                        refreshTimer = setTimeout( update_status_until_backlog_empty, 1 * 5000 );
                    }
                }
            } );


        }, 1000 );

    }

    function setAlertifyTitle( operation, dialog ) {
        let target = selectedHosts.length;

        if ( primaryInstance.clusterType == "kubernetes" ) {
            target = " kubernetes master: " + $kubernetesMastersSelect.val();
        } else if ( target === 1 ) {
            target = primaryHost;
        } else {
            target += " hosts";
        }
        let _lastOperation = operation + " service: " + serviceName + " on " + target;

        console.log( "setAlertifyTitle", _lastOperation );
        dialog.setting( {
            title: _lastOperation
        } );
    }


    function showStopDialog() {
        // Lazy create
        if ( !alertify.kill ) {
            let killDialogFactory = function factory() {
                return {
                    build: function () {
                        // Move content from template
                        this.setContent( $killOptions.show()[ 0 ] );
                    },
                    setup: function () {
                        return {
                            buttons: [ {
                                text: "Cancel",
                                className: alertify.defaults.theme.cancel,
                                key: 27/* Esc */
                            } ],
                            options: buildAlertifyOptions( "Stop Service Dialog" )
                        };
                    }

                };
            };

            alertify.dialog( 'kill', killDialogFactory, false, 'alert' );

            $killButton.click( function () {
                alertify.closeAll();
                killService();
            } );

            $stopButton.click( function () {
                alertify.closeAll();
                if ( serverType != "SpringBoot" ) {
                    stopService();
                    return;
                }


                let message = "Warning: service stops can take a while, and may never terminate the OS process."
                    + "<br><br>Use the CSAP Host Dashboard  and log viewer to monitor progress; use kill if needed."
                    + ' Unless specifically requested by service owner: <br><br>'
                    + '<div class="csap-info">kill option is preferred as it is an immediate termination</div>';

                _dialogs.showConfirmDialog(
                    "SpringBoot Stop Warning",
                    message,
                    function () { stopService() } ,
                    function () { console.log(`cancelled`) } ,
                    `Proceed With Stop`,
                    `Cancel`,
                    `warning`
                ) ;


            } );

        }

        let stopDialog = alertify.kill().show();

        let dialogTitle = "Stopping";

        _dom.csapDebug( "latest_instanceReport", latest_instanceReport )
        if ( latest_instanceReport.kubernetes ) {
            dialogTitle = "Removing kubernetes"
            //serviceHost = $kubernetesMastersSelect.val() ;
        }
        setAlertifyTitle( dialogTitle, stopDialog );

        _dialogs.resizeDialog( stopDialog, $killOptions.parent() );

    }

    function stopService() {


        let paramObject = {
            serviceName: serviceName
        };

        executeOnSelectedHosts( "stopServer", paramObject );

    }

    function killService() {

        let paramObject = new Object();

        if ( $( "#isSuperClean" ).is( ':checked' ) ) {
            // paramObject.push({noDeploy: "noDeploy"}) ;
            $.extend( paramObject, {
                clean: "super"
            } );
        } else {


            if ( $( "#clean-docker-volumes" ).is( ':checked' ) ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;
                $.extend( paramObject, {
                    clean: "cleanVolumes"
                } );
            } else if ( $( "#isClean" ).is( ':checked' ) ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;
                $.extend( paramObject, {
                    clean: "clean"
                } );
            }
            if ( $( "#isSaveLogs" ).is( ':checked' ) ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;
                $.extend( paramObject, {
                    keepLogs: "keepLogs"
                } );
            }


        }


        let message = serviceName + " is configured with warnings to prevent data loss.";
        message += "<br><br>Ensure procedures outlined by service owner have been followed to avoid data loss.";
        message += "<br><br> Click OK to proceed anyway, or cancel to use the stop button.";

        if ( latest_instanceReport.killWarnings ) {

            _dialogs.showConfirmDialog(
                "CSAP Kill Warning",
                message,
                function () { executeOnSelectedHosts( "killServer", paramObject ); },
                function () { console.log(`cancelled`) } ,
                `Proceed With Stopping`,
                `Cancel`,
                `warning`
            ) ;


        } else {
            executeOnSelectedHosts( "killServer", paramObject );
        }


    }

    function showDeployDialog() {

        // Lazy create
        if ( !alertify.deploy ) {

            createDeployDialog();
        }

        _deployDialog = alertify.deploy().show();

        if ( serverType === "docker" ) {
            $( "#template-repo-user" ).appendTo( $( "#docker-repo-user" ) );
            $( "#template-repo-pass" ).appendTo( $( "#docker-repo-pass" ) );
        }

        console.log( `showDeployDialog() clusterType: ${ primaryInstance.clusterType }` );
        if ( latest_instanceReport.kubernetes ) {

            console.log( "Updated text", latest_instanceReport.dockerSettings );
            maximizeDialog( _deployDialog );

            let dockerServiceSetting = JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" );
            if ( _kubernetesEditor == null ) {
                $( "#kubernetes-definition-text" ).text( dockerServiceSetting );
                //$( "#dockerImageVersion" ).parent().parent().append( $serviceParameters ) ;
                resize_alertify_element( $( "#kubernetes-definition-text" ), 100, 300 );


                setTimeout( function () {
                    _kubernetesEditor = aceEditor.edit( "kubernetes-definition-text" );
                    //editor.setTheme("ace/theme/twilight");
                    //editor.session.setMode("ace/mode/yaml");

                    _kubernetesEditor.setOptions( utils.getAceDefaults( "ace/mode/json" ) );
                }, 200 );
            } else {
                _kubernetesEditor.getSession().setValue( dockerServiceSetting );
                resize_alertify_element( $( "#kubernetes-definition-text" ), 100, 300 );
            }

        } else {
            _dialogs.resizeDialog( _deployDialog, $( "#deployDialog" ) );
        }
        setAlertifyTitle( "Deploying", _deployDialog );
        // $("#sourceOptions").fadeTo( "slow" , 0.5) ;


        if ( serviceName.indexOf( AGENT_NAME ) != -1 ) {
            _dialogs.showConfirmDialog(
                "CSAP Upgrade Confirmation",
                "CSAP Agent update: ensure latest csap linux package is installed.",
                function () { console.log( "proceeding deployment") } ,
                function () { alertify.closeAll() } ,
                // `Proceed With Update`,
                // `Abort update`,
                // csap-info or warning,
            ) ;
        }
    }


    function createDeployDialog() {

        console.log( "createDeployDialog() " );
        $( "#scmUserid", $osDeploy ).val( utils.getScmUser() );
        if ( utils.getScmUser().includes( "not-found" ) ) {
            $( "#scmUserid", $osDeploy ).val( "" );
        }

        let deployTitle = 'Service Deploy: <span title="After build/maven deploy on build host, artifact is deployed to other selected instances">'
            + primaryHost + "</span>"

        let okFunction = function () {
            let deployChoice = $( 'input[name=deployRadio]:checked' ).val();
            // alertify.success("Deployment using: "
            // +
            // deployChoice);
            alertify.closeAll();


            switch ( deployChoice ) {

                case "repo":
                    if ( $( "#deployServerServices input:checked" ).length == 0 ) {
                        deployService( true, serviceName );
                    } else {
                        $( "#deployServerServices input:checked" ).each( function () {
                            let curName = $( this ).attr( "name" );
                            deployService( true, curName );
                        } );
                    }
                    break;

                case "source":
                    deployService( false, serviceName );
                    break;

                case "upload":
                    uploadArtifact();
                    break;

            }

        }

        let deployDialogFactory = function factory() {
            return {
                build: function () {
                    // Move content from template
                    this.setContent( $( "#deployDialog" ).show()[ 0 ] );
                    this.setting( {
                        'onok': okFunction,
                        'oncancel': function () {
                            alertify.warning( "Cancelled Request" );
                        }
                    } );
                },
                setup: function () {
                    return {
                        buttons: [ { text: "Deploy Service", className: alertify.defaults.theme.ok, key: 0 },
                            { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                        ],
                        options: buildAlertifyOptions( deployTitle )
                    };
                }

            };
        };

        alertify.dialog( 'deploy', deployDialogFactory, false, 'confirm' );


        $( 'input[name=deployRadio]' ).change( function () {

            let sourceType = $( this ).val();
            console.log( `deployment source modified: '${ sourceType }'` );

            //
            //  artifactory deployments share fields with source deployments
            //
            if ( sourceType === 'repo' ) {
                $( "#template-repo-user" ).appendTo( $( "#repo-repo-user" ) );
                $( "#template-repo-pass" ).appendTo( $( "#repo-repo-pass" ) );
            } else {

                $( "#template-repo-user" ).appendTo( $( "#source-repo-user" ) );
                $( "#template-repo-pass" ).appendTo( $( "#source-repo-pass" ) );
            }

            $( "#osDeployOptions >div" ).hide();

            let $selectedDiv = $( `#${ sourceType }Options` );
            $selectedDiv.show();

            _dialogs.resizeDialog( _deployDialog, $( "#deployDialog" ) );

        } );

        $( 'input[name=deployRadio]:checked' ).trigger( "change" );


        $( '#scmPass' ).keypress( function ( e ) {
            if ( e.which == 13 ) {
                $( '.ajs-buttons button:first',
                    $( "#deployDialog" ).parent().parent().parent() )
                    .trigger( "click" );
            }
        } );


        $( '#cleanServiceBuild' ).click( function () {
            cleanServiceBuild( false );
            return false; // prevents link
        } );

        $( '#cleanGlobalBuild' ).click( function () {
            cleanServiceBuild( true );
            return false; // prevents link
        } );


    }

    function uploadArtifact() {

        let fileNameWithFakePath = $( "#uploadOptions :file" ).val();
        let fileNameOnly = fileNameWithFakePath.substring( fileNameWithFakePath.lastIndexOf( "\\" ) + 1 );

        let uploadMessage = `Uploading artifact for ${ serviceShortName }: ${ fileNameOnly }`
            + `<div class="csap-red percent-upload"> </div>`;
        utils.loading( uploadMessage );
        //        showResultsDialog( "Uploading artifact: " +  ;
        //
        //
        //        displayResults( "" ) ;
        $resultsPre.append( '<div class="progress"><div class="bar"></div ><div class="percent">0%</div ></div>' );

        $( "#upService" ).val( serviceName );
        // <input type="hidden " name="hostName" value="" />
        $( "#upHosts" ).empty();
        for ( let host of selectedHosts ) {
            jQuery( '<input/>', {
                type: "hidden",
                value: host,
                name: "hostName"
            } ).appendTo( $( "#upHosts" ) );
        }
        //        $( "#instanceTable *.selected" ).each( function () {
        //
        //            let reqHost = $( this ).data( "host" ) ; // (this).data("host")
        //            $( "#upHosts" ).append( '<input type="hidden" name="hostName" value="' + reqHost + '" />' ) ;
        //
        //        } ) ;

        let $percent = $( '.percent-upload' ).css( "width", "4em" );
        let status = $( '#status' );

        let formOptions = {
            beforeSend: function () {
                $( 'body' ).css( 'cursor', 'wait' );

                status.empty();
                let percentVal = '0%';
                $percent.html( percentVal );

            },
            uploadProgress: function ( event, position, total, percentComplete ) {
                let percentVal = percentComplete + '%';
                $percent.html( percentVal );
            },
            success: function () {
                let percentVal = '100%';
                $percent.html( percentVal );

            },
            complete: function ( xhr ) {
                //console.log( `xhr response: `, xhr )
                utils.loadingComplete();
                $( 'body' ).css( 'cursor', 'default' );
                let percentVal = '100%';
                $percent.html( percentVal );
                // status.html(xhr.responseText);
                // $("#resultPre").html( xhr.responseText ) ;
                displayResults( xhr.responseJSON );
            }
        };

        $( '#uploadOptions form' ).ajaxSubmit( formOptions );


    }


    function deployService( isMavenDeploy, deployServiceName ) {

        let copyToHosts = Array.from( selectedHosts );
        copyToHosts.splice( 0, 1 );

        console.log( `deployService: selectedHosts ${ selectedHosts } ,  copyToHosts ${ copyToHosts } ` )

        let deployHost = primaryHost;
        if ( latest_instanceReport.kubernetes ) {
            console.log( `Cluster type is kubernetes, deployment is only on ${ $kubernetesMastersSelect.val() }` );
            deployHost = $kubernetesMastersSelect.val();
            copyToHosts = new Array();
        }

        let autoStart = $( "#deployStart" ).is( ":checked" );

        let paramObject = {
            scmUserid: $( "#scmUserid" ).val(),
            scmPass: $( "#scmPass" ).val(),
            repoPass: $( "#repoPass" ).val(),
            scmBranch: $( "#scmBranch" ).val(),
            commandArguments: $serviceParameters.val(),
            targetScpHosts: copyToHosts,
            serviceName: deployServiceName,
            hostName: deployHost
        };

        if ( serverType == "docker" ) {
            if ( latest_instanceReport.kubernetes ) {
                $.extend( paramObject, {
                    //dockerImage: $( "#dockerImageVersion" ).val(),
                    // mavenDeployArtifact: $( "#kubernetes-definition-text" ).val()
                    mavenDeployArtifact: _kubernetesEditor.getValue()
                } );

            } else {
                $.extend( paramObject, {
                    //dockerImage: $( "#dockerImageVersion" ).val(),
                    mavenDeployArtifact: $( "#dockerImageVersion" ).val()
                } );
            }
        } else if ( isMavenDeploy ) {
            // paramObject.push({noDeploy: "noDeploy"}) ;
            let artifact = $( "#mavenArtifact" ).val();
            $.extend( paramObject, {
                mavenDeployArtifact: artifact
            } );
            console.log( "Number of ':' in artifact", artifact.split( ":" ).length );
            if ( artifact.split( ":" ).length != 4 ) {
                $( "#mavenArtifact" ).css( "background-color", "#f5bfbf" );
                alertify.csapWarning( "Unexpected format of artifact. Typical is a:b:c:d  eg. org.csap:BootEnterprise:1.0.27:jar" );
                //return ;
            } else {
                $( "#mavenArtifact" ).css( "background-color", "#CCFFE0" );
            }
        } else {

            let scmCommand = $( "#scmCommand" ).val();

            if ( scmCommand.includes( "deploy" )
                && $( "#isScmUpload" ).is( ':checked' ) ) {
                scmCommand += " deploy";
            }

            $.extend( paramObject, {
                scmCommand: scmCommand
            } );
        }


        if ( $( "#deployServerServices input:checked" ).length > 0 ) {
            $( "#deployStart" ).prop( 'checked', false );
            // default params are used when multistarts

            delete paramObject.commandArguments;
            delete paramObject.runtime;
            delete paramObject.scmCommand;
            delete paramObject.mavenDeployArtifact;
            $.extend( paramObject, {
                mavenDeployArtifact: "default"
            } );

        }


        if ( $( "#isHotDeploy" ).is( ':checked' ) ) {
            $.extend( paramObject, {
                hotDeploy: "hotDeploy"
            } );
        }

        let buildUrl = SERVICE_URL + "/rebuildServer";
        // buildUrl = "http://yourlb.yourcompany.com/admin/services" +
        // "/rebuildServer" ;

        utils.loading( `Adding ${ deployServiceName } to the deployment queue ` );
        $.post( buildUrl, paramObject )
            .done( function ( results ) {

                utils.loadingComplete();
                displayHostResults( deployHost, deployServiceName, "Build Started", results, 0, 0 );

                // $("#resultPre div").first().show() ;
                $( "#resultPre div" ).first().css( "display", "block" );

                fileOffset = "-1";

                _deploySuccess = false;


                // show logs
                utils.launchServiceLogs( "deploy" );

                getDeployLogs( deployServiceName );

            } )
            .fail( function ( jqXHR, textStatus, errorThrown ) {

                if ( deployServiceName.indexOf( AGENT_NAME ) != -1 ) {
                    alert( "csap agent can get into race conditions...." );
                    let numHosts = selectedHosts.length;

                    if ( numHosts > 1 && results.indexOf( "BUILD__SUCCESS" ) != -1 ) {
                        isBuild = true; // rebuild autostarts
                        startService();
                    } else {
                        $( 'body' ).css( 'cursor', 'default' );
                    }
                } else {
                    _dialogs.showJsonError(  rebuild, jqXHR );
                }
            } );

    }

    function getDeployLogs( nameOfService ) {

        // $('#serviceOps').css("display", "inline-block") ;

        let serviceHost = primaryHost;
        if ( latest_instanceReport.kubernetes ) {
            serviceHost = $kubernetesMastersSelect.val();
        }

        // console.log("Hitting Offset: " + fileOffset) ;
        let requestParms = {
            serviceName: nameOfService,
            hostName: serviceHost,
            logFileOffset: fileOffset
        };

        $.getJSON(
            SERVICE_URL + "/query/deployProgress",
            requestParms )

            .done( function ( hostJson ) {
                wait_for_build_complete( hostJson, nameOfService );
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {
                _dialogs.showJsonError(  `Retrieving changes for file: '${ $( "#logFileSelect" ).val() }'`, jqXHR );
            } );
    }


    function wait_for_build_complete( changesJson, nameOfService ) {

        if ( changesJson.error || changesJson.contents == undefined ) {
            console.log( "No results found, rescheduling" );
            setTimeout( function () {
                getDeployLogs( nameOfService );
            }, LOG_CHECK_INTERVAL );
            return;
        }
        // $("#"+ hostName + "Result").append("<br>peter") ;
        // console.log( JSON.stringify( changesJson ) ) ;
        // console.log("Number of changes :" + changesJson.contents.length);

        let previousBlock = "";
        for ( let currentChangeBlock of changesJson.contents ) {

            console.debug( `logs: ${ currentChangeBlock }` );
            // TOKEN may get split in lines, so check text results for success and complete tokens
            let check_for_tokens_block = previousBlock + currentChangeBlock;
            previousBlock = currentChangeBlock;


            if ( check_for_tokens_block.includes( "BUILD__SUCCESS" ) ) {
                console.log( `\n\n\n FOUND BUILD__SUCCESS  \n\n\n` );
                _deploySuccess = true;
            }

            // setTimeout( () => startService(), 1000 );
            // return ;

            if ( check_for_tokens_block.includes( "__COMPLETED__" ) ) {


                console.log( `\n\n\n FOUND __COMPLETED__  \n\n\n` );

                if ( _deploySuccess ) {
                    isBuild = true;

                    if ( $( "#deployStart" ).is( ':checked' ) ) {
                        // delay to allow deploy queues to clear
                        setTimeout( () => startService(), 1000 );
                    }

                } else {

                    alertify.csapWarning( "BUILD__SUCCESS not found in output - review logs" );
                }

                return;
            }
        }


        fileOffset = changesJson.newOffset;


        setTimeout( function () {
            getDeployLogs( nameOfService );
        }, LOG_CHECK_INTERVAL );


    }


    function cleanServiceBuild( isGlobal ) {

        let paramObject = {
            serviceName: serviceName,
            hostName: primaryHost
        };

        if ( isGlobal ) {
            $.extend( paramObject, {
                global: "GLOBAL"
            } );
        }

        $.post( SERVICE_URL + "/purgeDeployCache", paramObject,
            function ( results ) {

                displayResults( results, false );

            } );

    }

    function displayResults( resultReport, append ) {

        console.log( `displayResults() : `, resultReport );
        if ( resultReport.plainText ) {

            alertify.csapInfo( resultReport.plainText );

        } else {
            let results = JSON.stringify( resultReport, null, "  " );

            if ( results.includes( utils.getErrorIndicator() )
                || results.includes( utils.getWarningIndicator() ) ) {

                alertify.csapWarning( results );
            } else {
                alertify.csapInfo( results );
            }
        }


    }


}