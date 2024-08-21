
import "../libs/csap-modules.js";

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";


_dom.onReady( function () {

    let appScope = new Project_Health_Report();
    appScope.initialize();

} );

function Project_Health_Report() {


    _dom.logHead( "Main module" );


    this.initialize = function () {
        console.log( "Init in health..." );

        $.when( loadHealthInfo() ).then( addTableSorter )

    };;

    function loadHealthInfo() {
        let r = $.Deferred();
        let projName = getParameterByName( "projName" );
        $.getJSON( "api/report/healthMessage", {
            "projectName": projName
        } ).done( function ( loadJson ) {
            healthMessageSuccess( loadJson );
        } );
        setTimeout( function () {
            console.log( 'loading health info  done' );
            r.resolve();
        }, 500 );
        return r;
    }

    function addTableSorter() {
        $( "#healthTable" ).tablesorter( {
            sortList: [ [ 0, 0 ] ],
            theme: 'csapSummary'
        } );
    }

    function healthMessageSuccess( dataJson ) {
        for ( let i = 0; i < dataJson.length; i++ ) {
            let hostName = dataJson[ i ].host;

            let data = dataJson[ i ].data;
            let errors = data.errors;
            let errorList = errors[ hostName ];
            let healthContent = '<td> ' + hostName + '</td>'
                + '<td> ' + dataJson[ i ].lifecycle + '</td>'
                + '<td> ' + getErrorMessage( errorList ) + '</td>';
            let healthContentTr = $( '<tr />', {
                'class': "",
                html: healthContent
            } );
            $( '#healthBody' ).append( healthContentTr );
        }
    }

    function getErrorMessage( errorList ) {
        let errorMessage = '';
        if ( errorList.length > 0 ) {
            let containerObject = jQuery( '<div/>' );
            let errorObject = jQuery( '<ol/>', {
                class: " ",
                title: "List of errors"
            } ).appendTo( containerObject );
            for ( let i = 0; i < errorList.length; i++ ) {
                //errorMessage = errorMessage +'\n' +errorList[i] + ' ';
                //errorMessage.append()
                jQuery( '<li/>', {
                    class: "",
                    text: errorList[ i ]
                } ).css( {
                    "padding": "2px"
                } ).appendTo( errorObject );
            }
            errorMessage = containerObject.html();
        }
        return errorMessage;
        /*
         if(dataJson[i].errors.states.processes != undefined){
         return dataJson[i].errors.states.processes.message;
         }
         if(dataJson[i].errors.states.memory != undefined){
         return dataJson[i].errors.states.memory.message;
         }
         */
        //return '';

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