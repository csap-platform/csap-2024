
import "../libs/csap-modules.js";

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";


_dom.onReady( function () {

    let appScope = new Services_Report();
    appScope.initialize();

} );

function Services_Report() {


    _dom.logHead( "Main module" );


    let $table = $( "#servicesTable" );

    let $tableHeadRow = $( "thead tr", $table );
    let $tableFootRow = $( "tfoot tr", $table );

    let $tableBody = $( "tbody", $table );

    let projName = getParameterByName( "projName" );

    this.initialize = function () {
        console.log( "Init in instance" );

        _dialogs.loading( "building service report across all environments" );
        loadInstanceInfo();

    };

    let instanceMap;
    function loadInstanceInfo() {
        let r = $.Deferred();

        $( "#page-info", "body>header" ).html( `Service Templates: ${ projName }` )
        let appId = getParameterByName( "appId" );


        $.getJSON( baseUrl + "api/report/package-summary", {
            "projectName": projName,
            "appId": appId
        } ).done( function ( loadJson ) {

            _dialogs.loadingComplete() ;
            instanceInfoSuccess( loadJson );
        } );

        setTimeout( function () {
            console.log( 'loading health info  done' );
            r.resolve();
        }, 500 );

        return r;
    }

    function instanceInfoSuccess( dataJson ) {

        $tableBody.empty();
        instanceMap = new Object();

        let numLifecycles = 0;
        for ( let lifecycle in dataJson ) {
            numLifecycles++;
            //			let $lifeColumn = jQuery( '<td/>', { text: $( this ).data( "name" ) } );
            let $lifeColumn = jQuery( '<th/>', { text: lifecycle } );

            $tableHeadRow.append( $lifeColumn );

            let services = dataJson[ lifecycle ].instances.instanceCount;
            let projectUrl = dataJson[ lifecycle ].projectUrl;
            console.log( services )
            for ( let i = 0; i < services.length; i++ ) {
                let serviceSummary = services[ i ];
                let serviceName = serviceSummary.serviceName;
                let serviceClass = serviceName + "Row";
                let $serviceRow = $( "." + serviceClass );
                if ( $serviceRow.length == 0 ) {
                    $serviceRow = jQuery( '<tr/>', { class: serviceClass } );
                    $tableBody.append( $serviceRow );
                    let $nameColumn = jQuery( '<td/>', { text: serviceName } );
                    $serviceRow.append( $nameColumn );
                }

                // add placeholders for services not found in previous lifecycles
                for ( let numMissing = $serviceRow.children().length; numMissing < numLifecycles; numMissing++ ) {

                    let $countColumn = jQuery( '<td/>', { text: 0, class: "num" } );
                    $serviceRow.append( $countColumn );
                }

                let $projectLink = serviceSummary.count;
                if ( projectUrl != null ) {
                    $projectLink = jQuery( '<a/>', {
                        href: projectUrl + "/csap-admin/find-service/" + projName + "/" + serviceName,
                        title: "Click to open service portal",
                        text: serviceSummary.count,
                        target: "_blank",
                        class: "simple"
                    } );
                }


                let $countColumn = jQuery( '<td/>', { html: $projectLink, class: "num" } );
                $serviceRow.append( $countColumn );

            }

            let $sumColumn = jQuery( '<td/>', { 'data-math': "col-sum", class: "num" } );
            $tableFootRow.append( $sumColumn );
            //updateTable(instances,"instanceDevTable","instanceDevBody",lifecycle);

        }


        // Add row total
        $tableHeadRow.append( jQuery( '<th/>', { text: "All Lifecycles" } ) );
        $( "tr", $tableBody ).each( function ( index ) {

            let $serviceRow = $( this );
            for ( let numMissing = $serviceRow.children().length - 1; numMissing < numLifecycles; numMissing++ ) {

                let $countColumn = jQuery( '<td/>', { text: 0, class: "num" } );
                $serviceRow.append( $countColumn );
            }
            $serviceRow.append(
                jQuery( '<td/>', { 'data-math': "row-sum", class: "num" } )
            );
        } );
        let $sumColumn = jQuery( '<td/>', { 'data-math': "col-sum", class: "num" } );
        $tableFootRow.append( $sumColumn );

        $table.tablesorter( {
            sortList: [ [ 0, 0 ] ],
            theme: 'csapSummary',
            widgets: [ 'math' ],
            widgetOptions: {
                math_mask: '#,###,##0.',
                math_data: 'math'
            }
        } );



    }

    function getParameterByName( name ) {
        name = name.replace( /[\[]/, "\\\[" ).replace( /[\]]/, "\\\]" );
        let regexS = "[\\?&]" + name + "=([^&#]*)", regex = new RegExp( regexS ), results = regex
            .exec( window.location.href );
        if ( results == null ) {
            return "";
        } else {
            return decodeURIComponent( results[ 1 ].replace( /\+/g, " " ) );
        }
    }
}

