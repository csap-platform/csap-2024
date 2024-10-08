
import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";
import utils from "../utils.js"

const events = performance_events();
export default events;

// export default agent ;


function performance_events() {

    _dom.logHead( "Module loaded" );


    const $performanceTab = $( "#performance-tab-content" );

    const $eventDetailsMenu = $( ".event-details-selected", "#performance-tab" );

    const $eventsContent = $( "#performance-tab-events", $performanceTab );
    const $eventsPanel = $( "#events-panel", $eventsContent );
    const $eventsTableBody = $( "tbody", $eventsContent );

    const $eventsFilter = $( "#event-filter", $eventsContent );

    const $dateControls = $( "#date-controls", $eventsContent );
    const $dateFrom = $( "#from", $dateControls );
    const $dateTo = $( "#to", $dateControls );
    const $category = $( "#event-category", $eventsContent );
    const $count = $( "#csap-event-count", $eventsContent );

    let _eventFilterTimer;
    let lastSelected;

    let applyFilterFunction;


    return {

        initialize: function () {
            initialize();
        },

        show: function ( $displayContainer, forceHostRefresh ) {


            if ( forceHostRefresh ) {
                $dateFrom.val( "" );
                $dateTo.val( "" );
                $category.val( "/csap/ui/*" );
            }
            return getEvents();



        },

        selected: function () {
            return lastSelected;
        },

        closeDetails: function () {
            utils.launchMenu( "performance-tab,events" );
            $eventDetailsMenu.hide();
        }
    };



    function initialize() {


        $( '#user-events', $eventsContent ).click( function () {
            console.log( "contents", $( '#activityCount' ).text() );
            if ( $( '#activityCount' ).text().includes( "disabled" ) ) {
                alertify.csapWarning( "csap-events-service is currently disabled in your Application.json definition" );
                return false;
            }

            let targetUrl = HISTORY_URL;
            let curPackage = utils.getActiveProject();
            if ( curPackage != "All Packages" ) {
                targetUrl += "&project=" + curPackage;
            }
            openWindowSafely( targetUrl, "_blank" );
            return false;
        } );

        let alwaysShowFunction = function () {
            $( `td.event-date-label`, $eventsTableBody ).parent().show();
        }
        applyFilterFunction = utils.addTableFilter( $eventsFilter, $eventsTableBody.parent(), alwaysShowFunction );

        $( "#copy-events-table", $eventsContent ).click( function () {

            let $activeTable = $( "table:visible", $eventsContent );
            utils.copyItemToClipboard( $activeTable );

        } );


        $("#csap-event-global", $eventsContent ).change( getEvents );
        $( "#event-limit", $eventsContent ).change( getEvents );
        $( "#event-refresh", $eventsContent ).click( getEvents );
        $dateFrom.change( getEvents );
        $dateTo.change( getEvents );
        $category.change( getEvents );


        $( "#event-category-combo", $eventsContent ).change( function () {
            $category.val( $( this ).val() );
            getEvents();
        } );

        $dateFrom.datepicker( {
            defaultDate: "+0w",
            changeMonth: true,
            numberOfMonths: 1,
            onClose: function ( selectedDate ) {
                var toDateVal = $dateTo.val();
                if ( toDateVal === '' ) {
                    $dateTo.val( selectedDate );
                }
                $dateTo.datepicker( "option", "minDate", selectedDate );
                searchSetup();
            }
        } );
        $dateTo.datepicker( {
            defaultDate: "+0w",
            changeMonth: true,
            numberOfMonths: 1,
            onClose: function ( selectedDate ) {
                var fromDateVal = $dateFrom.val();
                if ( fromDateVal === '' ) {
                    $dateFrom.val( selectedDate );
                }
                $dateFrom.datepicker( "option", "maxDate", selectedDate );
                searchSetup();
            }
        } );

    }

    function getEvents() {

        utils.loading( `Loading Events` );


        let parameters = {
            project: utils.getActiveProject(),
            count: $( "#event-limit", $eventsContent ).val(),
            category: $category.val(),
            isGlobal: $("#csap-event-global", $eventsContent ).is( ":checked" ),
            from: $dateFrom.val(),
            to: $dateTo.val(),
        }


        let viewLoadedPromise = _net.httpGet( `${ APP_BROWSER_URL }/events/csap`, parameters );

        viewLoadedPromise
            .then( eventsReport => {
                utils.loadingComplete();
                _dom.logArrow( `eventsReport: ${ eventsReport.events.length }` );
                if ( !eventsReport.events ) {
                    $eventsTableBody.empty();
                    alertify.csapInfo( "No Events found" );
                    return;
                }
                showEvents( eventsReport.events );

                applyFilterFunction();
            } )
            .catch( ( e ) => {
                console.warn( e );
            } );


        return viewLoadedPromise;

    }

    function showEvents( events ) {

        $eventsTableBody.empty();

        let lastDate = null;

        let eventCount = 0;

        for ( let event of events ) {
            eventCount++;

            let eventDate = event.date;
            if ( eventDate != lastDate ) {
                let $labelRow = jQuery( '<tr/>', {} )
                    .appendTo( $eventsTableBody );

                let label = eventDate;
                if ( label == "" ) {
                    label = "Today"
                }

                jQuery( '<td/>', {
                    text: label,
                    class: "event-date-label",
                    colspan: 3
                } ).appendTo( $labelRow );

            }
            lastDate = eventDate;


            let $row = jQuery( '<tr/>', {} )
                .appendTo( $eventsTableBody );
            let $dateColumn = jQuery( '<td/>', {} ).appendTo( $row );

            jQuery( '<div/>', { 
                class: "copy-only", 
                text: `${ event.date } ${ event.time } ` 
            } )
                .appendTo( $dateColumn );




            let $dateTime = jQuery( '<div/>', { class: "date-time" } )
                .appendTo( $dateColumn );

            let $date = jQuery( '<div/>', {
                class: "event-date",
                text: `${ eventDate }`
            } );

            var $timeButton = jQuery( '<button/>', {
                "data-id": event.id,
                "data-self": event.selfLink,
                title: "View Event details",
                class: "csap-button event-time",
                text: event.time
            } );


            $dateTime.append( $timeButton );
            $dateTime.append( $date );





            let $host = jQuery( '<span/>', {
                text: `${ event.host }`
            } );

            let $launchButton = jQuery( '<button/>', {
                "data-host": event.host,
                "data-sourcedate": event.sourceDate,
                title: "Launch Source",
                class: "csap-icon launch-window"
            } ).click( function () {
                // utils.agentUrl( $(this).data.host,  
                //     `/app-browser?graphDate=${ $(this).data.sourcedate }#agent-tab,system`) ;

                utils.openAgentWindow(
                    $( this ).data( "host" ),
                    `/app-browser?graphDate=${ $( this ).data( "sourcedate" ) }#agent-tab,system` );
            } ).appendTo( $host );




            let $hostColumn = jQuery( '<td/>', {} ).appendTo( $row );
            $hostColumn.append( $host );

            jQuery( '<a/>', {
                class: "csap-link copy-only",
                target: `_blank`,
                href: utils.agentUrl( event.host, `/app-browser?graphDate=${event.sourceDate }#agent-tab,system`),
                html: ` (go) `
            } )
                .appendTo( $hostColumn );


            let $eventDetailsColumn = jQuery( '<td/>', {
            } ).appendTo( $row );

            let $sumamry = jQuery( '<div/>', {
                class: "event-summary",
                text: `${ event.summary }`
            } ).appendTo( $eventDetailsColumn );



            if ( $("#csap-event-global", $eventsContent ).is( ":checked" ) ) {
                jQuery( '<span/>', {
                    text: event.lifecycle
                } ).appendTo( $sumamry );
            }


            let $details = jQuery( '<div/>', {
                class: "event-details"
            } ).appendTo( $eventDetailsColumn );


            jQuery( '<span/>', {
                text: event.category
            } ).appendTo( $details );

            let user = event.user;
            if ( !user ) {
                user = "";
            }
            jQuery( '<span/>', {
                text: user
            } ).appendTo( $details );




            //            let fields = Object.keys( event ) ;
            //            for ( let field of fields ) {
            //                jQuery( '<span/>', {
            //                    text: event[ field]
            //                } ).appendTo( $eventsPanel ) ;
            //            }

        }

        $count.text( `events: ${ eventCount }` );

        $( "button.event-time", $eventsTableBody ).off().click( showEvent );

    }

    function showEvent() {

        lastSelected = {
            self: $( this ).data( "self" ),
            id: $( this ).data( "id" )
        };

        $eventDetailsMenu.show();

        utils.launchMenu( "performance-tab,event-details" );


    }

}

