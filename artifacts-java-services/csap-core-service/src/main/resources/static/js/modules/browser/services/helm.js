// define( [ "services/instances", "browser/utils", "ace/ace", "ace/ext-modelist" ], function ( instances, utils, aceEditor, aceModeListLoader ) {

//     console.log( "Module loaded" ) ;




import _dom from "../../utils/dom-utils.js";



import utils from "../utils.js"


import instances from "./instances.js"
import { aceEditor, jsYaml } from "../../libs/file-editor.js"

const svcHelm = service_helm();

export default svcHelm


function service_helm() {

    _dom.logHead( "Module loaded 33" );

    const $readmePanel = utils.findContent( "#services-tab-readme" );
    const $helmReadmeEditor = $( "#readme-viewer", $readmePanel );
    const $readmeSource = $( "#readme-source", $readmePanel );

    const $valuesPanel = utils.findContent( "#services-tab-helm" );
    const $helmValuesEditor = $( "#helm-values-editor", $valuesPanel );
    const $helmShowAll = $( "#helm-show-all", $valuesPanel );
    const $helmFold = $( "#helm-fold", $valuesPanel );

    let _helmValuesEditor;

    let prismLoadedPromise;

    return {

        readme: function ( $menuContent, forceHostRefresh, menuPath ) {

            console.log( `details for ${ menuPath }` );

            initialize();

            return loadReadme( menuPath );

        },

        values: function ( $menuContent, forceHostRefresh, menuPath ) {

            console.log( `details for ${ menuPath }` );

            initialize();

            return loadHelmInfo( menuPath );

        }

    };

    function initialize() {


        if ( !_helmValuesEditor ) {

            _dom.loadCss( `${ JS_URL }/prism-1.28/prism.css` );

            async function loadModules( targetFm ) {
                await import( `${ JS_URL }/prism-1.28/prism.js` );
            }

            prismLoadedPromise = loadModules();

            let editorId = $helmValuesEditor.attr( "id" );

            console.log( ` Building: _helmValuesEditor editor: ${ editorId } ` );
            _helmValuesEditor = aceEditor.edit( editorId );
            _helmValuesEditor.setOptions( utils.getAceDefaults( "ace/mode/yaml", true ) );
            //            _helmValuesEditor.setTheme( "ace/theme/merbivore_soft" ) ;
            _helmValuesEditor.setTheme( "ace/theme/chrome" );

            let $aceWrapCheckbox = $( '#helm-wrap', $valuesPanel );
            $aceWrapCheckbox.change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    _helmValuesEditor.session.setUseWrapMode( true );

                } else {
                    _helmValuesEditor.session.setUseWrapMode( false );
                }
            } );

            $helmFold.change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    _helmValuesEditor.getSession().foldAll( 1 );
                } else {
                    //_yamlEditor.getSession().unfoldAll( 2 ) ;
                    _helmValuesEditor.getSession().unfold();
                }
            } );

            $helmShowAll.change( function () {
                loadHelmInfo( "helm" );
            } )

        }

    }

    function loadHelmInfo( menuPath ) {

        let $contentLoaded = new $.Deferred();
        utils.loading( `Loading` );
        let selectedService = instances.getSelectedService();




        if ( selectedService === "not-initialized-yet" ) {
            utils.launchMenu( "services-tab,status" );
            return;
        }


        let parameters = {
            chart: selectedService,
            project: utils.getActiveProject(),
            command: menuPath,
            showAll: $helmShowAll.is( ":checked" )
        };


        let helmUrl = `${ APP_BROWSER_URL }/helm/info` ;

        let latestReport = instances.getLatestReport();
   

        console.log( `loading: ${ helmUrl }`, parameters );

        $.getJSON( helmUrl, parameters )

            .done( function ( itemReport ) {
                // console.log( ` content: `, lifeDialogHtml ) ;

                utils.loadingComplete();
                //alertify.csapInfo( eventDetails["response-yaml"]  ) ;
                //addPodDetails( podDetails, $container ) ;

                prismLoadedPromise.then( function () {
                    updateEditor( itemReport, menuPath );
                } )


            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                utils.loadingComplete();
                handleConnectionError( `Getting chart details ${ menuPath }`, errorThrown );
            } );






        return $contentLoaded;
    }



    function loadReadme( menuPath, mdFileName ) {

        let $contentLoaded = new $.Deferred();
        utils.loading( `Loading` );
        let selectedService = instances.getSelectedService();

        if ( selectedService === "not-initialized-yet" ) {
            utils.launchMenu( "services-tab,status" );
            return;
        }

        let latestReport = instances.getLatestReport();
        let parameters = {
            serviceName: selectedService,
            readmeName: mdFileName
        }

        let readmeUrl = `${ APP_BROWSER_URL }/readme`;
        console.log( `loading: ${ readmeUrl }`, parameters );

        $.getJSON( readmeUrl, parameters )

            .done( function ( itemReport ) {
                // console.log( ` content: `, lifeDialogHtml ) ;

                utils.loadingComplete();
                //alertify.csapInfo( eventDetails["response-yaml"]  ) ;
                //addPodDetails( podDetails, $container ) ;

                prismLoadedPromise.then( function () {
                    updateEditor( itemReport, menuPath );
                } )


            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                utils.loadingComplete();
                handleConnectionError( `Getting chart details ${ menuPath }`, errorThrown );
            } );






        return $contentLoaded;


    }

    function updateEditor( itemReport, menuPath ) {

        if ( menuPath === "helm"
            && itemReport[ "response-yaml" ] ) {

            _helmValuesEditor.getSession().setValue( itemReport[ "response-yaml" ] );

        } else {

            //console.log( `itemReport: `, itemReport) ;


            let serviceName = instances.getSelectedService();
            let serviceType = "process-monitor";

            let $readMeContent = jQuery( '<div/>', {} );

            let $noContent = jQuery( '<div/>', {
                class: `quote`,
                html: `No README.md found; it may be added to the service resource folder if desired.<br><br>`
            } ).appendTo( $readMeContent );


            let latestReport = instances.getLatestReport();
            if ( latestReport ) {
                console.log( `latestReport`, latestReport.dockerSettings) ;

                if ( latestReport.csapApi ) {
                    serviceType = "csap-api"
                } else if ( latestReport.filesOnly ) {
                    serviceType = "csap-package"
                } else if ( latestReport.springboot ) {
                    serviceType = "spring-boot"
                } else if ( latestReport.kubernetes ) {
                    serviceType = "kubernetes"
                } else if ( latestReport.dockerSettings  ) {
                    serviceType = "docker-container"
                }
            }



            if ( itemReport[ "response-html" ] ) {
                $readMeContent.html( itemReport[ "response-html" ] );


            } else if ( serviceType == "process-monitor" ) {
                $noContent.append( `Process monitors are typically used to collect OS Resource consumption, and 
                configured with application and property folder shortcuts for quick access to configuration.`);
            }


            let mdFileNames = itemReport[ "mdFiles" ];

            let $relatedFiles = jQuery( '<span/>', {} );
            if ( mdFileNames &&  mdFileNames.length > 1 ) {

                let $fileSelect = jQuery( '<select/>', { class: "readme-file-name"} ).appendTo( $relatedFiles );
                $fileSelect.css("font-size", "12px").css("align-self", "end");
                jQuery( '<option/>', { text: `related files...` , value: "none"} ).appendTo( $fileSelect );
                for ( let mdFile of mdFileNames) {
                    jQuery( '<option/>', { text: `${ mdFile }` } ).appendTo( $fileSelect );
                }
            }

            let $codeContainer = jQuery( '<div/>', {} );
            jQuery( '<h1/>', { text: `Csap Instance Report:` } ).appendTo( $codeContainer );

            let $codePre = jQuery( '<pre/>', {} ).appendTo( $codeContainer );
            let $code = jQuery( '<code/>', {
                class: "language-json",
                text: JSON.stringify( latestReport, "\n", "\t" )
            } ).appendTo( $codePre );


            $helmReadmeEditor.html(

                `<h1 class=flex-header><span>Service: ${ serviceName }</span>${  $relatedFiles.html() } <span>runtime: ${ serviceType }</span></h1>`
                + $readMeContent.html()
                + $codeContainer.html() );

            // bind readme selector
            $("select", $helmReadmeEditor).change( function() {
                
                loadReadme( menuPath, $(this).val() ) ;

            }) ;

            //
            // Prism formatting: support sh, yaml, java https://prismjs.com/#basic-usage
            //

            Prism.highlightAll();

            if ( itemReport.source ) {
                $readmeSource.empty();
                let label = itemReport.source;
                if ( label.length > 20 ) {
                    label = label.substring( 0, 20 ) + "...";
                }
                if ( itemReport.source.startsWith( "http" ) ) {
                    jQuery( '<a/>', {
                        href: itemReport.source,
                        title: itemReport.source,
                        target: "_blank",
                        text: label,
                        class: "csap-link-icon csap-window"
                    } ).appendTo( $readmeSource );
                } else {
                    $readmeSource.text( itemReport.source );
                }
            }

        }


        //$( ".code-fold", $container ).trigger( "change" ) ;


    }


}

