
// const explorerSources = [
//     "browser/utils",
//     "agent/explorer-pod-logs",
//     "agent/explorer-progress",
//     "agent/host-operations",
//     "ace/ace",
//     "editor/json-forms",
//     "jsYaml/js-yaml" ] ;
// //
// //
// //
// define( explorerSources, function ( utils, podLogs, explorerProgress, hostOperations, aceEditor, jsonForms, jsYaml ) {


//     console.log( "Module loaded" ) ;

import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";
import _dialogs from "../../utils/dialog-utils.js";


import jsonForms from "../../editor/json-forms.js";


import utils from "../utils.js";

import podLogs from "./explorer-pod-logs.js"
import explorerProgress from "./explorer-progress.js"
import hostOperations from "./host-operations.js"


import { aceEditor, jsYaml } from "../../libs/file-editor.js"


const explorerOps = explorer_ops();

export default explorerOps


function explorer_ops() {

    _dom.logHead( "Module loaded" );

    const $agentTabButton = utils.findNavigation( "#agent-tab" );
    const $agentTabContent = utils.findContent( "#agent-tab-content" );
    const $kubernetesTemplates = $( "#kubernetes-dashboard-templates" );

    let $dockerTree;
    let $kubernetesNameSpaceSelect;


    let _dockerCommandUrl = utils.getOsExplorerUrl() + "/docker"
    let _dockerContainerPath = "docker/container/";
    let _crioContainerPath = "crio/container/";
    let _containerCommandUrl = utils.getOsExplorerUrl() + "/" + _dockerContainerPath;

    let _jsonParseTimer, _loadTimer;

    let _namespaces = new Array();


    let $containerControls, $imageControls, $linuxServiceControls;

    let $k8PodControls, $k8DeployControls, $k8ServiceControls, $k8StatefulControls,
        $k8DaemonControls, $k8ReplicasetControls, $k8VolumeClaimControls,
        $k8EventControlTemplate;
    let _currentImageNode, _currentContainerNode, _currentLinuxServiceNode;

    let _current_ft_node;

    let _yamlAlertifyInstance;

    let _last_event_count = 0;

    let $pullImageDialog, $pullInput, $cleanImageDialog;

    let _yamlEditor;
    let $aceWrapCheckbox;

    const $yamlEditorDialog = $( "#yaml-editor-dialog", $kubernetesTemplates );
    const $yamlFolderCheck = $( "#ace-fold-checkbox", $yamlEditorDialog );
    const $yamlSpacing = $( "#yaml-op-spacing", $yamlEditorDialog );
    const $yamlText = $( "#yaml-editor-container", $yamlEditorDialog );

    let _proxyAlertify;
    const $serviceProxyDialog = $( "#service-proxy-dialog", $kubernetesTemplates );
    const $serviceProxyTable = $( "tbody", $kubernetesTemplates );


    let $processKillDialog;

    let CONTAINER_TYPE = "/container/";
    let LINUX_SERVICE_TYPE = "/services/linux/";
    let IMAGE_TYPE = "/image/";

    let treeHidden = false;


    let $quotaDialog;

    let _initComplete = false;


    let _podsByOwner;

    let _dockerContainerNames;


    return {

        setPodsByOwner: function ( podsByOwner ) {
            _podsByOwner = podsByOwner;
        },

        setDockerContainers: function ( containerNames ) {
            _dockerContainerNames = containerNames;
        },

        /**
         * @description refresh the ui with the latest host state
         * @returns {Promise}
         */
        refresh_host_status: function () {
            return refresh_host_status();
        },

        addCsapTreeCommands: function ( parentId, $commands ) {
            addCsapTreeCommands( parentId, $commands );
        },

        kubernetesNameSpace: function () {
            return $kubernetesNameSpaceSelect.val();
        },

        kubernetes_yaml_dialog: function () {
            kubernetes_yaml_dialog();
        },

        service_proxy_dialog: function ( $link ) {
            service_proxy_dialog( $link );
        },

        containerCommandPath: function () {
            return _dockerContainerPath;
        },

        crioContainerCommandPath: function () {
            return _crioContainerPath;
        },

        initialize: function ( dockerTree ) {

            // THis gets moved around - so hang on 
            $kubernetesNameSpaceSelect = $( "#kubernetes-namespace-select" );

            explorerProgress.initialize();

            $dockerTree = dockerTree;
            if ( !_initComplete ) {
                initialize();

            }
        },


        refreshFolder: function ( location, forceOpen, closeNodeType ) {

            reload_folder( location, forceOpen, closeNodeType );
        },

        buildTreeSummary: function ( loadResponse ) {

            buildTreeSummary( loadResponse );

        },

        showPullImagePrompt: function () {
            showPullImagePrompt();
        },

        showCleanImagePrompt: function () {
            showCleanImagePrompt();
        },

        adminOperation: function ( type, selectedNode, operation, skipPrompt ) {
            adminOperation( type, selectedNode, operation, skipPrompt );
        },

        getCurrentImage: function () {
            return _currentImageNode;
        },

        updateSelectedNode: function ( fancyTreeNode ) {
            updateSelectedNode( fancyTreeNode );
        },

        hideTreeCommands: function ( includeSystem ) {
            hideTreeCommands( includeSystem );
        },

        showAddDiskPrompt: function ( path, uri ) {
            showAddDiskPrompt( path, uri );
        }
    }

    function hideTreeCommands( includeSystem ) {

        console.log( "hideTreeCommands()" );
        // fancy tree will delete the time if left inside
        restoreTemplate( $k8DeployControls );
        restoreTemplate( $k8StatefulControls );
        restoreTemplate( $k8ReplicasetControls );
        restoreTemplate( $k8VolumeClaimControls );


        restoreTemplate( $k8DaemonControls );

        restoreTemplate( $k8PodControls );
        restoreTemplate( $k8ServiceControls );
        restoreTemplate( $containerControls );
        restoreTemplate( $imageControls );
        restoreTemplate( $linuxServiceControls );

        if ( includeSystem ) {
            restoreTemplate( $( "#kubernetes-namespace" ), $dockerTree );
        }

    }

    function initialize() {
        _initComplete = true;
        console.log( "Initializing" );

        $( "#yaml-editor-operations button" ).click( function () {

            let $button = $( this );
            _dialogs.showConfirmDialog(
                `Confirmation Dialog ${ $button.text() }`,
                `Operation cannot be undone, proceed with caution`,
                function () {  yamlEditorOperation( $button ) },
                function () { console.log(`cancelled`) } ,
                `Perform: ${ $button.text() }`,
                `Cancel`,
                `warning`
            ) ;

        } );

        $yamlSpacing.change( function () {

            alertify.csapInfo( "close/reopen dialog to view updated format" );

        } )

        $linuxServiceControls = $( "#linuxServiceControls" );
        $( "button", $linuxServiceControls ).click( function () {
            adminOperation(
                LINUX_SERVICE_TYPE,
                _currentLinuxServiceNode,
                $( this ).data( "command" ) );
        } );

        $containerControls = $( "#containerControls" );

        $( "select", $containerControls ).change( function () {

            adminOperation(
                CONTAINER_TYPE,
                _currentContainerNode,
                $( "select", $containerControls ).val() )

            // reset to perform next
            $( "select", $containerControls )[ 0 ].selectedIndex = 0;
        } );


        $( "button", $containerControls ).click( function () {
            adminOperation(
                CONTAINER_TYPE,
                _currentContainerNode,
                $( this ).data( "command" ) );
        } );

        $k8DeployControls = $( "#kubernetes-deploy-controls" );
        $( "button", $k8DeployControls ).click( function () {
            kubernetes_perform_command( $( this ) );
        } );


        $k8StatefulControls = $( "#kubernetes-statefulset-controls" );
        $( "button", $k8StatefulControls ).click( function () {
            kubernetes_perform_command( $( this ) );
        } );


        $k8ReplicasetControls = $( "#kubernetes-replicaset-controls" );
        $( "button", $k8ReplicasetControls ).click( function () {
            kubernetes_perform_command( $( this ) );
        } );

        $k8VolumeClaimControls = $( "#kubernetes-volume-claim-controls" );
        $( "button", $k8VolumeClaimControls ).click( function () {
            kubernetes_perform_command( $( this ) );
        } );


        $k8DaemonControls = $( "#kubernetes-daemonset-controls" );
        $( "button", $k8DaemonControls ).click( function () {
            kubernetes_perform_command( $( this ) );
        } );


        $k8PodControls = $( "#kubernetes-pod-controls" );
        $( "button", $k8PodControls ).click( function () {
            kubernetes_perform_command( $( this ) );
        } );

        $k8ServiceControls = $( "#kubernetes-service-controls" );
        $( "button", $k8ServiceControls ).click( function () {
            kubernetes_perform_command( $( this ) );
        } );

        $imageControls = $( "#imageControls" );

        $( "select", $imageControls ).change( function () {

            let operation = "";
            adminOperation(
                IMAGE_TYPE,
                _currentImageNode,
                $( "select", $imageControls ).val() )

            // reset to perform next
            $( "select", $imageControls )[ 0 ].selectedIndex = 0;
        } );


        $( "#imageBatch, #imageRemove", $imageControls ).off().click( function () {
            adminOperation(
                IMAGE_TYPE,
                _currentImageNode,
                $( this ).data( "command" ) );
        } );



        _yamlEditor = aceEditor.edit( $yamlText.attr( "id" ) );
        //editor.setTheme("ace/theme/twilight");
        //editor.session.setMode("ace/mode/yaml");

        $aceWrapCheckbox = $( '#ace-wrap-checkbox', $( "#yaml-editor-head" ) );
        _yamlEditor.setOptions( utils.getAceDefaults( "ace/mode/yaml" ) );

        _yamlEditor.getSession().on( 'change', function () {
            console.log( "yaml updated - checking for errors" );

            // yaml or text mode depending on ...
            try {
                const updatedYaml = jsYaml.loadAll( _yamlEditor.getSession().getValue() );
                _yamlEditor.getSession().clearAnnotations();
                //utils.enableButtons( $jsonSave, $jsonMode ) ;
            } catch ( e ) {
                console.log( `yaml parsing error ${ e.message }` );
                // console.log( e );
                utils.markAceYamlErrors( _yamlEditor.getSession(), e );
                //utils.disableButtons( $jsonSave, $jsonMode ) ;
            }
        } );

        $aceWrapCheckbox.change( function () {
            if ( isAceWrapChecked() ) {
                _yamlEditor.session.setUseWrapMode( true );

            } else {
                _yamlEditor.session.setUseWrapMode( false );
            }
        } );


        $yamlFolderCheck.change( function () {
            if ( $( this ).is( ':checked' ) ) {
                _yamlEditor.getSession().foldAll( 2 );
            } else {
                //_yamlEditor.getSession().unfoldAll( 2 ) ;
                _yamlEditor.getSession().unfold();
            }
        } )


        $( "#ace-theme-select", $( "#yaml-editor-head" ) ).change( function () {
            _yamlEditor.setTheme( $( this ).val() );
        } );

        $( "#kubernetes-yaml-clone" ).change( function () {

            let count = $( this ).val();

            console.log( `updating content ${ count } times` );
            let currentContent = _yamlEditor.getSession().getValue();
            _yamlEditor.getSession().setValue( "", -1 );

            for ( let i = 1; i <= count; i++ ) {
                _yamlEditor.getSession().insert( {
                    row: _yamlEditor.getSession().getLength(),
                    column: 0
                }, "\n" + currentContent.replaceAll( "XXX", i ) );
            }

        } );

        $( "#kubernetes-yaml-select" ).change( function () {

            let templateName = $( "#kubernetes-yaml-select" ).val();
            if ( templateName == "load" )
                return;

            $( "#last-loaded-yaml" ).text( templateName ).attr( "title", templateName );
            ;
            $( this ).val( "load" );

            let templateTextLoader = function ( content ) {

                console.log( `loading editor with retreived template` )
                content = content.replaceAll( "$$service-fqdn-host", utils.getHostFqdn() );

                // $( "#yaml-editor-container" ).val( content ) ;
                _yamlEditor.getSession().setValue( content, -1 );
                $yamlFolderCheck.trigger( "change" );
                //_yamlEditor.getSession().foldAll( 2 ) ;
                //_yamlEditor.gotoLine(1);
            };

            $.get( utils.getOsExplorerUrl() + "/kubernetes/template/" + templateName,
                templateTextLoader,
                'text' );

            //$( "#kubernetes-yaml-select" ).val( "load" ) ;
        } );


        $cleanImageDialog = $( "#image-clean-dialog" );

        $pullImageDialog = $( "#image-pull-dialog" );
        $pullInput = $( "#pullName" );
        $("#pull-repo-user").val( `${ window.CSAP_USER }@repo`) ;
        let $pullSelect = $( "select", $pullImageDialog );
        $pullSelect.val( "none" );

        $pullSelect.change( function() {
            let selected = $pullSelect.val();
            console.log( "selected", selected );
            if ( selected == "none" )
                return;
            $pullInput.val( selected );
            $pullSelect.val( "none" );
        })

        // $pullSelect.selectmenu( {
        //     width: "2em",
        //     change: function () {
        //
        //         let selected = $pullSelect.val();
        //         console.log( "selected", selected );
        //         if ( selected == "none" )
        //             return;
        //         $pullInput.val( selected );
        //         $pullSelect.val( "none" );
        //         $pullSelect.selectmenu( "refresh" );
        //
        //     }
        // } );


        $quotaDialog = $( "#cpuQuotaDialog" );
        $( "input", $quotaDialog ).keyup( function () {
            this.value = this.value.replace( /[^0-9\.]/g, '' );
            let $cpuPeriod = $( "#promptCpuPeriod" );
            let $cpuQuota = $( "#promptCpuQuota" );

            let coresUsed = $cpuQuota.val() / $cpuPeriod.val();

            $( "#promptCpuCoresUsed" ).text( coresUsed.toFixed( 1 ) );
        } );

        setTimeout( function () {
            refresh_host_status()
        }, 1000 );


    }

    function isAceWrapChecked() {
        if ( $aceWrapCheckbox.prop( 'checked' ) ) {
            return true;
        } else {
            return false;
        }

    }

    function updateSelectedNode( fancyTreeNode ) {

        console.log( `updateSelectedNode() node type: ${ fancyTreeNode.type }` );
        hideTreeCommands();

        _current_ft_node = fancyTreeNode;

        if ( _current_ft_node.data && _current_ft_node.data.path
            && _current_ft_node.data.path == "/" ) {

            return; // no menus on root nodes
        }


        addApiPathButtons( fancyTreeNode );

        switch ( fancyTreeNode.type ) {

            case hostOperations.categories().dockerImages:
                _currentImageNode = fancyTreeNode.data;

                $( ".lastImage", $dockerTree ).removeClass( "lastImage" );
                fancyTreeNode.addClass( "lastImage" );
                addCsapTreeCommands( "lastImage", $imageControls );
                break

            case hostOperations.categories().dockerContainers:
                _currentContainerNode = fancyTreeNode.data;

                $( ".lastContainer", $dockerTree ).removeClass( "lastContainer" );
                fancyTreeNode.addClass( "lastContainer" );
                addCsapTreeCommands( "lastContainer", $containerControls );
                break

            case hostOperations.categories().kubernetesDeployments:
                $( ".lastDeploy", $dockerTree ).removeClass( "lastDeploy" );
                fancyTreeNode.addClass( "lastDeploy" );
                addCsapTreeCommands( "lastDeploy", $k8DeployControls );
                break


            case hostOperations.categories().kubernetesStatefulSets:
                $( ".lastStatefulSet", $dockerTree ).removeClass( "lastStatefulSet" );
                fancyTreeNode.addClass( "lastStatefulSet" );
                addCsapTreeCommands( "lastStatefulSet", $k8StatefulControls );
                break

            case hostOperations.categories().kubernetesReplicaSets:
                $( ".lastReplicaSet", $dockerTree ).removeClass( "lastReplicaSet" );
                fancyTreeNode.addClass( "lastReplicaSet" );
                addCsapTreeCommands( "lastReplicaSet", $k8ReplicasetControls );
                break

            case hostOperations.categories().kubernetesVolumeClaims:
                $( ".lastVolumeClaimSet", $dockerTree ).removeClass( "lastVolumeClaimSet" );
                fancyTreeNode.addClass( "lastVolumeClaimSet" );
                addCsapTreeCommands( "lastVolumeClaimSet", $k8VolumeClaimControls );
                break



            case hostOperations.categories().kubernetesDaemonSets:
                $( ".lastDaemonSet", $dockerTree ).removeClass( "lastDaemonSet" );
                fancyTreeNode.addClass( "lastDaemonSet" );
                addCsapTreeCommands( "lastDaemonSet", $k8DaemonControls );
                break

            case hostOperations.categories().kubernetesPods:
                $( ".lastPod", $dockerTree ).removeClass( "lastPod" );
                fancyTreeNode.addClass( "lastPod" );
                addCsapTreeCommands( "lastPod", $k8PodControls );
                break

            case hostOperations.categories().kubernetesServices:
                $( ".lastK8Service", $dockerTree ).removeClass( "lastK8Service" );
                fancyTreeNode.addClass( "lastK8Service" );
                addCsapTreeCommands( "lastK8Service", $k8ServiceControls );
                break


        }

    }

    function addCsapTreeCommands( parentId, $commands ) {
        if ( $( "." + parentId + " div.csap-tree-commands", $dockerTree ).length == 0 ) {

            let $csapTreeCommand = jQuery( '<div/>', { class: "csap-tree-commands", text: "" } );
            $( "." + parentId, $dockerTree ).append( $csapTreeCommand );
        }
        $( "." + parentId + " div.csap-tree-commands", $dockerTree ).append( $commands );
    }

    function remove_folder( explorerType ) {

        let tree = $dockerTree.fancytree( 'getTree' );
        let firstNodeMatch = tree.findFirst( function ( fancyTreeNode ) {
            return isNodeMatched( fancyTreeNode, explorerType, "/" );
        } );
        $( firstNodeMatch.li ).hide();
        //firstNodeMatch.remove()
    }

    function disable_folder( explorerType ) {

        let tree = $dockerTree.fancytree( 'getTree' );
        let firstNodeMatch = tree.findFirst( function ( fancyTreeNode ) {
            return isNodeMatched( fancyTreeNode, explorerType, "/" );
        } );

        // http://www.wwwendt.de/tech/fancytree/doc/jsdoc/FancytreeNode.html
        $( "label.summary", firstNodeMatch.li )
            .css( "color", "grey" )
            .css( "font-style", "italic" );
    }

    function reload_folder( openNodeType, forceOpen, closeNodeType ) {

        let tree = $dockerTree.fancytree( 'getTree' );

        if ( closeNodeType ) {
            let closeNodeMatch = tree.findFirst( function ( fancyTreeNode ) {
                return isNodeMatched( fancyTreeNode, closeNodeType, "/" );
            } );
            closeNodeMatch.setExpanded( false );
        }

        let firstNodeMatch = tree.findFirst( function ( fancyTreeNode ) {
            return isNodeMatched( fancyTreeNode, openNodeType, "/" );
        } );

        if ( !firstNodeMatch ) {
            console.log( `Node not found ${ openNodeType } ` );
            return;
        }

        if ( forceOpen || firstNodeMatch.isExpanded() ) {
            firstNodeMatch.setExpanded( false );
            setTimeout( () => {
                firstNodeMatch.setExpanded( true );
            }, 500 );
        }
    }

    function isNodeMatched( fancyTreeNode, type, path ) {
        //console.log("findByPath", treeData, path) ;
        if ( fancyTreeNode.data.path ) {
            return ( fancyTreeNode.data.path == path )
                && fancyTreeNode.type == type;
        }

        return fancyTreeNode.type == type;
    }


    function getContainerId() {
        //return _currentContainerNode.containerName ;
        return _currentContainerNode.attributes.Id;
    }


    function showErrorDialog( operation, error, reason ) {
        let $description = jQuery( '<div/>', {} );
        let $warn = jQuery( '<div/>', { class: "warning" } );
        $warn.append( error );
        $warn.append( "<br>Reason: " + reason );
        $warn.appendTo( $description );
        alertify.alert( "Failed Operation: " + operation, $description.html() );

    }

    function spanForValue( value, limit ) {
        let $description = jQuery( '<div/>', {} );
        if ( value > limit ) {
            jQuery( '<span/>', {
                class: "warning",
                html: value
            } ).appendTo( $description );
        } else {
            jQuery( '<span/>', {
                class: "normal",
                html: value
            } ).appendTo( $description );
        }

        return $description.html();
    }

    function setComment( id, comment ) {
        $( `#${ id }`, $dockerTree ).html( comment );
    }

    function buildTreeSummary( loadResponse ) {

        let htmlSpacer = '<span style="padding-left: 3em"></span>';

        $( "#cpuTree" ).html(
            "Cpu: " + spanForValue( loadResponse.cpu, 70 ) + "%"
            + htmlSpacer
            + " current load: "
            + spanForValue( loadResponse.cpuLoad, loadResponse.cpuCount )
            + " on " + loadResponse.cpuCount + " cores" );



        let memoryComment =
            spanForValue( loadResponse.memory.total, 99 )
            + ", free: " + spanForValue( loadResponse.memory.free, 99 )
            + htmlSpacer + 'swap: ' + loadResponse.memory.swapTotal + " swap free: " + loadResponse.memory.swapFree;

        setComment( "memoryTree", memoryComment );
        setComment( "processTree", spanForValue( loadResponse.processCount, 500 ) + " active processes" );
        setComment( "csapServiceTree", spanForValue( loadResponse.csapCount, 100 ) + " services" );
        setComment( "linuxTree", spanForValue( loadResponse.linuxServiceCount, 50 ) + " services" );
        setComment( "chef-commandsTree", spanForValue( loadResponse.chefCookBookCount, 150 ) + " cookbooks" );
        setComment( "network-routesTree", spanForValue( loadResponse.linuxInterfaceCount, 50 ) + " interfaces" );
        setComment( "packageTree", spanForValue( loadResponse.linuxPackageCount, 600 ) + " packages" );
        setComment( "diskTree", spanForValue( loadResponse.diskCount, 200 ) + " partitions mounted" );
        setComment( "linuxDefTree", loadResponse.osVersion );

        if ( loadResponse.docker ) {

            let containerComment =
                "version: " + spanForValue( loadResponse.docker.version, "zz" )
                + ", storage: " + spanForValue( loadResponse.docker.dockerStorage + "Gb", "zz" )
                + ` ( ${ containerUrl } ) `;


            setComment( "configTree", containerComment );
            setComment( "containerTree", spanForValue( loadResponse.docker.containerCount, 100 )
                + " total, " + loadResponse.docker.containerRunning + " running" );
            setComment( "cri-commandsTree", spanForValue( loadResponse.docker.crioContainerCount, 100 ) + " containers" );
            setComment( "imageTree", spanForValue( loadResponse.docker.imageCount, 100 ) + " Images" );
            setComment( "docker-volumeTree", spanForValue( loadResponse.docker.volumeCount, 100 ) + " Volumes" );
            setComment( "docker-networkTree", spanForValue( loadResponse.docker.networkCount, 10 ) + " Networks" );


        } else {
            setComment( "configTree", "Not Available" );
            disable_folder( hostOperations.categories().dockerConfig );
            remove_folder( hostOperations.categories().dockerContainers );
            remove_folder( hostOperations.categories().dockerImages );
            remove_folder( hostOperations.categories().dockerVolumes );
            remove_folder( hostOperations.categories().dockerNetworks );

        }


        if ( loadResponse.kubernetes
            && loadResponse.kubernetes.heartbeat
            && !loadResponse.kubernetes.error ) {

            if ( treeHidden ) {

                treeHidden = false;

                setTimeout( function () {
                    utils.launchMenu( "agent-tab,explorer" );
                }, 200 )

                return;
            }

            $( "div.comment.k8-loading", $dockerTree ).removeClass( "csap-loading" );


            let currentNamespace = $kubernetesNameSpaceSelect.val();

            let namespaces = loadResponse.kubernetes.namespaces;
            if ( Array.isArray( namespaces )
                && JSON.stringify( namespaces ) !== JSON.stringify( _namespaces ) ) {

                console.log( `updating namespaces: ${ namespaces } ` );
                _namespaces = namespaces;
                $kubernetesNameSpaceSelect.empty();
                let optionItem = jQuery( '<option/>', {
                    value: "all",
                    text: "all"
                } );
                $kubernetesNameSpaceSelect.append( optionItem );
                //if  (loadResponse.kubernetes.namespaces ) {
                for ( let namespace of namespaces ) {
                    let $option = jQuery( '<option/>', {
                        text: namespace
                    } );
                    if ( currentNamespace === namespace ) {
                        $option = jQuery( '<option/>', {
                            text: namespace,
                            selected: "selected"
                        } );
                    }
                    $kubernetesNameSpaceSelect.append( $option );
                }
            }



            build_kubernetes_configuration( $( "#k8ConfigurationTree", $dockerTree ), loadResponse.kubernetes.version );

            let podSummary = spanForValue( loadResponse.kubernetes.podReport.count, 200 ) + " Pods";
            if ( loadResponse.kubernetes.podReport.restarts > 0 ) {
                podSummary += ",  Restarts: " + spanForValue( loadResponse.kubernetes.podReport.restarts, 0 );
            }
            setComment( "k8PodTree", podSummary );

            $( "#k8EventTree", $dockerTree )
                .css( "background-color", "white" );
            //console.log( "_last_event_count: ", _last_event_count, "loadResponse.kubernetes.eventCount", loadResponse.kubernetes.eventCount )
            let eventWarningLimit = 500;
            let changeMessage = "";

            if ( _last_event_count != loadResponse.kubernetes.eventCount ) {
                if ( _last_event_count != 0 ) {
                    //alertify.notify( "New kubernetes events" ) ;
                    let changeCount = ( loadResponse.kubernetes.eventCount - _last_event_count );
                    changeMessage = " (change: " + changeCount + ")";
                    reload_kubernetes_folders();
                }
                eventWarningLimit = 0; // highlight event count
                $( "#k8EventTree", $dockerTree )
                    .css( "background-color", "yellow" );
            } else {
                console.debug( `Skipping folder refresh,  event count: ${ _last_event_count } , ${ loadResponse.kubernetes.eventCount }` );
            }
            let eventContent = spanForValue( loadResponse.kubernetes.eventCount, eventWarningLimit ) + " Events" + changeMessage;

            //            eventContent += 
            setComment( "k8EventTree", eventContent );
            _last_event_count = loadResponse.kubernetes.eventCount;



            let routeCount = spanForValue( loadResponse.kubernetes.serviceCount, 100 ) + " Services, "
                + spanForValue( loadResponse.kubernetes.ingressCount, 50 ) + " Ingresse(s)";

            setComment( "k8ServiceTree", routeCount );

            let jobCount = spanForValue( loadResponse.kubernetes.cronJobCount, 10 ) + " CronJob(s), "
                + spanForValue( loadResponse.kubernetes.jobCount, 50 ) + " job(s)";

            setComment( "k8JobTree", jobCount );


            setComment( "helm-commandsTree", spanForValue( loadResponse.kubernetes.helmReleaseCount, 100 ) + " releases" );
            setComment( "k8ConfigMapTree", spanForValue( loadResponse.kubernetes.configMapCount, 100 ) + " ConfigMaps" );
            setComment( "k8DeployTree", spanForValue( loadResponse.kubernetes.deploymentCount, 100 ) + " Deployments" );
            setComment( "k8EndpointTree", spanForValue( loadResponse.kubernetes.endpointCount, 100 ) + " Endpoints" );
            setComment( "k8ReplicaSetTree", spanForValue( loadResponse.kubernetes.replicaSetCount, 100 ) + " Replica Sets" );
            setComment( "k8StatefulSetTree", spanForValue( loadResponse.kubernetes.statefulSetCount, 100 ) + " Stateful Sets" );
            setComment( "k8DaemonSetTree", spanForValue( loadResponse.kubernetes.daemonSetCount, 100 ) + " Daemon Sets" );
            setComment( "k8VolumeClaimTree", spanForValue( loadResponse.kubernetes.volumeClaimCount, 100 ) + " Volume Claims" );

        } else {

            treeHidden = true; // trigger a refresh when resolved

            let message = "Not Available";

            let reason = utils.json( "kubernetes.error.reason", loadResponse );
            if ( reason ) {
                message = `${ message }: <span class="status-red">${ reason }</span>`
            }
            setComment( "k8ConfigurationTree", message );
            disable_folder( hostOperations.categories().kubernetesConfig );
            remove_folder( hostOperations.categories().kubernetesEvents );
            remove_folder( hostOperations.categories().kubernetesConfigMaps );
            remove_folder( hostOperations.categories().kubernetesPods );
            remove_folder( hostOperations.categories().kubernetesServices );
            remove_folder( hostOperations.categories().kubernetesJobs );
            remove_folder( hostOperations.categories().kubernetesDeployments );
            remove_folder( hostOperations.categories().kubernetesReplicaSets );
            remove_folder( hostOperations.categories().kubernetesEndpoints );
            remove_folder( hostOperations.categories().kubernetesVolumeClaims );

            remove_folder( hostOperations.categories().kubernetesStatefulSets );
            remove_folder( hostOperations.categories().kubernetesDaemonSets );
        }
    }

    function build_kubernetes_configuration( $configNodeLabel, version ) {

        let $configuration = $( "#kubernetes-namespace" );
        if ( $configNodeLabel.text() == "" ) {
            console.log( "build_kubernetes_configuration() - initializing title" );
            $configNodeLabel.empty();
            $configNodeLabel.append( $configuration );

            $kubernetesNameSpaceSelect.off().change( function () {

                console.log( "\n\n namespace selection updated: refreshing status" );
                _last_event_count = -1;
                $( "div.comment.k8-loading", $dockerTree ).text( "" );
                $( "div.comment.k8-loading", $dockerTree ).addClass( "csap-loading" );

                refresh_host_status();

            } );
        }
        $( "span", $configuration ).text( version ).addClass( "normal" );


        return $configuration;
    }

    function reload_kubernetes_folders() {

        if ( $( "#kubernetes-reload-checkbox", $dockerTree ).is( ":checked" ) ) {
            //console.error("reloading folders") ;
            reload_folder( hostOperations.categories().kubernetesEvents );
            reload_folder( hostOperations.categories().kubernetesPods );
            reload_folder( hostOperations.categories().kubernetesServices );
            reload_folder( hostOperations.categories().kubernetesJobs );
            reload_folder( hostOperations.categories().kubernetesDeployments );
            reload_folder( hostOperations.categories().kubernetesEndpoints );
            reload_folder( hostOperations.categories().kubernetesReplicaSets );
            reload_folder( hostOperations.categories().kubernetesVolumeClaims );

            reload_folder( hostOperations.categories().kubernetesStatefulSets );
            reload_folder( hostOperations.categories().kubernetesConfigMaps );
            reload_folder( hostOperations.categories().kubernetesDaemonSets );

            // adding in container filter support

            reload_folder( hostOperations.categories().dockerContainers );
        }
    }

    function refresh_host_status() {

        let intervalInSeconds = $( "#cpuIntervalId" ).val();
        if ( !intervalInSeconds ) {
            console.debug( `Warning: intervalInSeconds not set - defaulting to 10` );
            intervalInSeconds = 10;
        }
        console.debug( `refresh_host_status(): updates agent view every ${ intervalInSeconds } seconds` );
        let isAgentTabActive = $agentTabButton.hasClass( "active" );
        console.debug( `isAgentTabActive: ${ isAgentTabActive } ` );
        clearTimeout( _loadTimer );
        if ( !isAgentTabActive ) {
            console.log( `agent tab not active - aborting refreshes` );
            return;
        }

        let responsePromise = _net.httpGet(
            utils.getOsUrl() + "/cached/status", {
            "namespace": $kubernetesNameSpaceSelect.val()
        } );

        responsePromise

            .then( hostStatusReport => {
                $( "#cpuTimestamp" ).html( hostStatusReport[ "timeStamp" ] );
                buildTreeSummary( hostStatusReport );
            } )
            
            .catch( ( e ) => {
                console.warn( e );
            } ); 

        _loadTimer = setTimeout( function () {
            refresh_host_status()
        }, intervalInSeconds * 1000 );

        return responsePromise;
    }

    function showCpuQuotaPrompt() {

        let title = _currentContainerNode.containerName + " CPU Quota"


        let $cpuPeriod = $( "#promptCpuPeriod" );
        let $cpuQuota = $( "#promptCpuQuota" );

        let okFunction = function ( evt, value ) {

            utils.loading( "Updateding cpu quota" );
            restoreTemplate( $quotaDialog )
            alertify.notify( "Updating cpu: " + $( "#promptCpuPeriod" ).val() );
            let paramObject = {
                "name": _currentContainerNode.containerName,
                "periodMs": $cpuPeriod.val(),
                "quotaMs": $cpuQuota.val()
            };
            let commandUrl = _containerCommandUrl + "cpuQuota";

            $.post( commandUrl, paramObject )
                .done( function ( commandResults ) {
                    hostOperations.showResultsDialog( "cpuQuota", commandResults, commandUrl );
                    reload_folder( hostOperations.categories().dockerContainers );

                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    console.log( "Failed command", textStatus, jqXHR );
                    if ( jqXHR.status == 403 ) {
                        alertify.alert( "Permission Denied: " + jqXHR.status, "Contact your administrator to request permissions" );
                    } else {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                    }
                } );
        }
        let cancelFunction = function () {
            restoreTemplate( $quotaDialog )
        }

        _dialogs.showConfirmDialog(
            title,
            '<div id="cpuPrompt"></div>',
            okFunction,
            cancelFunction,
            `Modify Quota`,
            `Cancel`,
            `csap-info`
        ) ;

        $quotaDialog.appendTo( $( '#cpuPrompt' ) );
    }

    function restoreTemplate( $item ) {
        console.debug( "restoring", $item.attr( "id" ) );
        $item.appendTo( $( '#jsTemplates' ) );
    }

    function showContainerStopDialog() {

        let containerName = _currentContainerNode.containerName;

        let $stopDialog = $( "#stopContainerDialog" );
        let $killCheckbox = $( "#containerKill" );
        let $stopSeconds = $( "#containerStopSeconds" );


        let okFunction = function () {
            utils.loading( "Loading container info" );
            restoreTemplate( $stopDialog );
            let commandUrl = _containerCommandUrl + "stop";
            let paramObject = {
                "name": containerName,
                "kill": $killCheckbox.is( ':checked' ),
                "stopSeconds": $stopSeconds.val()
            };
            console.log( "hitting: ", commandUrl, paramObject );
            $.post( commandUrl, paramObject )
                .done( function ( commandResults ) {
                    hostOperations.showResultsDialog( "/container/stop", commandResults, commandUrl );
                    reload_folder( hostOperations.categories().dockerContainers );
                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    utils.loadingComplete();
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                } );
        }

        let cancelFunction = function () {
            alertify.error( 'Canceled' );
            restoreTemplate( $stopDialog );
        }


        _dialogs.showConfirmDialog(
            "Stop Container: " + containerName,
            '<div id="stopPrompt"></div>',
            okFunction,
            cancelFunction,
            `Stop Container`,
            `Cancel`,
            `csap-info`
        ) ;

        $stopDialog.appendTo( $( '#stopPrompt' ) );

    }

    function showImageRemoveDialog() {

        let imageName = _currentImageNode.imageName;
        let imageId = _currentImageNode.attributes.Id;
        let repoTags = JSON.stringify( _currentImageNode.attributes.RepoTags, null, "\t" );

        console.log( "showImageRemoveDialog: current image", _currentImageNode );

        let $removeDialog = $( "#removeImageDialog" );
        let $removeForce = $( "#imageRemoveForce" );

        let $imageSpan = $( "#imageRemoveId" );
        $imageSpan.text( imageId );

        let $tagsSpan = $( "#imageRemoveTags" );
        $tagsSpan.text( repoTags )


        let okFunction = function () {
            utils.loading( "removing image" )
            restoreTemplate( $removeDialog );
            let commandUrl = _dockerCommandUrl + "/image/remove";
            let paramObject = {
                name: imageName,
                id: imageId,
                "force": $removeForce.is( ':checked' )
            };
            console.log( "hitting: ", commandUrl, paramObject );
            $.post( commandUrl, paramObject )
                .done( function ( commandResults ) {
                    hostOperations.showResultsDialog( "/image/remove", commandResults, commandUrl );
                    reload_folder( hostOperations.categories().dockerImages );
                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    utils.loadingComplete();
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                } );
        }

        let cancelFunction = function () {
            alertify.error( 'Canceled' );
            restoreTemplate( $removeDialog );
        }
        _dialogs.showConfirmDialog(
            "Remove Image: " + imageName,
            '<div id="removePrompt"></div>',
            okFunction,
            cancelFunction,
            `Remove Image`,
            `Cancel`,
            `csap-info`
        ) ;

        //let $description = jQuery( '<div/>', { "id:", ""} );
        // alertify.confirm(
        //     "Remove Image Confirmation:",
        //     '<div id="removePrompt"></div>',
        //     okFunction,
        //     cancelFunction
        // );

        $removeDialog.appendTo( $( '#removePrompt' ) );

    }

    function showContainerRemoveDialog() {

        let containerName = _currentContainerNode.containerName;

        let $removeDialog = $( "#removeContainerDialog" );
        let $removeForce = $( "#containerRemoveForce" );
        let $removeVolumes = $( "#containerRemoveVolumes" );


        let okFunction = function () {
            utils.loading( "removing container" )
            restoreTemplate( $removeDialog );
            let commandUrl = _containerCommandUrl + "remove";
            let paramObject = {
                "name": containerName,
                "force": $removeForce.is( ':checked' ),
                "removeVolumes": $removeVolumes.is( ':checked' )
            };
            console.log( "hitting: ", commandUrl, paramObject );
            $.post( commandUrl, paramObject )
                .done( function ( commandResults ) {
                    hostOperations.showResultsDialog( "/container/remove", commandResults, commandUrl );
                    reload_folder( hostOperations.categories().dockerContainers );
                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    utils.loadingComplete();
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                } );
        }

        let cancelFunction = function () {
            alertify.error( 'Canceled' );
            restoreTemplate( $removeDialog );
        }


        _dialogs.showConfirmDialog(
            "Remove Container: " + containerName,
            '<div id="removePrompt"></div>',
            okFunction,
            cancelFunction,
            `Remove Container`,
            `Cancel`,
            `csap-info`
        ) ;

        $removeDialog.appendTo( $( '#removePrompt' ) );

    }




    function showCleanImagePrompt() {

        //
        // confirm dialog re-use: works because $cleanImageDialog is global
        //

        let okFunction = function () {
            utils.loading( "Running image clear" );
            let imageCleanUrl = _dockerCommandUrl + "/image/clean/"
                + $( "#clean-days" ).val()
                + "/" + $( "#clean-minutes" ).val();
            console.log( "hitting: ", imageCleanUrl );
            $.delete( imageCleanUrl )
                .done( function ( commandResults ) {
                    hostOperations.showResultsDialog( "Docker Image clean up", commandResults, imageCleanUrl );
                    reload_folder(
                        hostOperations.categories().dockerImages,
                        true );
                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                } );
        }

        let cancelFunction = function () {
            alertify.error( 'Canceled' );
        }
        //let $description = jQuery( '<div/>', { "id:", ""} );
        // let $cleanUpAlertify = alertify.confirm(
        //     "Docker Clean Up...",
        //     '<div id="image-clean-prompt"></div>',
        //     okFunction,
        //     cancelFunction
        // );


        _dialogs.showConfirmDialog(
            "Docker Clean Up...",
            '<div id="image-clean-prompt"></div>',
            okFunction,
            cancelFunction,
            `Clean Up Containers`,
            `Cancel`,
            `csap-info`
        ) ;

        $cleanImageDialog.appendTo( $( '#image-clean-prompt' ) );

    }



    function showPullImagePrompt() {

        let imageName = "docker.io/hello-world";

        if ( _currentImageNode != null ) {
            imageName = _currentImageNode.imageName;
        }
        ;

        $pullInput.val( imageName );


        let okFunction = function () {
            utils.loading( "Pulling image" )

            let pullName = $pullInput.val();
            restoreTemplate( $pullImageDialog );
            let commandUrl = _dockerCommandUrl + "/image/pull";
            let paramObject = {
                "name": pullName,
                repoUser: $("#pull-repo-user").val(),
                repoPass: $("#pull-repo-pass").val()
            };
            console.log( "hitting: ", commandUrl, paramObject );

            let monitorProgress = true;
            $.post( commandUrl, paramObject )
                .done( function ( commandResults ) {

                    utils.loadingComplete();
                    if ( commandResults.error ) {
                        console.log( "Got an error", commandResults );
                        monitorProgress = false;
                        hostOperations.showResultsDialog( "Pull Request: " + pullName, commandResults, commandUrl );
                    } else {

                        monitorProgress = commandResults.monitorProgress;

                        reload_folder(
                            hostOperations.categories().dockerImages,
                            true );
                    }

                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    utils.loadingComplete();
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                } );

            setTimeout( function () {

                if ( monitorProgress ) {

                    let onCompleteFunction = function () {
                        reload_folder( hostOperations.categories().dockerImages );
                    }

                    explorerProgress.image(
                        _dockerCommandUrl + "/image/pull/progress",
                        0,
                        onCompleteFunction );
                }
            }, 2000 );
        }

        let cancelFunction = function () {
            alertify.error( 'Canceled' );
            restoreTemplate( $pullImageDialog );
        }

        _dialogs.showConfirmDialog(
            "Download Image using docker pull",
            '<div id="pullPrompt"></div>',
            okFunction,
            cancelFunction,
            `Pull Image`,
            `Cancel`,
            `csap-info`
        ) ;

        $pullImageDialog.appendTo( $( '#pullPrompt' ) );

    }

    function service_proxy_dialog( $link ) {


        if ( !alertify.serviceProxy ) {

            let csapDialogFactory = _dialogs.dialog_factory_builder( {
                content: $serviceProxyDialog.show()[ 0 ],
                onresize: function () { },
                getWidth: function () {
                    return Math.round( $serviceProxyDialog.outerWidth( true ) ) + 20;
                },
                getHeight: function () {
                    return Math.round( $serviceProxyDialog.outerHeight( true ) ) + 50;
                }
            } );

            alertify.dialog( 'serviceProxy', csapDialogFactory, false, 'confirm' );


            $( "button.csap-window" ).click( function () {

                let url = $( "#proxy-url", $serviceProxyDialog ).val()
                    + $( "#proxy-path", $serviceProxyDialog ).val();
                utils.launch( url );
                _proxyAlertify.close();
            } )
            $( "button.csap-go" ).click( function () {
                utils.launchScript( { template: "kubernetes-proxy.sh" } );
            } )

        }

        console.log( "$link: ", $link )

        let name = $link.data( "name" );
        let port = $link.data( "port" );
        let path = $link.data( "path" );
        let api = $link.data( "api" );
        $( "#proxy-name", $serviceProxyDialog ).val( name );
        $( "#proxy-port", $serviceProxyDialog ).val( port );
        $( "#proxy-path", $serviceProxyDialog ).val( path );
        $( "#proxy-url", $serviceProxyDialog ).val( api );


        $serviceProxyTable.empty();
        $serviceProxyTable.html( `<tr>-<td></td><td>loading...</td></tr>` )

        _proxyAlertify = alertify.serviceProxy().show();
        $serviceProxyDialog.closest( ".ajs-dialog" ).addClass( "serviceProxy" );
        $.getJSON( utils.getOsExplorerUrl() + "/os/processes", {
            q: "test"

        }, function ( processReports ) {

            $serviceProxyTable.empty();


            for ( let processSummary of processReports ) {
                if ( processSummary.label == "kubectl" ) {
                    for ( let processReport of processSummary.attributes ) {
                        let $row = jQuery( '<tr/>', {} ).appendTo( $serviceProxyTable );
                        jQuery( '<td/>', {
                            text: processReport.pid
                        } ).appendTo( $row );

                        jQuery( '<td/>', {
                            text: processReport.parameters
                        } ).appendTo( $row );
                    }
                }
            }

            if ( $( "tr", $serviceProxyTable ).length == 0 ) {
                $serviceProxyTable.html( `<tr>-<td></td><td>no kubectl processes running: Api launches will fail</td></tr>` )
            }
        } );
    }

    function kubernetes_yaml_dialog() {

        if ( !alertify.yamlOps ) {

            let csapDialogFactory = _dialogs.dialog_factory_builder( {
                content: $yamlEditorDialog.show()[ 0 ],
                onresize: yamlEditorResize
            } );

            alertify.dialog( 'yamlOps', csapDialogFactory, false, 'confirm' );
        }



        _yamlAlertifyInstance = alertify.yamlOps().show();

    }

    function yamlEditorResize( dialogWidth, dialogHeight ) {

        setTimeout( function () {



            let maxWidth = dialogWidth - 10;
            $yamlText.css( "width", maxWidth );

            let maxHeight = dialogHeight
                - Math.round( $( "#yaml-editor-head" ).outerHeight( true ) )
                - 20;
            $yamlText.css( "height", maxHeight );

            console.log( `kubernetes_yaml_dialog() launched/resizing yaml editor` );
            _yamlEditor.resize();

        }, 500 );

    }

    function yamlEditorOperation( $button ) {

        //let commandUrl = utils.getOsExplorerUrl() + $( 'input[name=yaml-type-radio]:checked' ).val();
        let commandUrl = utils.getOsExplorerUrl() + "/kubernetes/cli";

        let paramObject = {
            yaml: _yamlEditor.getValue(),
            force: $( "#yaml-op-delete-force" ).is( ':checked' )
        };



        let failFunction = function ( jqXHR, textStatus, errorThrown ) {
            utils.loadingComplete();
            console.log( "yamlEditorOperation() request failure", jqXHR, textStatus, errorThrown )
            alertify.alert( "Failed Operation: Status Code: " + jqXHR.status,
                JSON.stringify( jqXHR.responseJSON, "\n", "\t" ) );
        }

        let description = $button.text();
        let httpMethod = "POST";

        if ( description.includes( "Update" ) ) {
            httpMethod = "PUT";

        } else if ( description.includes( "Delete" ) ) {
            //commandUrl += "Delete";
            httpMethod = "DELETE"; // Deletes struggle with parameters and payloads
            // httpMethod = "POST";
        }

        let successFunction = function ( commandResults ) {
            utils.loadingComplete();
            hostOperations.showResultsDialog( description + " Results: ", commandResults );

            reload_kubernetes_folders();
            reload_folder(
                hostOperations.categories().kubernetesDeployments,
                true );
        }

        let requestOptions = {
            method: httpMethod,
            data: paramObject,
            success: successFunction,
            error: failFunction
        };

        _yamlAlertifyInstance.close();

        utils.loading( "invoking yaml executor" );

        console.log( `yamlEditorOperation() url: ${ commandUrl }` );
        $.ajax( commandUrl, requestOptions );
    }



    function addApiPathButtons( fancyTreeNode ) {
        //let 

        let namespace = jsonForms.getValue( "data.attributes.metadata.namespace", fancyTreeNode );
        let name = jsonForms.getValue( "data.attributes.metadata.name", fancyTreeNode );

        let apiPath = jsonForms.getValue( "data.attributes.apiPath", fancyTreeNode );
        if ( apiPath == undefined ) {
            console.log( `addApiPathButtons: legacy check for kubernetes pre `, fancyTreeNode );
            apiPath = jsonForms.getValue( "data.attributes.metadata.selfLink", fancyTreeNode );
        }


        if ( apiPath == undefined ) {
            let csapLink = jsonForms.getValue( "data.attributes.csapLink", fancyTreeNode );
            if ( jsonForms.getValue( "data.attributes.namespaced", fancyTreeNode )
                && ( $kubernetesNameSpaceSelect.val() != "all" ) ) {
                csapLink += "/" + $kubernetesNameSpaceSelect.val();
            }
            apiPath = csapLink;
        }

        if ( apiPath != undefined ) {
            console.log( `apiPath: ${ apiPath }` );

            $( ".update-by-path" ).remove();

            let $yamlButton = jQuery( '<button/>', {
                class: "csap-icon csap-edit tree update-by-path",
                title: "View/Update/Delete specification"
            } )
                .css( "margin-left", "3em" )
                .click( function () {
                    showKubernetesInfo( "specification", apiPath );
                } )


            $( "span.fancytree-active .fancytree-title" ).append( $yamlButton );



            let $describeButton = jQuery( '<button/>', {
                class: "csap-icon csap-info tree update-by-path",
                title: "View information (kubectl describe)"
            } )
                .css( "margin-left", "0" )
                .css( "margin-top", "1px" )
                .click( function () {
                    showDescribe( "describe", apiPath );
                } )


            $( "span.fancytree-active .fancytree-title" ).append( $describeButton );

            if ( apiPath.endsWith( "/deployments/" + name )
                || apiPath.endsWith( "/statefulsets/" + name )
                || apiPath.endsWith( "/daemonsets/" + name )
                || apiPath.endsWith( "/namespaces/" + name ) ) {

                let $restartButton = jQuery( '<button/>', {
                    class: "csap-icon csap-recycle tree update-by-path",
                    title: "restart (kubectl rollout restart )"
                } )
                    .css( "margin-left", "0" )
                    .css( "margin-top", "1px" )
                    .click( function () {

                        let type = `deployments`;

                        if ( apiPath.endsWith( "/statefulsets/" + name ) ) {
                            type = `statefulsets`;
                        } else if ( apiPath.endsWith( "/daemonsets/" + name ) ) {
                            type = `daemonsets`;
                        } else if ( apiPath.endsWith( "/namespaces/" + name ) ) {
                            type = `namespaces`;
                        }

                        hostOperations.restartCli( type, name, namespace );
                    } )


                $( "span.fancytree-active .fancytree-title" ).append( $restartButton );
            }

            if ( apiPath.endsWith( "/deployments/" + name )
                || apiPath.endsWith( "/statefulsets/" + name ) ) {

                let $pauseButton = jQuery( '<button/>', {
                    class: "csap-icon csap-pause tree update-by-path",
                    title: "pause (kubectl scale --replicas=0 )"
                } )
                    .css( "margin-left", "0" )
                    .css( "margin-top", "1px" )
                    .click( function () {

                        let type = `deployments`;

                        if ( apiPath.endsWith( "/statefulsets/" + name ) ) {
                            type = `statefulsets`;
                        }

                        hostOperations.pauseCli( type, name, namespace );
                    } )


                $( "span.fancytree-active .fancytree-title" ).append( $pauseButton );
            }

        } else {
            console.debug( `did not find apiPath attribute - skipping` );
        }
    }

    function showDescribe( type, resourcePath ) {
        let commandUrl = utils.getOsExplorerUrl() + "/kubernetes/cli/info/" + type + "?resourcePath=" + resourcePath;
        let $link = jQuery( '<label/>', {
            "data-targeturl": commandUrl
        } );
        hostOperations.commandRunner( $link );
    }

    function showKubernetesInfo( type, resourcePath ) {
        let commandUrl = utils.getOsExplorerUrl() + "/kubernetes/cli/info/" + type;


        kubernetes_yaml_dialog();
        _yamlEditor.setValue( "load specification for " + resourcePath );
        $yamlFolderCheck.trigger( "change" );

        $.get( commandUrl, { resourcePath: resourcePath } )
            .done( function ( commandResults ) {

                $( "#last-loaded-yaml" ).text( resourcePath ).attr( "title", resourcePath );

                let yamlText = checkYamlFormatting( commandResults[ "response-yaml" ] );


                _yamlEditor.setValue(
                    yamlText,
                    -1 );
                $yamlFolderCheck.trigger( "change" );
                //_yamlEditor.getSession().foldAll( 2 ) ;
            } )
            .fail( function ( jqXHR, textStatus, errorThrown ) {
                alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
            } );
    }

    function checkYamlFormatting( loadedContent ) {

        let formatedContent = loadedContent;

        if ( $yamlSpacing.is( ":checked" ) ) {

            try {
                let specAsYaml = jsYaml.loadAll( loadedContent );
                let specAsJsonText = JSON.stringify( specAsYaml );
                let specJsonDocuments = JSON.parse( specAsJsonText );
                console.log( `yaml formating enabled, specAsJson: `, specJsonDocuments );

                let deletedFields = false;
                for ( let specJsonDoc of specJsonDocuments ) {

                    let managedFields = utils.json( "metadata.managedFields", specJsonDoc );
                    if ( managedFields ) {
                        console.log( "removing managed fields" );
                        let metadata = specJsonDoc[ "metadata" ];
                        delete metadata.managedFields;
                        deletedFields = true;

                    }
                }
                if ( deletedFields ) {
                    formatedContent = jsYaml.dump( specJsonDocuments );
                }


                let spaceTopLevel = false;

                formatedContent = utils.yamlSpaces(
                    formatedContent,
                    [ "metadata", "spec", "status" ],
                    spaceTopLevel );
            } catch ( e ) {
                alertify.csapWarning( "Failed to perform yaml spacing" );
                console.error( "Failed to perform yaml spacing", e );
            }
        }

        return formatedContent;
    }


    function kubernetes_perform_command( $button ) {

        let resourceType = _current_ft_node.type;
        let resourceName = _current_ft_node.data.originalTitle;
        let operation = $button.attr( "id" );

        console.log( `kubernetes_perform_command():  ${ operation }  on  ${ resourceType }`, _current_ft_node );

        if ( resourceType == hostOperations.categories().kubernetesServices
            && _current_ft_node.data.attributes
            && _current_ft_node.data.attributes.spec
            && _current_ft_node.data.attributes.spec.rules ) {
            resourceType = "kubernetes/ingresses";
            console.log( "kubernetes_perform_command: updated resourceType", resourceType );
        }

        let commandUrl = utils.getOsExplorerUrl() + "/" + resourceType;

        switch ( operation ) {

            case "pod-yaml":
            case "persistentvolumeclaim-yaml":
            case "statefulset-yaml":
            case "daemonset-yaml":
            case "replicaset-yaml":
            case "service-yaml":
            case "deployment-yaml":


                //let namespace = $kubernetesNameSpaceSelect.val() ;
                // let namespace = _current_ft_node.data.attributes.metadata.namespace ;
                let namespace = jsonForms.getValue( "data.attributes.metadata.namespace", _current_ft_node );
                let resourceType = operation.split( "-" )[ 0 ];
                let rules = jsonForms.getValue( "data.attributes.spec.rules", _current_ft_node );
                if ( ( resourceType === "service" ) && ( typeof rules === "object" ) ) {
                    console.log( `Detected ingress - updating type` );
                    resourceType = "ingress";
                }
                commandUrl = utils.getOsExplorerUrl() + "/kubernetes/cli"
                    + "/" + namespace
                    + "/" + resourceType + "/" + resourceName;


                console.log( `kubernetes_perform_command() commandUrl:  ${ commandUrl } ` );

                $( this ).data( "targeturl", commandUrl );

                // hostOperations.commandRunner( $( this ) ) ;

                $.get( commandUrl )
                    .done( function ( commandResults ) {
                        //showResultsDialog( "Command Output", commandResults ) ;
                        $( "#last-loaded-yaml" ).text( resourceName ).attr( "title", resourceName );
                        kubernetes_yaml_dialog();
                        _yamlEditor.setValue(
                            commandResults[ "response-yaml" ],
                            -1 );
                        $yamlFolderCheck.trigger( "change" );
                        //_yamlEditor.getSession().foldAll( 2 ) ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                    } );

                break;


            case "deployment-remove":
            case "statefulset-remove":
            case "volume-claim-remove":
            case "daemonset-remove":
            case "replicaset-remove":
            case "service-k8-delete":
            case "pod-delete":
                {
                    let podMetaData = _current_ft_node.data.attributes.metadata;
                    commandUrl += "/" + podMetaData.namespace
                        + "/" + podMetaData.name;


                    if ( operation == "deployment-remove" ) {
                        commandUrl += "/true"  // deleteService
                            + "/true"; // deleteIngress
                    }



                    hostOperations.show_k8s_delete_confirmation(
                        commandUrl,
                        reload_kubernetes_folders
                    );
                }
                break;

            case "pod-logs":
                {

                    console.log( "Showing default logs for container" );
                    let podAttributes = _current_ft_node.data.attributes;
                    let podMetaData = podAttributes.metadata;
                    let podSpec = podAttributes.spec;

                    let relatedPods = new Array();
                    relatedPods.push( podMetaData.name );
                    let owner = jsonForms.getValue( "ownerReferences.0.name", podMetaData );
                    if ( owner ) {
                        relatedPods.push( ..._podsByOwner[ owner ] );
                    }

                    let containerNames = new Array();
                    if ( podSpec.containers ) {
                        for ( let container of podSpec.containers ) {
                            containerNames.push( container.name );
                        }
                    }

                    if ( podSpec.initContainers ) {

                        for ( let container of podSpec.initContainers ) {
                            containerNames.push( container.name );
                        }
                    }

                    podLogs.configure(
                        podMetaData.name,
                        podMetaData.namespace,
                        containerNames,
                        podAttributes.hostname,
                        relatedPods );


                    utils.launchMenu( "agent-tab,explorer-pod-logs" );
                }
                break;


            case "pod-describe":
                {

                    console.log( "Running pod describe" );
                    let podAttributes = _current_ft_node.data.attributes;
                    let podMetaData = podAttributes.metadata;
                    let podSpec = podAttributes.spec;
                    let $link = jQuery( '<label/>', {
                        "data-targeturl": utils.getOsExplorerUrl() + "/kubernetes/pod/describe"
                            + "/" + podMetaData.namespace + "/" + podMetaData.name
                    } );
                    hostOperations.commandRunner( $link );
                }
                break;


            case "deployment-describe":
                {

                    console.log( "Running deploy describe" );
                    let podAttributes = _current_ft_node.data.attributes;
                    let podMetaData = podAttributes.metadata;
                    let podSpec = podAttributes.spec;
                    let $dlink = jQuery( '<label/>', {
                        "data-targeturl": utils.getOsExplorerUrl() + "/kubernetes/deployment/describe"
                            + "/" + podMetaData.namespace + "/" + podMetaData.name
                    } );
                    hostOperations.commandRunner( $dlink );
                }
                break;

            case "pod-commands":

                utils.launchScript( {
                    serviceName: `${ resourceName }`,
                    fromFolder: `__root__/opt/csap`,
                    template: `kubernetes-pods.sh`
                } );

                break;

            default:
                alertify.alert( "Operation not implemented: " + operation );
                console.warn( "kubernetesOperations(): operation not implemented:", operation )
        }
    }

    function adminOperation( type, nodeData, operation, skipPrompt ) {

        console.log( "type:", type, " operation: ", operation, " nodeData:", nodeData );



        let name = null;
        let id = null;
        let targetItem = "noTarget";
        let templateName = "docker-image.sh";

        if ( nodeData != null ) {
            name = nodeData.containerName;
            if ( nodeData.imageName ) {
                name = nodeData.imageName;
                id = nodeData.attributes.Id;
                targetItem = nodeData.imageName;
            }

            if ( nodeData.containerName ) {
                templateName = "docker-container.sh"
                // strip leading slash for commands
                targetItem = nodeData.containerName.substring( 1 );
            }

            if ( nodeData.linuxServiceName ) {

                if ( operation == "logs" ) {
                    let parameters = {
                        fileName: "__journal__/" + nodeData.linuxServiceName,
                        serviceName: null,
                        hostName: uiSettings.hostName
                    };
                    postAndRemove( "_blank",
                        uiSettings.baseUrl + "file/FileMonitor",
                        parameters );
                    return;
                }
            }
        }

        let paramObject = {
            "name": name,
            "id": id
        };

        let commandUrl = _dockerCommandUrl + type + operation;
        console.log( "commandUrl:", commandUrl, " paramObject:", paramObject )

        if ( !skipPrompt && ( operation == "remove" || operation == "stop" || operation == "/system/prune" ) ) {
            if ( type == CONTAINER_TYPE && operation == "remove" ) {
                showContainerRemoveDialog();
            } else if ( type == CONTAINER_TYPE && operation == "stop" ) {
                showContainerStopDialog();
            } else if ( type == IMAGE_TYPE && operation == "remove" ) {
                showImageRemoveDialog();
            } else {

                showConfirmPrompt( type, nodeData, operation, targetItem );
            }
            return;
        }

        switch ( operation ) {

            case "create":
                $( "#containerCreate" ).trigger( "click" );
                //showCreateContainerDialog()
                break;


            case "pull":
                showPullImagePrompt()
                break;

            case "cpuQuota":
                showCpuQuotaPrompt()
                break;

            case "batch":


                utils.launchScript( {
                    serviceName: `${ targetItem }`,
                    template: `${ templateName }`
                } );
                //                commandUrl = commandScreen + '?command=script&'
                //                        + 'template=' + templateName + '&'
                //                        + 'serviceName=' + targetItem + '&' ;
                //
                //                openWindowSafely( commandUrl, "_blank" ) ;
                break;

            case "fileBrowser":

                utils.launchFiles( {
                    locationAndClear: `__docker__${ nodeData.containerName }`
                } );

                //                commandUrl = `${ fileManagerUrl }?quickview=Container: ${ nodeData.containerName }`
                //                        + `&fromFolder=__docker__${nodeData.containerName}&`
                //
                //                openWindowSafely( commandUrl, "_blank" ) ;
                break;

            case "info":
            case "processTree":
            case "sockets":

                $.get( commandUrl, paramObject )
                    .done( function ( commandResults ) {
                        hostOperations.showResultsDialog( operation, commandResults, commandUrl );
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                    } );
                break;

            case "tail":
                // explorerProgress.docker( _currentContainerNode.containerName, 100 ) ;

                podLogs.configure( _currentContainerNode.containerName, null, _dockerContainerNames );
                utils.launchMenu( "agent-tab,explorer-pod-logs" );

                break;


            default:

                utils.loading( `Running command ${ operation }` )
                $.post( commandUrl, paramObject )
                    .done( function ( commandResults ) {
                        hostOperations.showResultsDialog( operation, commandResults, commandUrl );

                        if ( type == IMAGE_TYPE && operation != "create" ) {
                            reload_folder( hostOperations.categories().dockerImages );

                        } else if ( type == CONTAINER_TYPE && operation != "create" ) {
                            reload_folder( hostOperations.categories().dockerContainers );

                        }

                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {

                        utils.loadingComplete();
                        console.log( "Failed command", textStatus, jqXHR );
                        if ( jqXHR.status == 403 ) {
                            alertify.alert( "Permission Denied: " + jqXHR.status, "Contact your administrator to request permissions" );
                        } else {
                            alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                        }
                    } );
                break;

        }


    }


    function showConfirmPrompt( type, selectedNode, operation, desc ) {

        let title = "Confirmation Required"

        let $description = jQuery( '<div/>', {
            text: "Proceed with " + operation + " of: " + desc
        } );

        let okFunction = function ( evt, value ) {
            adminOperation(
                type, selectedNode, operation, true );
        }
        let cancelFunction = function () {
            alertify.error( 'Canceled' );
        }


        _dialogs.showConfirmDialog(
            title,
            $description.html(),
            okFunction,
            cancelFunction,
            `${operation}`,
            `Cancel`,
            `csap-info`
        ) ;

        // alertify.confirm(
        //     title,
        //     $description.html(),
        //     okFunction,
        //     cancelFunction
        // );
    }

    function showAddDiskPrompt( dsPath, uri ) {

        console.log( `showAddDiskPrompt() dsPath: ${ dsPath } `, _current_ft_node );

        let nameToFolderPaths = dsPath.split( ":", 2 );

        let $addDiskDialog = $( "#vsphere-add-disk" );

        let $allInputs = $( "label.line", $addDiskDialog );

        let $datastoreName = $( "#vsphere-datastore-name", $addDiskDialog );
        $datastoreName.val( nameToFolderPaths[ 0 ] );

        let $datastoreFolder = $( "#vsphere-datastore-folder", $addDiskDialog );
        $datastoreFolder.val( nameToFolderPaths[ 1 ] );

        let $diskPath = $( "#vsphere-disk-path" );
        let diskPathSelected = jsonForms.getValue( "data.attributes.Path", _current_ft_node );
        if ( diskPathSelected ) {
            let folder = "/";
            let parentFolderPath = jsonForms.getValue( "data.attributes.path", _current_ft_node.getParent() );
            if ( parentFolderPath ) {
                let parentPaths = parentFolderPath.split( ":", 2 );
                if ( parentPaths.length = 2 ) {
                    folder = parentPaths[ 1 ];
                }
            }
            $diskPath.val( folder.substr( 1 ) + diskPathSelected );
        }

        let $operationChoices = $( "input:radio", $addDiskDialog );

        $operationChoices.off().change( function () {

            if ( !$( this ).prop( "checked" ) ) {
                return;
            }

            let operation = $( this ).val();
            console.log( `showAddDiskPrompt() operations: ${ operation } ` );
            $allInputs.hide();
            $diskPath.parent().show();


            switch ( operation ) {

                case 'add':
                    $( "#vsphere-disk-type" ).parent().show();
                    $( "#vsphere-disk-size" ).parent().show();
                    $datastoreName.parent().show();
                    break;

                case 'delete':
                    $datastoreName.parent().show();
                    break;

            }
        } );


        $operationChoices.val( [ 'find' ] );
        $operationChoices.trigger( "change" );


        let okFunction = function () {
            utils.loading( `Waiting for response` )
            restoreTemplate( $addDiskDialog );
            let commandUrl = uri;
            let paramObject = {
                "datastoreName": $datastoreName.val(),
                "diskPath": $diskPath.val(),
                "diskSize": $( "#vsphere-disk-size" ).val(),
                "diskType": $( "#vsphere-disk-type" ).val(),
                "operation": $( "input[name='vsphere-disk-radio']:checked", $addDiskDialog ).val()
            };
            console.log( "hitting: ", commandUrl, paramObject );
            $.post( commandUrl, paramObject )
                .done( function ( commandResults ) {
                    utils.loadingComplete();

                    hostOperations.showResultsDialog( "Vsphere Datastore Results: " + $datastoreName.val(),
                        commandResults,
                        commandUrl );

                    //                        reload_folder( hostOps.categories().vsphereVms );
                    reload_folder( hostOperations.categories().vsphereDatastores );

                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    utils.loadingComplete();
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
                } );
        }

        let cancelFunction = function () {
            alertify.error( 'Canceled' );
            restoreTemplate( $addDiskDialog );
        }
        //let $description = jQuery( '<div/>', { "id:", ""} );
        // alertify.confirm(
        //     "Vsphere Disk Operations",
        //     '<div id="add-disk-holder"></div>',
        //     okFunction,
        //     cancelFunction
        // );

        _dialogs.showConfirmDialog(
            "Vsphere Disk Operations",
            '<div id="add-disk-holder"></div>',
            okFunction,
            cancelFunction,
            `Add`,
            `Cancel`,
            `csap-info`
        ) ;

        $addDiskDialog.appendTo( $( '#add-disk-holder' ) );

    }

}
