import _dom from "../utils/dom-utils.js";


import flotUtils from "./flotUtils.js"
import graphLayout from "./graphLayout.js"
import settings from "./settings.js"

import utils from "../browser/utils.js"


const hostGraphs = graphs_host();

export default hostGraphs


function graphs_host() {

    _dom.logHead( "Module loaded" );


    _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/dist/es5/jquery.flot.js`, true );
    _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/lib/jquery.event.drag.js`, true );
    _dom.loadScript( `${ JS_URL }/modules/graphs/flot-navigate-enhanced.js`, true );

    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/lib/jquery.mousewheel.js`, true );

    // DEBUGGGING with source lines
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.canvaswrapper.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.colorhelpers.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.js`, true );
    // // _dom.loadScript( `${ JS_URL }/modules/graphs/flot-with-fix.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.uiConstants.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.browser.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.legend.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.saturated.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.time.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.drawSeries.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.categories.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.axislabels.js`, true );
    // _dom.loadScript( `${ BASE_URL }webjars/flot/4.2.2/source/jquery.flot.logaxis.js`, true );


    let _statsForHostsGraphs = new Object();

    let $globalTimeZoneSelect = $( "#global-graph-time-zone" );


    let _colorCount = 0;
    let _isNeedsLabel = true;


    return {
        draw: function ( resourceGraph, $newContainer, metricsJson, host, _graphsInitialized ) {
            console.groupEnd();
            console.groupEnd();
            console.groupCollapsed( "Drawing graphs for host: " + host );
            drawGraphs( resourceGraph, $newContainer, metricsJson, host, _graphsInitialized );
            console.groupEnd();
        }
    };

    function getGraphId( graphName ) {
        let graphId = graphName;
        // backwards compatiblity: attribute was inferred from graphs, which were tweak for 
        switch ( graphName ) {
            case "Cpu_15s":
                graphId = "topCpu";
                break;

            case "diskUtilInMB":
                graphId = "diskUtil";
                break;

            case "rssMemoryInMB":
                graphId = "rssMemory";
                break;

            case "Cpu_As_Reported_By_JVM":
                graphId = "cpuPercent";
                break;

        }

        return graphId;
    }

    function getGraphCheckboxId( graphName, $currentGraph ) {

        let checkId = getGraphId( graphName ) + "CheckBox";
        if ( $( "#" + checkId, $currentGraph ).is( ':checked' ) ) {
            return checkId;
        }

        return graphName + "CheckBox";
    }

    function isGraphSelected( graphName, $currentGraph ) {

        if ( $( "#" + graphName + "CheckBox", $currentGraph ).is( ':checked' ) ) {
            return true;
        }
        let checkId = getGraphId( graphName ) + "CheckBox";
        if ( $( "#" + checkId, $currentGraph ).is( ':checked' ) ) {
            return true;
        }

        return false;
    }

    function drawGraphs( resourceGraph, $newContainer, hostMetricsReport, host, onCompleteDeferred ) {
        // console.log("drawGraph(): " + JSON.stringify(metricsJson, null, "\t") ) ;
        let $currentGraph = resourceGraph.getCurrentGraph();
        

        //alert("got here") ;


        let dataManager = resourceGraph.getDataManager();

        // $( 'div.qtip:visible' ).qtip( 'hide' );

        console.log( `drawGraphs(): host: ${ host } layout: ${ $currentGraph.attr( "id" ) } `
            + $( ".layoutSelect", $currentGraph ).val() );
        let currentDate = new Date();
        // FLOT apis work in GMT //


        let metricsOffset = $( ".graphTimeZone", $currentGraph ).val();

        if ( $globalTimeZoneSelect.length > 0 ) {
            metricsOffset = $globalTimeZoneSelect.val();
        }

        if ( metricsOffset == "Browser" ) {
            metricsOffset = currentDate.getTimezoneOffset();

        } else {

            if ( metricsOffset == "Host" ) {
                if ( typeof hostMetricsReport.attributes.timezone == 'undefined' ) {
                    alertify.notify( "Update csap agent to get host timezone information" );
                    $( ".graphTimeZone", $currentGraph ).val( "-8" );
                    metricsOffset = "-8";
                } else {
                    metricsOffset = hostMetricsReport.attributes.timezone;
                }
            }

            let today = new Date();
            if ( today.dst() && metricsOffset < 0 ) {
                // adjust for DST which logs are using
                metricsOffset = parseFloat( metricsOffset ) + 1;
            }

            metricsOffset = metricsOffset * -60;
        }
        $( "#metricsZoneDisplay", $currentGraph ).html(
            "GMT(" + metricsOffset + ")" );

        //metricsOffset=240;
        console.log( "drawGraph(): " + hostMetricsReport.attributes.id + " timezone: " + metricsOffset );

        // error check here for empty data
        let flotTimeOffsetArray = dataManager.getLocalTime(
            hostMetricsReport.data.timeStamp,
            metricsOffset );

        let originalTimestamps = hostMetricsReport.data.timeStamp.toReversed() ;
        if ( utils.isGraphSlicingActive(  ) ) {
            let simpleTimes = new Array() ;
            for ( let i=0; i < originalTimestamps.length ; i++) {
                simpleTimes.push(i) ;
            }
            flotTimeOffsetArray = simpleTimes.toReversed() ;
        }

        // javascript built in apis work on local. For displaying navigation to
        // work
        let sliderTimeOffsetArray = dataManager.getLocalTime( flotTimeOffsetArray, 0 - currentDate.getTimezoneOffset() );
        //		let sliderTimeOffsetArray = dataManager.getLocalTime( flotTimeOffsetArray, "-"
        //				+ currentDate.getTimezoneOffset() );
        // Very tricky
        // FLOT api require a full GMT offset, but local js only needs relative
        settings.modifyTimeSlider( $newContainer, flotTimeOffsetArray,
            sliderTimeOffsetArray, resourceGraph );


        console.log( `\n\n\n\n ==== checking for cleaniup of host ${ host }` );
        let hostPlots = dataManager.getHostGraph( host );
        if ( Array.isArray( hostPlots ) ) {
            for ( let currPlot of hostPlots ) {
                // Custom Hook into FLOT nav plugin.
                //let currPlot = dataManager.getHostGraph( host )[ i ];
                // currPlot.clear() ;
                console.debug( `==== cleaning up` );
                _dom.csapDebug( `currPlot: `, currPlot );
                currPlot.destroy();
                // jQuery.removeData( currPlot );
                // currPlot.setData( new Array() );
                // currPlot.draw();
                // currPlot.shutdown();
                //console.log("drawGraph(): Shutting down Plot " + i ) ;
            }
        }

        let hostPlotInstances = new Array();
        // resource graph
        // $("#debug").html( JSON.stringify(
        // buildPoints(metricsJson.data.timeStamp,
        // metricsJson.data.usrCpu) , null,"\t") ) ;

        let $hostPlotContainer = $( ".plotContainer", $newContainer );
        graphLayout.restore( resourceGraph, $hostPlotContainer );

        //return ;

        let graphs = hostMetricsReport.attributes.graphs;


        console.log( `graphs: ${ Object.keys( graphs ) }` );
        _dom.csapDebug( `graphs: `, graphs );

        let numGraphs = 0;
        for ( let graphName in graphs ) {

            if ( !isGraphSelected( graphName, $currentGraph ) )
                continue;

            // empty graphs check 
            if ( !graphs[ getGraphId( graphName ) ]
                || Object.keys( graphs[ getGraphId( graphName ) ] ).length === 0 ) {

                console.log( `${ host } Warning - found empty graph data for: ${ graphName }` );
                continue;
            }

            numGraphs++;
        }
        console.log( "numGraphs", numGraphs );
        if ( numGraphs == 0 ) {
            //alertify.notify( "Note - host: " + host + " contains no matches, removing" );
            console.log( `Host ${ host } contains no matches - ignoring output ` );
            //			$("#" + host + "Check").prop('checked', false);
            //			$("#" + host + "Check").trigger("click");
            // return;
        }

        if ( $( '.csv', $currentGraph ).prop( "checked" ) ) {

            alertify.notify( "Rendering csv" );

            drawCsv( graphs, hostMetricsReport, $hostPlotContainer );

            return;
        }

        let isStackHosts = false;
        let isIncrementalRendering = false;
        if ( $( "#isStackHosts" ).length > 0 && dataManager.getHostCount() > 1 ) {
            isStackHosts = $( "#isStackHosts" ).val() > -1;
            isIncrementalRendering = $( "#isStackHosts" ).val() == 99;
        }

        if ( hostMetricsReport.attributes.seriesInfo
            && Object.keys( hostMetricsReport.attributes.seriesInfo ).length > 0 ) {

            Object.keys( hostMetricsReport.attributes.seriesInfo ).forEach( function ( infoKey ) {
                let details = hostMetricsReport.attributes.seriesInfo[ infoKey ];
                let detailsArray = details.split( "," );
                let testForPrevious = detailsArray[ 0 ];
                if ( detailsArray.length > 1 ) {
                    testForPrevious = detailsArray[ 1 ];
                }

                // console.log( `key=${infoKey}  value=${details} testForPrevious=${testForPrevious}` );
                if ( !$( ".graph-info-messages ul" ).text().includes( testForPrevious ) ) {

                    let $item = jQuery( "<li/>", {} );
                    jQuery( "<label/>", {
                        class: "",
                        html: infoKey + " (" + host + ")"
                    } ).css( "min-width", "20em" ).appendTo( $item );

                    for ( let detailItem of detailsArray ) {
                        jQuery( "<span/>", {
                            text: detailItem
                        } ).appendTo( $item );
                    }
                    $( ".graph-info-messages ul" ).append( $item );
                    $( ".graph-info-messages button" ).off().click( function () {
                        $( ".graph-info-messages .settings" ).toggle();
                    } )
                }
            } );
            $( ".graph-info-messages" ).show();
        }


        let graphNamesToRender = Object.keys( graphs );

        let customView = resourceGraph.getSelectedCustomView();
        if ( customView != null ) {
            graphNamesToRender = customView.graphs;
            console.log( `customView.graphs: ${ customView.graphs } ` )
        }

        console.log( `graphs names ${ graphNamesToRender }` );
        let lastGraphName = "";
        for ( let graphName of graphNamesToRender ) {
            console.groupCollapsed( `Building: ${ graphName } on  ${ host }` );

            let graphData = graphs[ graphName ];

            if ( !graphData ) {
                console.log( `No data found - skipping ${ graphName }` );
                continue;
            }

            let needToGenerateStubData = Object.keys( graphData ).length === 0;
            if ( !needToGenerateStubData
                && graphName == "topCpu" ) {
                // topCpu graphs always include totalCpu. 
                needToGenerateStubData = Object.keys( graphData ).length === 1
            }


            if ( needToGenerateStubData ) {
                console.log( `*** Generating empty graph: ${ graphName } on  ${ host } because no keys found` );
                graphData.attributes_notfound = "missing-data-verify-os-packages*";
                if ( Array.isArray( hostMetricsReport.attributes.servicesRequested )
                    && hostMetricsReport.attributes.servicesRequested.length >= 1 ) {
                    graphData.attributes_notfound = hostMetricsReport.attributes.servicesRequested[ 0 ] + "*";
                }
                hostMetricsReport.attributes.notfound = 0;
            } else {
                console.log( `*** Found graph data: ${ graphName } on  ${ host } ` );
            }


            // if ( graphName != "OS_MpStat" ) continue;
            // if ( graphName != "topCpu") continue ;
            // alert(numGraphsChecked) ;
            // alert("Drawing: " + graphName) ;
            if ( !isGraphSelected( graphName, $currentGraph ) ) {
                console.log( "Skipping: " + graphName + " on " + host + " because it is not selected" );
                console.groupEnd();
                continue;
            }

            if ( window.csapGraphSettings != undefined ) {
                if ( getGraphId( graphName ) != getGraphId( window.csapGraphSettings.graph ) ) {
                    console.log( "Skipping: " + graphName + " on " + host + " because panel selection " );
                    console.groupEnd();
                    continue;
                }
                console.log( "csapGraphSettings: ", getGraphId( window.csapGraphSettings.graph ) );
            }


            lastGraphName = graphName;
            let graphItems = graphs[ graphName ];
            let linesOnGraphArray = new Array(); // multiple series on each graph
            // Build the flot points

            _isNeedsLabel = true; // Full Label is used only on first item when stacked
            if ( isStackHosts && dataManager.getStackedGraph( graphName ) != undefined ) {
                _isNeedsLabel = false;
            }

            _colorCount = 0;
            let numberOfSeries = 0
            for ( let seriesName in graphItems ) {
                numberOfSeries++;
            }
            for ( let seriesName in graphItems ) {


                let optionalGraphs = getOptionalSeries( resourceGraph, seriesName, hostMetricsReport, $currentGraph );
                console.log( `Adding Graph: ${ graphName }  Series: ${ seriesName }, optionalGraphs: ${ optionalGraphs.length } ` );

                let seriesLabelSuffix = "";
                if ( optionalGraphs.length > 0 ) {
                    seriesLabelSuffix = hostMetricsReport.attributes.titles[ graphName ];
                }

                // support for remote collections: use label
                let collectionHost = null;
                //console.log( "Adding Graph: ", graphName, " Series: ", seriesName, " seriesLabel: ", seriesLabel );
                if ( hostMetricsReport.attributes.remoteCollect ) {

                    if ( hostMetricsReport.attributes.remoteCollect[ "default" ] ) {
                        // remote application collections
                        if ( isStackHosts ) {
                            collectionHost = hostMetricsReport.attributes.remoteCollect[ "default" ];
                        } else {
                            $( ".resourceGraphTitle .hostName" ).text( hostMetricsReport.attributes.remoteCollect[ "default" ] );
                        }
                    } else {
                        // remote JMX collections
                        let seriesLabel = graphItems[ seriesName ];
                        if ( hostMetricsReport.attributes.remoteCollect[ seriesLabel ] ) {
                            // service ports
                            if ( isStackHosts ) {
                                collectionHost = hostMetricsReport.attributes.remoteCollect[ seriesLabel ];
                            } else {
                                $( ".resourceGraphTitle .hostName" ).text( hostMetricsReport.attributes.remoteCollect[ seriesLabel ] );
                            }
                        }
                    }
                }
                if ( collectionHost != null && host != collectionHost ) {
                    seriesLabelSuffix += collectionHost;
                }

                console.log( `hostMetricsReport: ${ Object.keys( hostMetricsReport ) }` );
                _dom.csapDebug( `hostMetricsReport: `, hostMetricsReport );

                let graphSeries = buildSeriesForGraph( host, $currentGraph, dataManager, graphName,
                    seriesName, numberOfSeries, graphItems,
                    hostMetricsReport, flotTimeOffsetArray, seriesLabelSuffix );

                if ( graphSeries != null ) {
                    linesOnGraphArray.push( graphSeries );
                } else {
                    console.log( "Note:  graphSeries is null" );
                }

                let additionSeries = numberOfSeries;
                for ( let optionalGraph of optionalGraphs ) {
                    additionSeries++;

                    console.log( `optionalGraph: `, optionalGraph );
                    let optionalSeries = graphs[ optionalGraph.key ];
                    if ( optionalSeries ) {

                        graphSeries = buildSeriesForGraph(
                            host, $currentGraph, dataManager, graphName,
                            optionalGraph.seriesName, additionSeries,
                            graphs[ optionalGraph.key ],
                            hostMetricsReport, flotTimeOffsetArray, optionalGraph.title );

                        linesOnGraphArray.push( graphSeries );
                    } else {
                        console.warn( `Did not location graph for report ${ optionalGraph.key } ` );
                    }

                }

            }

            console.log( `*** linesOnGraphArray length: ${ linesOnGraphArray.length } ` );

            let graphCheckedId = getGraphCheckboxId( graphName, $currentGraph );
            if ( isStackHosts ) {

                if ( buildStackedPanels( resourceGraph, host, graphName, numGraphs,
                    $hostPlotContainer, flotTimeOffsetArray, originalTimestamps,
                    hostMetricsReport,
                    $newContainer, hostPlotInstances,
                    linesOnGraphArray, graphCheckedId ) ) {
                    console.log( "Failed to render data" );
                    onCompleteDeferred.resolve();
                    break;
                }

            } else {

                let title = getGraphTitle( graphName, hostMetricsReport );
                let titleHoverText = undefined;
                if ( hostMetricsReport.attributes.collectedFrom ) {
                    titleHoverText = "Collection source: " + hostMetricsReport.attributes.collectedFrom[ graphName ];
                }

                let builder = graphStatisticsBuilder( $currentGraph.parent().attr( "id" ), linesOnGraphArray, title );
                let $plotPanel = flotUtils.buildPlotPanel(
                    title, resourceGraph, numGraphs, host,
                    graphName, graphCheckedId, builder, titleHoverText );

                let $plotTargetDiv = flotUtils.addPlotAndLegend( resourceGraph, numGraphs, graphName, host, $plotPanel, $hostPlotContainer );

                // return ;

                let plotWidth = Math.round( $plotTargetDiv.outerWidth() );
                let plotHeight = Math.round( $plotTargetDiv.outerHeight() );
                if ( plotWidth == 0 ) {
                    //alert( `Failed to build graph: invalid div width: host: ${host}, graph: ${graphName}` ) ;
                    console.info( `Note: div has 0 width (div not visible?). host: ${ host }, graph: ${ graphName }` );
                    // continue anyway to resolve deffereds
                }


                let plotOptions = flotUtils.getPlotOptionsAndXaxis( $plotPanel, graphName, flotTimeOffsetArray, originalTimestamps, linesOnGraphArray,
                    $currentGraph, hostMetricsReport.attributes.sampleInterval, dataManager.isDataAutoSampled(), false );

                console.log( `\n\nPlotting: '${ title }' \n `
                    + `plotDiv id: ${ $plotTargetDiv.attr( "id" ) }, \n width: ${ plotWidth }, height: ${ plotHeight }` );

                console.log( `plotOptions: ${ Object.keys( plotOptions ) }` );
                _dom.csapDebug( `plotOptions: `, plotOptions );
                // memory leaks on console!!         
                //console.log( `Lines:`, linesOnGraphArray );
                //                return ;
                let plotObj = $.plot( $plotTargetDiv, linesOnGraphArray, plotOptions );

                flotUtils.configurePanelEvents( $currentGraph, $plotPanel, $newContainer, dataManager.getAllHostGraphs(), originalTimestamps );

                hostPlotInstances.push( plotObj );
                checkSampling( resourceGraph, flotTimeOffsetArray, linesOnGraphArray, $plotTargetDiv );

            }

            console.groupEnd();
        }


        //auto refresh keeps appending - so delete then add
        $( ".spacerForFloat", $hostPlotContainer ).remove();
        $hostPlotContainer.append( '<div class="spacerForFloat" ></div>' );

        $( ".spacerForFloat", $hostPlotContainer.parent() ).remove();
        $hostPlotContainer.parent().append( '<div class="spacerForFloat" ></div>' );


        if ( !isStackHosts ) {
            graphLayout.addContainer( $currentGraph, $hostPlotContainer );
            graphLayout.restore( resourceGraph, $hostPlotContainer );

            dataManager.updateHostGraphs( host, hostPlotInstances );

            onCompleteDeferred.resolve();

        } else {

            if ( dataManager.getStackedGraph( lastGraphName ) != undefined ) {
                let numberOfHostsSoFar = dataManager.getStackedGraph( lastGraphName ).length;
                if ( isIncrementalRendering || dataManager.getHostCount() == numberOfHostsSoFar ) {
                    console.log( `Stack graph rendering: '${ lastGraphName }', Incremental rendering: '${ isIncrementalRendering }'` );

                    graphLayout.addContainer( $currentGraph, $hostPlotContainer );
                    graphLayout.restore( resourceGraph, $hostPlotContainer );


                    dataManager.updateHostGraphs( host, hostPlotInstances );

                    onCompleteDeferred.resolve();

                } else {
                    console.log( `Stack graph rendering: '${ numberOfHostsSoFar }' of '${ dataManager.getHostCount() }' ` );
                }
            } else {
                if ( lastGraphName !== "" ) {
                    alertify.notify( `Warning: graph '${ lastGraphName }' is undefined ` );
                } else {
                    console.log( `lastGraphName is empty - assuming switch` );
                }
            }
        }

        // Doubled up to get back to top level on initial load. Schedule also adds
        console.groupEnd();

    }

    //
    // Note - this enables stacked graphs to each have a unique analytics view
    //
    function graphStatisticsBuilder( containerId, lines, title, builder ) {

        if ( builder == null ) {
            //console.log("graphStatisticsBuilder() statsBuilder - new Object") ;
            builder = new Object();
            builder.buildStatsPanelHtml = flotUtils.buildHostStatsPanel;
            builder.lines = new Array();
            builder.title = new Array();
        }
        builder.lines.push( lines );
        builder.title.push( title );
        builder.containerId = containerId;
        console.log( "graphStatisticsBuilder() statsBuilder length:" + builder.lines.length );
        return builder;
    }

    function getGraphTitle( graphName, metricsJson ) {
        let title = splitUpperCase( graphName );
        if ( graphName == "jmxHeartbeatMs" )
            title = "Service Heartbeat (ms)"
        //  console.log("getGraphTitle() " + graphName + " converted to: " + title) ;

        let optionalTitles = metricsJson.attributes.titles;
        if ( optionalTitles != undefined && optionalTitles[ graphName ] != undefined ) {
            title = optionalTitles[ graphName ];
        }
        return title;
    }


    function buildStackedPanels( resourceGraph, host, graphName, numGraphs,
                                 plotContainer, flotTimeOffsetArray, originalTimestamps, metricsJson,
                                 $newContainer, stackedHostPlots,
                                 linesOnGraphArray, checkId ) {

        let $currentGraph = resourceGraph.getCurrentGraph();
        let dataManager = resourceGraph.getDataManager();

        if ( dataManager.getStackedGraph( graphName ) == undefined ) {
            console.log( `buildStackedPanels(): currentGraph: ${ $currentGraph } creating array for  graph name${ graphName }` );
            //						+ JSON.stringify( linesOnGraphArray[0] , null,"\t") ) ;
            dataManager.initStackedGraph( graphName );
            ;
        }

        //default to the first series when stacking hosts
        let graphDataOnHost = linesOnGraphArray[ 0 ];

        let userSeriesSelection = $( "#isStackHosts" ).val();

        let isShowAllSeries = ( userSeriesSelection == "99" );
        console.log( `currentGraph: ${ $currentGraph.attr( "id" ) } isShowAllSeries: ${ isShowAllSeries } ` );

        $( "#show-mismatch-data", $currentGraph ).css( "visibility", "hidden" );

        for ( let seriesIndexToGraph = 0; seriesIndexToGraph < linesOnGraphArray.length; seriesIndexToGraph++ ) {

            if ( ( seriesIndexToGraph != userSeriesSelection )
                && !isShowAllSeries ) {
                continue;
            }

            graphDataOnHost = linesOnGraphArray[ seriesIndexToGraph ];


            if ( graphDataOnHost === undefined ) {
                alertify.csapWarning( "Unable to render graphs for multiple hosts<br><br> Verify that selected hosts all contain selected service."
                    + "<br><br> When selecting multiple hosts, cluster selection will ensure VMs have consistent services deployed." );

                return true;
            }

            // push the series onto the global Variable
            dataManager.pushStackedGraph( graphName, graphDataOnHost );

            let numberOfHostsForGraphSoFar = dataManager.getStackedGraphCount( graphName );

            // Replace label on host 
            let fullLabel = graphDataOnHost.label;

            let hostWithTagName = host;
            if ( utils.getHostTag( host ) ) {
                hostWithTagName = `${ utils.getHostTag( host ) }: ${ host }`;
            }
            if ( isShowAllSeries ) {
                graphDataOnHost.label += " " + hostWithTagName;
                numberOfHostsForGraphSoFar = numberOfHostsForGraphSoFar / linesOnGraphArray.length;
            } else {
                graphDataOnHost.label = hostWithTagName;
                ;
            }
            //

            // give each host a distinct color
            graphDataOnHost.color = CSAP_THEME_COLORS[ numberOfHostsForGraphSoFar ];
            graphDataOnHost.lines = { lineWidth: 2 };

            //console.log("Adding About for: " + host) ;
            let $graphHostContainer = $( "." + host + "Container", $currentGraph );
            // Draw once we have all responses
            //console.log( fullLabel + " numberOfHostsSoFar: " + numberOfHostsSoFar + " of " + dataManager.getHostCount())


            // add multiple hosts to statsBuilder array
            let statsBuilder = null;
            if ( numberOfHostsForGraphSoFar > 1 ) {
                statsBuilder = _statsForHostsGraphs[ graphName ];
                console.log( "statsBuilder retreived: ", statsBuilder );
            }


            _statsForHostsGraphs[ graphName ] = graphStatisticsBuilder(
                `${ host }-${ $currentGraph.parent().attr( "id" ) }`,
                // `${ host }-${ $currentGraph.parent().attr("id")}`,
                linesOnGraphArray,
                getGraphTitle( graphName, metricsJson ),
                statsBuilder );

            console.log( `buildStackedPanels: hostItems: ${ _statsForHostsGraphs[ graphName ].lines.length }`
                + `  numberOfHostsForGraphSoFar(): ${ numberOfHostsForGraphSoFar }`
                + `  dataManager.getHostCount(): ${ dataManager.getHostCount() }` );

            if ( dataManager.getHostCount() > numberOfHostsForGraphSoFar ) {
                // Stacked/restore seems to require
                $graphHostContainer.hide();
                console.log( `*** Waiting for more results before graphing` );

            } else {

                resourceGraph.setStackHostContainer( host );
                $graphHostContainer.show(); // customLayouts requires
                $( ".hostName", $graphHostContainer ).text( "Merged" );

                let title = getGraphTitle( graphName, metricsJson );

                console.log( `buildStackedPanels() graph type: ${ resourceGraph.getMetricType() }` );
                if ( ( resourceGraph.getMetricType() == "resource" ) && ( !isShowAllSeries ) ) {
                    title += ": " + fullLabel;
                }
                let $plotPanel = flotUtils.buildPlotPanel(
                    title, resourceGraph, numGraphs, host,
                    graphName, checkId, _statsForHostsGraphs[ graphName ] );

                let plotDiv = flotUtils.addPlotAndLegend( resourceGraph, numGraphs, graphName, host, $plotPanel, plotContainer );

                //					 console.log( "\n ==== drawGraph(): Stack Plotting: " +  graphName
                //								+ "  on host: " + host + ", " + curHostIndex
                //								+ " of " + _hostArray.length ) ;


                //console.log(`\n\n *** flotTimeOffsetArray` , flotTimeOffsetArray, `\n\n *** linesOnGraphArray`, linesOnGraphArray );

                let plotOptions = flotUtils.getPlotOptionsAndXaxis(
                    $plotPanel, graphName, flotTimeOffsetArray, originalTimestamps, linesOnGraphArray,
                    $currentGraph, metricsJson.attributes.sampleInterval, dataManager.isDataAutoSampled(), true );

                let graphData = dataManager.getStackedGraph( graphName );
                console.log( `buildStackedPanels() ${ graphName } options: `, plotOptions, ` data: `, graphData );
                let hostPointCount, hostPointName;

                for ( let hostGraph of graphData ) {
                    if ( !hostPointCount ) {
                        hostPointCount = hostGraph.data.length;
                        hostPointName = hostGraph.label;
                    }

                    if ( hostPointCount != hostGraph.data.length ) {
                        $( "#show-mismatch-data", $currentGraph ).css( "visibility", "visible" );
                        $( "#show-mismatch-data", $currentGraph ).off().click( function () {
                            let $message = jQuery( "<div/>", {} );

                            let $title = jQuery( "<div/>", {
                                id: "mis-match-message",
                                class: "stackLabel hquote"
                            } ).appendTo( $message );

                            jQuery( "<span/>", {
                                text: `Mismatch in data points collected: '${ graphName }'`
                            } ).appendTo( $title );

                            jQuery( "<div/>", {
                                text: `host: ${ hostGraph.label }  has ${ hostGraph.data.length } points`
                            } ).appendTo( $message );

                            jQuery( "<div/>", {
                                text: `host: ${ hostPointName }  has ${ hostPointCount } points`
                            } ).appendTo( $message );

                            let $notes = jQuery( "<ul/>", {} ).appendTo( $message );

                            jQuery( "<li/>", {
                                text: `use graph metrics to view collection counts (top right of panel next to maximize button)`
                            } ).appendTo( $notes );

                            jQuery( "<li/>", {
                                text: `graph options: enable 'line mode' enables gaps to be viewed on graph`
                            } ).appendTo( $notes );

                            jQuery( "<li/>", {
                                text: `graph options -> uncheck 'Show Mismatch' to disable this warning`
                            } ).appendTo( $notes );

                            alertify.csapWarning( $message );
                        } );
                    }
                }

                let plotObj = $.plot( plotDiv, graphData, plotOptions );

                flotUtils.configurePanelEvents( $currentGraph, $plotPanel, $newContainer, dataManager.getAllHostGraphs(), originalTimestamps );

                stackedHostPlots.push( plotObj );

                checkSampling( resourceGraph, flotTimeOffsetArray, linesOnGraphArray, plotDiv );

                let seriesLabel = jQuery( "<span/>", {
                    class: "stackLabel",
                    title: fullLabel,
                    html: fullLabel
                } ).appendTo( $( ".graphTitle .graphNotes", $plotPanel ) );

                let graphType = resourceGraph.getCurrentGraph().attr( "id" );
                console.log( "Stack graph resource type", graphType );
                if ( graphType != "resourceGraphsClone" ) {
                    let isAppData = $( ".triggerJmxCustom" ).length > 0;
                    if ( !isAppData ) {
                        //alert(`Updating applicationNameLabel: ${fullLabel} `) ;
                        //$( "#applicationNameLabel" ).text( "Service: " + fullLabel ).show();
                    }
                } else {
                    $( "#applicationNameLabel" ).hide();
                }


                if ( isShowAllSeries ) {
                    $plotPanel.css( "height", $plotPanel.outerHeight( true ) - 20 );
                }
            }
        }


        return false;
    }

    function checkSampling( resourceGraph, flotTimeOffsetArray, linesOnGraphArray, $plotDiv ) {

        let currentGraph = resourceGraph.getCurrentGraph();
        let dataManager = resourceGraph.getDataManager();

        let graphNotes = "";
        if ( dataManager.isDataAutoSampled() ) {

            graphNotes += "Reduced:" + flotTimeOffsetArray.length + " points to: "
                + linesOnGraphArray[ 0 ].data.length;

            // console.log("linesOnGraphArray: " + JSON.stringify(linesOnGraphArray[0], null, "\t"))
            //$(".zoomSelect").val(AUTO_SAMPLE_LIMIT).trigger("change") ;
        }

        if ( !$( '.padLatest', currentGraph ).prop( "checked" ) ) {
            graphNotes += " *Latest Excluded";
        }


        $( ".graphNotes", $plotDiv.parent() ).text( graphNotes );
        $plotDiv.attr( "title", graphNotes );

    }


    function buildSeriesForGraph( host, $GRAPH_INSTANCE, _dataManager,
                                  graphName, seriesName, numberOfSeries, graphItems,
                                  metricsJson, flotTimeOffsetArray, seriesLabelOverride ) {

        console.log( `${ host } buildSeriesForGraph() ${ $GRAPH_INSTANCE.attr( "id" ) }  graphName: ${ graphName } seriesName: ${ seriesName }` );

        _dom.csapDebug( `graphItems: `, graphItems );


        if ( window.csapGraphSettings != undefined ) {
            console.log( "csapGraphSettings: ", window.csapGraphSettings );
            if ( window.csapGraphSettings.type == "service" && graphItems[ seriesName ] != window.csapGraphSettings.service ) {
                console.log( `buildSeriesForGraph() - used to exit, but staying for stubbing` );
                // return null;
            }
        }

        let $checkBox = $( "#nonExistantItem" );
        // default: check using series name


        try {
            $checkBox = $( "#serviceCheckbox" + graphItems[ seriesName ], $GRAPH_INSTANCE );
        } catch ( error ) {
            console.log( `invalid selector: seriesName ${ seriesName }` );
            _dom.csapDebug( `graphItems: `, graphItems );
        }

        if ( !$checkBox.length ) {
            try {
                $checkBox = $( "#serviceCheckbox" + seriesName, $GRAPH_INSTANCE );
            } catch ( error ) {
                console.log( "invalid selector: ", graphItems[ seriesName ] );
            }
        }

        // console.log( "$checkBox.length: ", $checkBox.length )

        if ( $checkBox.length ) {
            if ( !$checkBox.is( ':checked' ) ) {

                console.log( `buildSeriesForGraph() : Not selected:  seriesName: ${ seriesName }`
                    + ` key:  ${ graphItems[ seriesName ] } ` );
                // console.log("Skipping: " + graphKey) ;
                return null;
            }

        }


        if ( seriesName == "totalCpu" ) {
            if ( !$( "#serviceCheckboxtotalCpu", $GRAPH_INSTANCE )
                .is( ':checked' ) ) {
                console.log( "Skipping: " + graphKey );
                return null;
            }
        }

        let graphDefn = new Object();

        if ( !isAttribute( seriesName ) ) {

            if ( metricsJson.data[ seriesName ] == null ) {
                //                alertify.notify( "No Data available for: " + seriesName );
                console.log( `No Data available for host: ${ host } series: ${ seriesName } ` );
                _dataManager.buildPoints(
                    flotTimeOffsetArray, -1, $GRAPH_INSTANCE,
                    graphLayout.getWidth( graphName, $GRAPH_INSTANCE ) );
                // continue ;
            } else {


                graphDefn.data = _dataManager.buildPoints(
                    flotTimeOffsetArray,
                    metricsJson.data[ seriesName ],
                    $GRAPH_INSTANCE,
                    graphLayout.getWidth( graphName, $GRAPH_INSTANCE ) );

                //console.log(`seriesName: ${ seriesName }`, graphDefn.data )

                // graph statistics 
                graphDefn.dataValues = metricsJson.data[ seriesName ];
                graphDefn.timeValues = flotTimeOffsetArray;
            }
        } else {
            // Hook for inserting a straight line in graph. useful for
            // establishing thresholds
            // attribute containes a "_"

            let specialIndex = seriesName.indexOf( "_" );
            if ( specialIndex == -1 ) {
                specialIndex = seriesName.indexOf( "." );
            }
            let attKey = seriesName.substring( specialIndex + 1 );

            let values = metricsJson.attributes[ attKey ];
            console.log( `buildSeriesForGraph(): building straight line: ${ values }` );

            graphDefn.data = _dataManager.buildPoints(
                flotTimeOffsetArray,
                values,
                $GRAPH_INSTANCE,
                graphLayout.getWidth( graphName, $GRAPH_INSTANCE ) );
        }
        // alertify.alert( JSON.stringify(graphDefn.data, null,"\t") ) ;

        let seriesLabel = graphItems[ seriesName ];
        console.log( `seriesLabel:  ${ seriesLabel }` );

        if ( seriesLabel == null ) {
            seriesLabel = seriesName;
            console.log( "Null name: " + seriesName );
        }

        if ( seriesLabel.startsWith( "mbean:" ) ) {

            let mbeanComposites = seriesLabel.split( ",," );

            if ( mbeanComposites.length == 2 ) {
                // strip off composite name
                seriesLabel = mbeanComposites[ 1 ];
            }
            console.log( `seriesLabel:`, seriesLabel )
            if ( seriesLabel.length > 40 ) {
                let mbeanPieces = seriesLabel.split( "," );
                if ( mbeanPieces.length > 2 ) {
                    seriesLabel = `${ mbeanPieces[ mbeanPieces.length - 1 ] } ${ mbeanPieces[ 0 ].replaceAll( "mbean:", "" ) }`;
                }
            }

        } else if ( seriesLabel.includes( "my_prom_app_id_" ) ) {

            seriesLabel = seriesLabel.replaceAll( "my_prom_app_id_", "" );

        } else if ( seriesLabel.includes( "csap." ) ) {

            seriesLabel = seriesLabel.replaceAll( "csap.", "" );

        }

        if ( seriesLabelOverride != "" ) {
            seriesLabel += ":" + seriesLabelOverride;
        }

        if ( seriesLabel.length > 50 ) {
            seriesLabel = `${ seriesLabel.substr( 0, 49 ) }...`;
        }

        graphDefn.label = seriesLabel;

        let lineWidth = $( ".flot-line-thickness", $GRAPH_INSTANCE ).val();

        if ( seriesName.includes( "attributes_" ) ) {
            graphDefn.color = "#60BD68";

        } else if ( seriesName == "totalCpu" ) {
            let blackColor = "#0F0F0F";
            graphDefn.color = blackColor;

            // } else if ( numberOfSeries == 1 ) {
            //     //graphDefn.color = "#5488ea";
            //     graphDefn.color = "green";
            //     //lineWidth = 4 ;
        } else {
            if ( _colorCount < CSAP_THEME_COLORS.length ) {
                graphDefn.color = CSAP_THEME_COLORS[ _colorCount++ ];
            }
        }
        graphDefn.lines = {
            lineWidth: lineWidth
        }


        //console.log("numItems on graph: " + graphItems[seriesName].length)


        return graphDefn;
    }

    // Used to build custom combinations of elements
    function getOptionalSeries( resourceGraph, seriesName, metricReport, $graphContainer ) {

        let seriesItems = new Array();

        let customView = resourceGraph.getSelectedCustomView();

        console.log( `Checking for graph report view` );
        if ( customView == null ) {
            return seriesItems;
        }

        _dom.logSection( `Building customView: ${ customView?.graphs }` );
        _dom.csapDebug( `customView: `, customView );

        let seriesType = seriesName;
        let seriesPrefix = "";


        if ( seriesName.includes( "_" ) ) {
            seriesType = seriesName.substring( 0, seriesName.indexOf( "_" ) )
            seriesPrefix = seriesName.substring( seriesName.indexOf( "_" ) )
        }

        // console.log("seriesType: " + seriesType + "  seriesPrefix: " +seriesPrefix + " viewSelected: " + jsonToString( customView ))


        if ( customView.graphMerged ) {
            let graphsToAdd = customView.graphMerged[ seriesType ];
            if ( Array.isArray( graphsToAdd ) ) {

                for ( let graphToAdd of graphsToAdd ) {
                    let optionalSeries = Object();

                    optionalSeries.seriesName = graphToAdd + seriesPrefix;
                    optionalSeries.title = metricReport.attributes.titles[ graphToAdd ];
                    optionalSeries.key = graphToAdd;

                    seriesItems.push( optionalSeries );
                }

            }
        }

        console.log( `seriesName: '${ seriesName }', seriesType: '${ seriesType }',\n   seriesPrefix: ${ seriesPrefix }` );


        _dom.csapDebug( `seriesItems: `, seriesItems );


        return seriesItems;
    }

    this.drawCsv = drawCsv;

    function drawCsv( graphs, metricsJson, plotContainer ) {
        for ( let graphName in graphs ) {
            let csvText = jQuery( '<textArea/>', {
                title: "Click to toggle size",
                rows: "5",
                cols: "50",
            } );
            let graphItems = graphs[ graphName ];
            let plotArray = new Array();
            let count = 0;
            let colorCount = 0;

            // Build the flot points
            let csvContent = "timeStamp,";
            for ( let graphKey in graphItems ) {
                csvContent += graphKey + ",";
            }
            for ( let i = 0; i < metricsJson.data.timeStamp.length; i++ ) {
                let reverseOrderIndex = metricsJson.data.timeStamp.length
                    - 1 - i;
                let t = new Date();
                t.setTime( metricsJson.data.timeStamp[ reverseOrderIndex ] );
                csvContent += "\n" + excelFormat( t ) + ",";
                for ( let graphKey in graphItems ) {
                    if ( !isAttribute( graphKey ) ) {
                        // console.log("Adding cvs content: " + graphKey) ;
                        csvContent += metricsJson.data[ graphKey ][ reverseOrderIndex ]
                            + ",";
                    } else {
                        let attKey = graphKey.substring( graphKey
                            .indexOf( "." ) + 1 );
                        // console.log( attKey ) ;
                        csvContent += metricsJson.attributes[ attKey ] + ",";
                    }
                }
            }

            csvText.val( csvContent );
            plotContainer.append( csvText );
        }
    }

    function isAttribute( graphKey ) {
        if ( graphKey.indexOf( "attributes_" ) != -1 )
            return true;
        if ( graphKey.indexOf( "attributes." ) != -1 )
            return true;

        return false;
    }

    function excelFormat( inDate ) {

        let returnDateTime = 25569.0 + ( ( inDate.getTime() - ( inDate
            .getTimezoneOffset() * 60 * 1000 ) ) / ( 1000 * 60 * 60 * 24 ) );
        return returnDateTime.toString().substr( 0, 20 );

    }

}