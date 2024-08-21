

// define( [ "browser/utils", "agent/service-live-plots" ], function ( utils, alertPlot ) {


//     console.log( "Module loaded 99" ) ;


import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";

import utils from "../utils.js"

import alertPlot from "./service-live-plots.js"


export const serviceLive = Service_Live();


function Service_Live() {

    _dom.logHead( "Module loaded" );


    const $liveContent = $( "#agent-tab-live", "#agent-tab-content" );


    let $metricDetails = $( "#metricDetails", $liveContent );
    let $dataPanel = $( "#data-panel", $liveContent );


    const $healthStatusSpan = $( "#healthStatus", $liveContent );

    let lastCollectionSeconds = 0;

    let podName = "csap-agent";
    let isAlarmInfoHidden = false;
    let isPodProxyMode = false;

    let _defaultrefreshOnce = false;

    let $refreshData = $( "#refreshData", $liveContent );
    let $refreshButton = $( "#refreshMetrics", $liveContent );
    let $meterDescriptionFilter = $( "#meter-desc-filter", $liveContent );
    let $switchTableView = $( "#switch-table-view", $liveContent );
    let _autoTimer = null;
    let $alertsBody = $( "#alertsBody", $liveContent );
    let $defBody = $( "#defBody", $liveContent );
    let $healthTable = $( "#health", $liveContent );
    let $numberOfHours = $( "#numberHoursSelect", $liveContent );
    let $metricTable = $( "#metricTable", $liveContent );
    let $metricBody = $( "#metricBody", $liveContent );
    let $meterView = $( "#meter-view", $liveContent );
    let $deltaReportSelect = $( "#meter-delta-view", $liveContent );
    let $friendlyUnits = $( "#meter-friendly-units", $liveContent );

    let _alertsCountMap = new Object();

    let applyFilterFunction;

    const MILLION = 1000000;
    let _filterTimer = null;
    let _tagFilterTimer = null;

    let testCountParam;

    let init;

    let _liveDataWorker;

    let isPrometheus = false;

    let _latestMeterReport;
    let _rebindTableButtons;


    return {
        show: function ( $menuContent, forceHostRefresh, menuPath ) {


            if ( !init ) {
                registerForEvents();
            }


            let selectedService = utils.getServiceSelector().val();
            if ( selectedService !== utils.showDefaultServicesName() ) {
                podName = selectedService;
            } else {
                podName = "csap-agent";
            }

            return autoRefreshMetrics();

            //setTimeout( autoRefreshMetrics, 30 ) ;

        }

    };


    function registerForEvents() {

        // FF NOT supported: { type: 'module'  }
        _liveDataWorker = new Worker( `${ JS_URL }/modules/browser/agent/service-live-worker.js` );

        _liveDataWorker.onmessage = function ( e ) {
            let workerResponse = e.data;
            _dom.csapDebug( 'workerResponse', workerResponse );

            if ( workerResponse.isPrometheus ) {
                $( "#snap-max-header", $metricTable ).text( "Rate" );
                $( "#snap-95-header", $metricTable ).text( "95th" );
                $( "th:nth-child(5) ", $metricTable ).hide();

                isPrometheus = true;
            }

            _latestMeterReport = workerResponse.meterReport;

            processMicroMeters( workerResponse.meterReport );
        }


        init = true;
        utils.getServiceSelector().change( function () {

            podName = $( this ).val();
            autoRefreshMetrics();

        } );

        isAlarmInfoHidden = true;


        $( ".simon-only" ).hide();
        //        $( ".ui-tabs-nav" ).hide() ;
        $meterView.val( "api" );


        isPodProxyMode = true;



        let refreshNow = function () {
            utils.loading( `reloading table` );

            setTimeout( () => {
                autoRefreshMetrics( true );
            }, 300 );

        }

        $refreshData.change( refreshNow );

        $numberOfHours.change( getAlerts );

        $( "#refreshAlerts" ).click( function () {

            getAlerts();

        } );

        $switchTableView.click( function () {
            $metricTable.toggle();
            $healthTable.toggle();
            $meterDescriptionFilter.toggle();
        } )


        let microSortOrder = [ [ 7, 1 ] ];
        $meterView.change( function () {


            isAlarmInfoHidden = true;
            $( ".simon-only" ).hide();

            if ( $( this ).val() == "meter-only" ) {
                switchViewToMeterOnly();
                $metricTable.trigger( "updateAll" );
                $metricTable.data( 'tablesorter' ).sortList = microSortOrder;
            } else if ( $( this ).val() == "starter" ) {
                //                getDefinitions() ;
                isAlarmInfoHidden = false;
                //                $( ".ui-tabs-nav" ).show() ;
                $( ".simon-only" ).show();
                $switchTableView.css( "visibility", "visible" );
                setTimeout( function () {
                    $metricTable.find( 'th:eq(1)' ).trigger( 'sort' );
                }, 1000 );


            }
            $( "#meter-tag-filter" ).hide();
            if ( $( this ).val() == "apiAggregated" ) {
                $( "#meter-tag-filter" ).show();
            }

            refreshNow();


        } );


        $friendlyUnits.change( refreshNow );
        $deltaReportSelect.change( refreshNow );

        // alerts will refresh getMicroMeters
        $refreshButton.click( function () {
            if ( isAlarmInfoHidden ) {
                getMicroMeters();
            } else {
                getAlerts();
            }
        } );

        $( "#clearMetrics" ).click( function () {
            $.getJSON(
                baseUrl + "/../clearMetrics" )
                .done( function ( metricResponse ) {

                    alertify.alert( "Experimental: Only non jvm and non tomcat are cleared: " + metricResponse.cleared + " cleared, skipped: " + metricResponse.skipped );
                    getMicroMeters();

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "clearing alerts", errorThrown );
                } );
        } );

        // applyFilterFunction = utils.addTableFilter( $meterDescriptionFilter, $metricTable );


        if ( !applyFilterFunction ) {

            $meterDescriptionFilter.keyup( function () {
                // 
                console.log( `meter filter change` )
                clearTimeout( _filterTimer );
                _filterTimer = setTimeout( function () {
                    //getMicroMeters();
                    processMicroMeters( _latestMeterReport );
                }, 500 );


                return false;
            } );
        }

        $( "#meter-tag-filter" ).keyup( function () {
            // 
            console.log( `meter filter change` )
            clearTimeout( _tagFilterTimer );
            _tagFilterTimer = setTimeout( function () {
                getMicroMeters();
            }, 500 );


            return false;
        } );

        $.tablesorter.addParser( {
            // set a unique id
            id: 'raw',
            is: function ( s, table, cell, $cell ) {
                // return false so this parser is not auto detected
                return false;
            },
            format: function ( s, table, cell, cellIndex ) {
                let $cell = $( cell );
                // console.log("timestamp parser", $cell.data('timestamp'));
                // format your data for normalization
                return $cell.data( 'raw' );
            },
            // set type, either numeric or text
            type: 'numeric'
        } );

        $healthTable.tablesorter( {
            sortList: [ [ 0, 1 ] ],
            theme: 'csapSummary'
        } );

        let sortOrder = [ [ 1, 1 ], [ 7, 1 ], [ 2, 1 ] ];
        if ( isAlarmInfoHidden ) {
            sortOrder = microSortOrder;
        }

        $metricTable.tablesorter( {
            sortList: sortOrder,
            stringTo: "bottom",
            emptyTo: 'bottom',
            theme: 'csapSummary'
        } );


        $dataPanel.hover( function () {

            //console.log( `_rebindTableButtons ${ _rebindTableButtons }`) ;
            if ( _rebindTableButtons ) {
                bindButtonsInRows();
            }
            _rebindTableButtons = false;
        }, function () {
            //console.log( "hover off") ;
        } );

        $( "tr", $defBody ).each( function ( index ) {
            let $defRow = $( this );
            let defId = $( ":nth-child(1) span", $defRow ).text().trim();
            _alertsCountMap[ defId ] = 0;
        } );


    }

    function bindButtonsInRows() {
        console.debug( `binding buttons` );
        $( "button.plot-launch", $metricBody ).click( function () {
            alertPlot.addPlot( $( this ).data( "name" ) );

            setTimeout( function () { $dataPanel.scrollTop( 0 ); }, 500 );
            //$meterDescriptionFilter.parent().css("top", "3px") ;
            $dataPanel.animate( {
                scrollTop: $dataPanel.offset().top
            }, 300 );
        } );
        $( "button.id-launch", $metricBody ).click( function () {
            let name = $( this ).data( "name" );
            let message = name + " : " + JSON.stringify( _latestMeterReport[ name ], "\n", "\t" );
            alertify.csapInfo( message );
        } );
    }

    function autoRefreshMetrics( doNow ) {

        $( "#meter-source", $liveContent ).empty().text( podName );

        let newInterval = $refreshData.val();
        clearTimeout( _autoTimer );

        if ( newInterval < 0 && !doNow ) {
            console.log( `disabled autorefresh` );
            return;
        }

        if ( isAlarmInfoHidden ) {
            getMicroMeters();
        } else {
            getAlerts();
        }


        _autoTimer = setTimeout( function () {
            autoRefreshMetrics();
        }, newInterval * 1000 );
    }

    function busy( show ) {

        if ( show ) {
            $refreshButton.removeClass( "refresh-window" );
            $refreshButton.addClass( "csap-activity" );
        } else {
            $refreshButton.addClass( "refresh-window" );
            $refreshButton.removeClass( "csap-activity" );
        }
    }

    function workerRequest( command ) {
        return {
            command
        }
    }

    function getMicroMeters() {




        busy( true );
        let apiRequest = $meterView.val();
        let params = {
            meterView: apiRequest,
            precision: 3
        };

        if ( apiRequest == "starter" ) {
            $.extend( params, {
                "aggregate": true,
                "tagFilter": "csap-collection"
            } );
        }

        if ( apiRequest == "apiDetails" ) {
            $.extend( params, {
                "details": true
            } );
        }
        if ( apiRequest == "apiAggregated" ) {
            $.extend( params, {
                "aggregate": true,
                "tagFilter": $( "#meter-tag-filter" ).val()
            } );
        }

        let microUrl = `/podProxy/${ podName }?csapui=true`;

        _liveDataWorker.postMessage( [ {
            command: "getMicroMeters",
            meterView: $meterView.val(),
            microUrl,
            params
        } ] );

        return false;

    }



    function processMicroMeters( microMeterReport ) {

        $( "div.meter-errors", $dataPanel ).remove();


        if ( microMeterReport.error ) {
            let $error = jQuery( '<div/>', {
                class: "csap-white meter-errors",
                text: `${ microMeterReport.error }`
            } ).css( "word-break", "break-all" ).css( "width", "40em" );
            jQuery( '<div/>', {
                //                text: microMeterReport.details
                class: "csap-blue",
                text: `verify csap live integration is available and enabled`
            } ).appendTo( $error );

            $error.append( `source: ${ microMeterReport.source }` );

            $metricBody.empty();
            $dataPanel.append( $error );
            return;
        }




        let $updatedData = jQuery( '<tbody/>', {} );
        addMeterRows( microMeterReport, $updatedData );

        // support for "raw" data
        addRawRows( microMeterReport.report, $updatedData );

        $metricBody.html( $updatedData.html() );
        busy( false );

        if ( !$( "th.simon-only", $metricTable ).is( ":visible" ) ) {
            $( "td.simon-only", $metricTable ).hide();
        }
        $metricTable.trigger( "update" );

        _rebindTableButtons = true;


        alertPlot.drawPlots();
        updateCounts();

        utils.loadingComplete();
    }


    function addRawRows( report, $updatedData ) {

        for ( let fieldName in report ) {

            let $row = jQuery( '<tr/>', {} );
            $row.appendTo( $updatedData );

            jQuery( '<td/>', {
                text: fieldName
            } ).appendTo( $row );

            //$row.append( buildMicroValue(  ) ) ;


            let $detailsCol = jQuery( '<td/>', {
                colspan: 99
            } ).appendTo( $row );

            let $details = jQuery( '<pre/>', {
                text: JSON.stringify( report[ fieldName ], "\n", "   " ),
                colspan: 99
            } ).appendTo( $detailsCol );

            //            $row.append( buildMicroValue(  ) ) ;
            //            $row.append( buildMicroValue(  ) ) ;
            //            $row.append( buildMicroValue(  ) ) ;
            //            $row.append( buildMicroValue( ) ) ;
            //            $row.append( buildMicroValue(  ) ) ;

        }

    }

    function addMeterRows( microMeterReport, $updatedData ) {
        //
        // Get collection delta
        let currentSecondsInterval = 1;
        let upTimeSeconds = 1;
        alertPlot.addTimeNow();
        for ( let meterName in microMeterReport ) {
            if ( meterName.startsWith( "process.uptime" ) ) {

                let uptimeSecondsFromReport = microMeterReport[ meterName ];
                currentSecondsInterval = uptimeSecondsFromReport - lastCollectionSeconds;
                lastCollectionSeconds = uptimeSecondsFromReport;
                upTimeSeconds = uptimeSecondsFromReport;
                break;
            }
        }

        let includeFilter = $meterDescriptionFilter.val();
        for ( let meterName in microMeterReport ) {

            let meter = microMeterReport[ meterName ];

            let latestDiff = 0 ;
            if ( isObject( meter ) ) {
                let count = meter.count;
                if ( meter.value ) {
                    count = meter.value;
                }
                latestDiff = alertPlot.addData( meterName, currentSecondsInterval, count, meter );
            } else {
                latestDiff = alertPlot.addData( meterName, currentSecondsInterval, meter )
            }

            
            if ( $deltaReportSelect.val() > 0 ) {

                let currentLimit = Number.parseInt( $deltaReportSelect.val() );
                // console.log( `latestDiff: ${ latestDiff } currentLimit: ${ currentLimit }` );
                if ( isNaN( latestDiff )
                    || Math.abs( latestDiff ) < currentLimit ) {
                    // console.log( `latestDiff: ${ latestDiff } currentLimit: ${ currentLimit }` );
                    continue ;
                }
            }


            if ( !applyFilterFunction
                && includeFilter.length > 0 ) {

                let filterEntries = includeFilter.toLowerCase().split( "," );
                let meterNameLower = meterName.toLowerCase();
                let foundMatch = false;
                for ( let filterItem of filterEntries ) {

                    if ( meterNameLower.includes( filterItem ) ) {
                        foundMatch = true;
                    }
                }
                if ( !foundMatch ) {

                    continue;
                }
            }
            if ( meterName == "health-report" ) {
                let healthReport = microMeterReport[ "health-report" ];

                console.log( "healthReport: ${ Object.keys( healthReport ) }" );

                updateStatus( healthReport.isHealthy,
                    healthReport.lastCollected,
                    upTimeSeconds );

                $( "#show-health-issues" )
                    .off()
                    .click( function () {
                        let message = JSON.stringify( healthReport, "\n", "\t" );
                        alertify.csapInfo( message );
                    } )
                    .show();

                if ( !healthReport.isHealthy && healthReport.errors ) {
                    $healthStatusSpan.append( "(" + healthReport.errors.length + ")" );
                }


                continue;
            }


            //console.log(`meter: ${meterName}`) ;
            if ( meterName.startsWith( "process.uptime" )
                || meterName === "report"
                || meterName === "timers" ) {
                continue;
            }

            let $row = jQuery( '<tr/>', {} );
            $row.appendTo( $updatedData );

            let $nameParentCell = jQuery( '<td/>', {} );


            let $tag = jQuery( '<span/>', { class: "meter-tag" } );
            $row.append( $nameParentCell );

            let $nameCell = jQuery( '<div/>', { class: "meter-name" } );
            $nameParentCell.append( $nameCell );

            let nameFormatted = meterName;
            let tagIndex = meterName.indexOf( "[" );
            if ( tagIndex > 0 ) {
                nameFormatted = meterName.substr( 0, tagIndex );
                $tag.text( meterName.substr( tagIndex ).replace( /,/g, ', ' ) );
            }

            if ( isPrometheus ) {
                if ( meter?.details?.tags ) {
                    for ( let tagName in meter.details.tags ) {
                        nameFormatted = nameFormatted.replaceAll( meter.details.tags[ tagName ], "" );
                    }
                }
            }


            let $nameFormatted = jQuery( '<span/>', { text: nameFormatted } );

            let $idButton = jQuery( '<button/>', {
                class: "csap-icon csap-info id-launch",
                title: "View collection ids",
                "data-name": meterName
            } );

            let $graphButton = jQuery( '<button/>', {
                class: "csap-icon csap-graph plot-launch",
                title: "Show Graph",
                "data-name": meterName
            } );

            $nameCell.append( $idButton );
            $nameCell.append( $graphButton );
            $nameCell.append( $nameFormatted );
            //$nameCell.append( $tag );
            $nameFormatted.append( $tag );

            //console.log( `meter: ${meterName}, isObject ${ isObject( meter )}`, meter )
            // let latestDiff = 0;
            if ( isObject( meter ) ) {
                let meterDescription = meterName;

                if ( meter.details ) {

                    // && $meterView.val() == "apiDetails"
                    let titleForTag = "";
                    if ( meter.details.description ) {
                        meterDescription = meter.details.description;

                        if ( isPrometheus ) {
                            titleForTag = meterDescription;
                        }
                        if ( $meterView.val() == "apiDetails" ) {
                            $nameFormatted.append( jQuery( '<div/>', {
                                class: "quote",
                                html: meterDescription
                            } ) );
                        }
                    }

                    if ( meter.details.tags ) {

                        let $tagQuote = jQuery( '<div/>', {
                            class: "quote",
                        } );

                        $nameFormatted.append(
                            $tagQuote
                        );

                        for ( let tagName in meter.details.tags ) {
                            jQuery( '<span/>', {
                                class: "sub-tag",
                                title: titleForTag,
                                text: tagName + ": " + meter.details.tags[ tagName ]
                            } ).appendTo( $tagQuote );
                        }
                    }
                }

                if ( !isAlarmInfoHidden ) {
                    $row.append( buildAlertCell( meterName ) );
                } else {
                    $row.append( jQuery( '<td/>', {
                        class: "simon-only",
                        text: ""
                    } ) );
                }

                let count = meter.count;
                if ( meter.value ) {
                    count = meter.value;
                }
                // latestDiff = alertPlot.addData( meterName, currentSecondsInterval, count, meter );

                let countUnit = null;
                let timeUnit = "ms";

                if ( isPrometheus ) {

                    let meterDescriptionCheck = meterDescription.toLowerCase();

                    if ( meterDescriptionCheck.includes( "inmbval" ) ) {
                        countUnit = "bytes";
                        count = count * 1024 * 1024;

                    } else if ( meterDescriptionCheck.includes( "bytes" )
                        || meterDescriptionCheck.includes( "memory" )
                        || meterDescriptionCheck.includes( "disk" )
                        || meterDescriptionCheck.includes( "indexsize" )
                        || meterDescriptionCheck.includes( "messagesize" ) ) {
                        countUnit = "bytes";
                    } else if ( !meterDescriptionCheck.includes( "milli" )
                        && !meterDescriptionCheck.includes( "time" ) ) {
                        timeUnit = null;
                    } else if ( meterDescriptionCheck.includes( "time" )
                        || meterDescriptionCheck.includes( "millis" )
                        && ( !meterDescriptionCheck.endsWith( "millis" ) ) ) {
                        countUnit = "ms";
                        if ( meter[ "total-ms" ] == undefined ) {
                            meter[ "total-ms" ] = count;
                        }
                    }

                } else {

                    let meterNameCheck = meterName.toLowerCase();
                    // translations for spring micro meter
                    if ( meterNameCheck.includes( "bytes" )
                        || meterNameCheck.includes( "memory" )
                        || meterNameCheck.includes( "disk" )
                        || meterNameCheck.includes( "indexsize" )
                        || meterNameCheck.includes( "messagesize" ) ) {
                        countUnit = "bytes";
                        if ( meterNameCheck.includes( ".mb" ) ) {
                            count = count * 1024 * 1024;
                        }
                    }
                }

                if ( !$friendlyUnits.prop( "checked" ) ) {
                    countUnit = null;
                    timeUnit = null;
                }
                $row.append( buildMicroValue( count, countUnit ) );

                $row.append( buildMicroValue( meter[ "mean-ms" ], timeUnit ) );

                let $snapMean = buildMicroValue( meter[ "bucket-0.5-ms" ], timeUnit );
                $row.append( $snapMean );
                if ( isPrometheus ) {
                    $snapMean.hide();
                }
                let $snap95 = buildMicroValue( meter[ "bucket-0.95-ms" ], timeUnit );
                $row.append( $snap95 );
                $row.append( buildMicroValue( meter[ "bucket-max-ms" ], timeUnit ) );
                $row.append( buildMicroValue( meter[ "total-ms" ], timeUnit ) );
            } else {
                if ( !isAlarmInfoHidden ) {
                    $row.append( buildAlertCell( meterName ) );
                } else {
                    $row.append( jQuery( '<td/>', {
                        class: "simon-only",
                        text: ""
                    } ) );
                }

                let countUnit = null;
                // translations for spring micro meter
                if ( meterName.includes( "bytes" )
                    || meterName.includes( "memory" )
                    || meterName.includes( "disk" )
                    || meterName.includes( "indexsize" )
                    || meterName.includes( "messagesize" ) ) {
                    countUnit = "bytes";
                    if ( meterName.includes( ".mb" ) ) {
                        meter = meter * 1024 * 1024;
                    }
                }

                if ( !$friendlyUnits.prop( "checked" ) ) {
                    countUnit = null;
                }
                // alertPlot.addData( meterName, currentSecondsInterval, meter )
                $row.append( buildMicroValue( meter, countUnit ) );
                $row.append( buildMicroValue( meter.missing ) );
                $row.append( buildMicroValue( meter.missing ) );
                $row.append( buildMicroValue( meter.missing ) );
                $row.append( buildMicroValue( meter.missing ) );
                $row.append( buildMicroValue( meter.missing ) );
            }

            // filterMetricRow( $row );

            if ( applyFilterFunction ) {
                applyFilterFunction( $row );
            }

        }

    }

    function buildAlertCell( meterName ) {

        let alertContents = "";
        let $alertImage = "";
        let alertValue = "";

        console.debug( `_alertsCountMap: ${ meterName }`, _alertsCountMap );
        if ( _alertsCountMap[ meterName.trim() ] == 0 ) {

            console.log( `no alerts: ${ meterName } ` )
            alertContents = "";
            $alertImage = jQuery( '<span/>', {
                text: alertContents,
                class: "status-green"
            } );
            alertValue = 0;
        } else if ( _alertsCountMap[ meterName.trim() ] > 0 ) {
            console.log( `found alerts: ${ meterName } ` )
            alertContents = _alertsCountMap[ meterName.trim() ];
            $alertImage = jQuery( '<span/>', {
                text: alertContents,
                class: "status-red"
            } );
            alertValue = 1;
        }


        let $alertCell = jQuery( '<td/>', {
            class: "simon-only",
            "data-raw": alertValue
        } );

        if ( $alertImage != "" ) {
            $alertCell.append( $alertImage );
        }
        return $alertCell;
    }

    function updateCounts() {

        let $rows = $( "tr", $metricBody );
        let $visibleRows = $( "tr:visible", $metricBody );
        let countMessage = $visibleRows.length;
        if ( $visibleRows.length != $rows.length ) {
            countMessage += " of " + $rows.length;
        }
        $( "#meter-count", $liveContent ).text( countMessage ).parent().show();

        if ( !_defaultrefreshOnce
            && $rows.length > 400 ) {
            _defaultrefreshOnce = true;
            $refreshData.val( "10" );

            if ( $rows.length > 1500 ) {
                $refreshData.val( "20" );
            }
        }
    }

    function buildMicroValue( collectedValue, unit ) {
        let showVal = "";
        let sortVal = "";
        if ( collectedValue != undefined ) {
            sortVal = collectedValue;
            let collected = collectedValue;
            if ( $.isNumeric( collectedValue ) ) {
                collected = collectedValue.toFixed( 1 );
                if ( collectedValue > 10000000000000
                    || ( collectedValue > 0 && collectedValue < 1 ) ) {
                    collected = collectedValue.toPrecision( 5 );
                }
            }
            showVal = collected;
            if ( unit && unit == "ms" ) {
                showVal = utils.adjustTimeUnitFromMs( collected );

            } else if ( unit && unit == "bytes" ) {
                showVal = utils.bytesFriendlyDisplay( Math.round( collectedValue ) );

            } else {
                if ( ( showVal + "" ).endsWith( ".0" ) ) {

                    //showVal = '<span class="padZero">' + val.toFixed(0) + "</span>"
                    //showVal =  val.toFixed(0) ;
                    showVal = numberWithCommas( collectedValue )
                }
            }
        }

        let $cell = jQuery( '<td/>', {
            html: showVal,
            title: collectedValue,
            "data-raw": sortVal
        } )
        return $cell;
    }



    function isObject( theReference ) {

        if ( theReference == null )
            return false;

        return typeof theReference === "object";
    }

    function updateStatus( isHealthy, lastCollected, uptimeSeconds ) {


        $healthStatusSpan.removeClass( `status-green status-red` );

        $healthStatusSpan.empty();
        let status = `status-green`;
        if ( !isHealthy ) {
            status = 'status-red';
        }

        $healthStatusSpan.parent().attr( "title", "last refreshed: " + lastCollected );

        $healthStatusSpan.addClass( status );

        let uptimeWithUnit = utils.adjustTimeUnitFromMs( uptimeSeconds * 1000 );
        $( "#uptime" ).html( ` up: ${ uptimeWithUnit }` );
    }


    function getMetricItem( name ) {
        let params = {
            "name": name,
            meterView: $meterView.val()
        };
        $.getJSON(
            baseUrl + "/../metric", params )
            .done(
                showMetricDetails )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "clearing alerts", errorThrown );
            } );
    }


    function showMetricDetails( metricResponse ) {

        $( ".name", $metricDetails ).text( metricResponse.name );
        $( "#firstTime", $metricDetails ).text( metricResponse.firstUsage );
        $( "#lastTime", $metricDetails ).text( metricResponse.lastUsage );
        $( "#maxTime", $metricDetails ).text( metricResponse.maxTimeStamp );

        if ( !metricResponse.details ) {
            alertify.alert( "Details not found" );
            return;
        }
        let detailItems = metricResponse.details.split( "," );

        let $tbody = $( "tbody", $metricDetails );
        $tbody.empty();

        for ( let i = 0; i < detailItems.length; i++ ) {

            let $row = jQuery( '<tr/>', {} );
            $row.appendTo( $tbody );

            let items = detailItems[ i ].split( "=" );

            if ( items[ 0 ].includes( "name" ) || items[ 0 ].includes( "note" ) )
                continue;

            jQuery( '<td/>', {
                text: items[ 0 ]
            } ).appendTo( $row );
            jQuery( '<td/>', {
                text: items[ 1 ]
            } ).appendTo( $row );
        }




        alertify.alert( $metricDetails.html() );

    }



    function numberWithCommas( collectedValue ) {

        let displayValue = collectedValue;
        let unit = "";
        if ( ( collectedValue + "" ).endsWith( ".0" ) ) {
            displayValue = collectedValue.toFixed( 0 );
        }
        if ( collectedValue > MILLION ) {
            let millions = ( collectedValue / MILLION );
            if ( millions < 10 ) {
                displayValue = millions.toFixed( 2 );
            } else if ( millions < 100 ) {
                displayValue = millions.toFixed( 1 );
            } else {
                displayValue = millions.toFixed( 0 );
            }
            unit = "<span class=munits title=million>m</span>"
        }

        displayValue = displayValue.toString().replace( /\B(?=(\d{3})+(?!\d))/g, "," );
        return displayValue + unit;

    }

    function getAlerts() {

        busy( true );

        let alertUrl = baseUrl + "/alerts";

        let paramObject = {
            hours: $numberOfHours.val()
        };

        if ( isPodProxyMode ) {

            if ( podName == "default" ) {
                alertUrl = baseUrl + "/../metrics/micrometers";
            } else {
                // csap pod connection
                alertUrl = `/podProxy/${ podName }`;
            }
            paramObject = {
                alertReport: true,
                hours: $numberOfHours.val()
            };
        }

        if ( testCountParam ) {
            $.extend( paramObject, {
                testCount: testCountParam
            } );
        }

        console.log( `loading alarms ${ alertUrl }` );

        $.getJSON( alertUrl, paramObject )
            .done( function ( alertReport ) {

                busy( false );

                //console.log( "alertResponse", alertResponse ) ;

                let alertResponse = JSON.parse( alertReport.response );


                $alertsBody.empty();
                let alerts = alertResponse.triggered;
                if ( alerts.length == 0 ) {
                    let $row = jQuery( '<tr/>', {} );

                    $row.appendTo( $alertsBody );

                    $row.append( jQuery( '<td/>', {
                        colspan: 99,
                        text: "No alerts found. Adjust filters as needed."
                    } ) )
                } else {
                    addAlerts( alerts );
                }

                $healthTable.trigger( "update" );
                getMicroMeters();

            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "getting alerts", errorThrown );
            } );

    }

    function addAlerts( alerts ) {

        for ( let id in _alertsCountMap ) {
            _alertsCountMap[ id ] = 0;
        }
        for ( let i = 0; i < alerts.length; i++ ) {
            let $row = jQuery( '<tr/>', {} );

            let alert = alerts[ i ]

            $row.appendTo( $alertsBody );

            jQuery( '<td/>', {
                text: alert.time,
                "data-raw": alert.ts
            } ).appendTo( $row );

            jQuery( '<td/>', {
                text: alert.id
            } ).appendTo( $row );

            if ( !_alertsCountMap[ alert.id ] ) {
                _alertsCountMap[ alert.id ] = 0;
            }

            _alertsCountMap[ alert.id ] = _alertsCountMap[ alert.id ] + 1;

            jQuery( '<td/>', {
                text: alert.type
            } ).appendTo( $row );

            let desc = alert.description;
            if ( alert.count > 1 ) {
                desc = desc + "<br/><div>Alerts Throttled: <span>" + alert.count + "</span></div>";
            }
            jQuery( '<td/>', {
                html: desc
            } ).appendTo( $row );
        }

        if ( alerts.length > 0 ) {
            _alertsCountMap[ "csap.health.report.fail" ] = alerts.length;
        } else {
            _alertsCountMap[ "csap.health.report.fail" ] = 0;
        }
    }
}