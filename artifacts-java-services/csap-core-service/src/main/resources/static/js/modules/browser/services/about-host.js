
import _dom from "../../utils/dom-utils.js";

import _dialogs from "../../utils/dialog-utils.js";


const aboutHost = about_host();

export default aboutHost


function about_host() {

    _dom.logHead( "Module loaded" );

    return {
        show: function ( host ) {
            show( host );
        }
    };

    function show( host ) {
        let paramObject = {
            hostName: host
        };

        let rootUrl = baseUrl;
        try {
            rootUrl = contextUrl;
        } catch ( e ) {
            console.log( "Using baseurl" );
        }

        $.getJSON(
            rootUrl + "os/hostOverview", paramObject )
            .done( function ( response ) {
                buildAbout( response, host );
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {
                console.log(jqXHR)
                _dialogs.showJsonError( "Host Overview", jqXHR );
            } );
    }

    function buildAboutLine( label, value, valueClass="noteAlt" ) {
        let $line = jQuery( '<label/>', { class: "csap-form" } );

        jQuery( '<span/>', { class: "label", text: label + ":" } ).appendTo( $line );
        jQuery( '<div/>', { class: valueClass, html: value } )
            .css( "font-weight", "bold" )
            .css( "color", "black" )
            .css( "white-space", "pre" )
            .appendTo( $line );

        return $line;
    }

    function buildAbout( loadJson, host ) {
        let $wrap = jQuery( '<div/>', { class: "aWrap" } );

        let $about = jQuery( '<div/>', { class: "info about-host-info" } ).appendTo( $wrap );
        $about.append( buildAboutLine( "Host", host ) );
        $about.append( buildAboutLine( "Version", loadJson.redhat ) );
        $about.append( buildAboutLine( "Uptime", loadJson.uptime ) );
        $about.append( buildAboutLine( "uname", loadJson.uname ) );

        let $dfTable = jQuery( '<table/>', { class: "csap sticky-header" } ).appendTo( $about );

        let dfLines = loadJson.df.split( "\n" );
        for ( let i = 0; i < dfLines.length; i++ ) {

            let $tableSection = $dfTable;
            let type = '<td/>';
            if ( i == 0 ) {
                $tableSection = jQuery( '<thead/>', {} ).appendTo( $dfTable );
                type = '<th/>';
            }

            let $row = jQuery( '<tr/>', { class: "" } ).appendTo( $tableSection );

            let fields = dfLines[ i ].trim().split( /\s+/ );

            if ( fields.length >= 5 ) {
                for ( let j = 0; j < fields.length; j++ ) {
                    let clazz ="numeric" ;
                    if ( j == fields.length -1 ) {
                        clazz="";
                    }

                    jQuery( type, { text: fields[ j ], class: clazz } ).appendTo( $row );
                }
            }
        }

        $about.append( buildAboutLine( "Disk", $dfTable, "df-table" ) );

        //$about.append( buildAboutLine( "Disk", "df Output\n" + loadJson.df ) ) ;

        alertify.alert( "About " + host, $wrap.html() );

        $( ".alertify" ).css( "width", "800px" );
        $( ".alertify" ).css( "margin-left", "-400px" );
        $( ".awrap" ).css( "text-align", "justify" );
        $( ".awrap" ).css( "white-space", "pre-wrap" );
        $( 'body' ).css( 'cursor', 'default' );

    }
}
