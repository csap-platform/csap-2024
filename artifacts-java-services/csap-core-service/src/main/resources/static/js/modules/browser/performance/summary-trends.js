// define( [ "browser/utils" ], function ( utils ) {
//     console.log( "Module loaded" ) ;



import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";
import utils from "../utils.js"

import _csapCommon from "../../utils/csap-common.js";

const events = summary_trends();
export default events;

// export default agent ;


function summary_trends() {

    _dom.logHead( "Module loaded" );



    const $performanceTab = $( "#performance-tab-content" );

    const USER_ACTIVITY_REPORT = "host";
    const HEALTH_REPORT = "custom/health";
    const LOGROTATE_REPORT = "custom/logRotate";
    const CORE_REPORT = "custom/core";
    const CORES_USED = "coresUsed";




    let isDisplayZoomHelpOnce = true;
    let slideIndex = new Object();

    const MAX_ATTEMPTS = 4;
    const TREND_CHECK_FOR_DATA_SECONDS = 3;

    let attemptCounts = new Object();
    let _activePlots = new Array();

    let _showFirstNotify = true;


    return {

        initialize: function () {
            initialize();
        },

        showHealth: function ( $container, numDays = 14, byHost = false, graphsPerScreen = 3 ) {

            return runSummaryForReport( $container, "Alerts", byHost, graphsPerScreen, HEALTH_REPORT, numDays, "UnHealthyCount" );

        },

        showUserActivity: function ( $container, numDays = 14, byHost = false, graphsPerScreen = 2 ) {

            return runSummaryForReport( $container, "User Activity", byHost, graphsPerScreen, USER_ACTIVITY_REPORT, numDays, "totActivity" );

        },

        showApplication: function ( $container, numberOfDays, byHost ) {
            loadApplicationDefinitions( $container, numberOfDays, byHost );
        },

        showTrend: function ( $container, theTitle, byHost, graphsPerScreen, report, numberOfDays, selectedColumn, serviceName, divideBy ) {
            return runSummaryForReport( $container, theTitle, byHost, graphsPerScreen, report, numberOfDays, selectedColumn, serviceName, divideBy );
        },

        cleanUp: function () {
            cleanUpPreviousTrends();
        }

    }

    function initialize() {

    }

    function loadApplicationDefinitions( $container, numberOfDays, byHost ) {
        $.getJSON(
            APP_BROWSER_URL + "/trend/definition" )
            .done( function ( trendDefinitions ) {

                console.log( `Application defined trends: ${ trendDefinitions.length }` );

                buildApplicationTrends( trendDefinitions, $container, numberOfDays, byHost );

            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "clearing alerts", errorThrown );
            } );
    }

    function cleanUpPreviousTrends() {

        console.log( `getConfiguredTrends() purging previous plots: ${ _activePlots.length }` );
        let numDestroyed = 0;
        for ( let plotInstance of _activePlots ) {

            //console.log( "deleteing plot " ) ;
            plotInstance.destroy();
            // delete plotInstance;
            numDestroyed++;

        }

        if ( numDestroyed > 0 ) {
            console.log( `Cleaned up: ${ numDestroyed }` );
            // return;
        }
    }

    function buildApplicationTrends( trendDefinitions, $container, numberOfDays, byHost ) {
        console.log( `getConfiguredTrends() building: ${ trendDefinitions.length } trends` );

        $container.empty();

        let containerId = `app-master-slide`;

        jQuery( '<div/>', {
            id: containerId,
            class: "trend-slide the-plot"
        } ).appendTo( $container );

        let containerIndex = 0;
        let trendCount = 0;
        for ( let trendDefinition of trendDefinitions ) {
            trendCount++;
            let containerId = `app-${ trendCount }`;

            let $panel = jQuery( '<div/>', {
                id: containerId,
                class: "the-plot"
            } ).appendTo( $container );

            //if ( trendDefinition.label != ) continue ;

            runSummaryForReport(
                $panel,
                trendDefinition.label,
                byHost,
                4,
                trendDefinition.report,
                numberOfDays,
                trendDefinition.metric,
                trendDefinition.serviceName,
                trendDefinition.divideBy );

        }

    }


    function runSummaryForReport(
        $container, theTitle, byHost,
        graphsPerScreen, report, numberOfDays,
        metricId, serviceName, divideBy ) {


        let retryFunction = function () {
            runSummaryForReport( $container, theTitle, byHost, graphsPerScreen, report, numberOfDays, metricId, serviceName, divideBy );
        }


        console.log( `runSummaryForReport()  ${ theTitle } report: ${ report }, metricId: ${ metricId }  serviceName: ${ serviceName } divideBy: ${ divideBy } ` );

        $container.empty();

        //        let attemptKey = report + metricId + serviceName ;
        let attemptKey = $container.attr( "id" );
        if ( !attemptCounts[ attemptKey ] ) {
            attemptCounts[ attemptKey ] = 0;
        }
        let currentAttempt = attemptCounts[ attemptKey ]++;
        console.log( ` attemptKey: ${ attemptKey } count: ${ currentAttempt }` );
        if ( currentAttempt > MAX_ATTEMPTS ) {
            $container.append( "Unable to load data" );
            attemptCounts[ attemptKey ] = 0;
            return;
        }


        let plotId = $container.attr( "id" ) + "-summary";
        //build container first


        let $plotContainer = jQuery( '<div/>', {
            id: plotId,
            // 	title: "Trending " + selectedColumn + ": Click on any data point for report",
            class: ""
        } );

        let displayHeight = $( window ).outerHeight( true )
            - $( "header" ).outerHeight( true ) - 300;
        let trendHeight = Math.floor( displayHeight / graphsPerScreen );
        if ( trendHeight < 100 ) {
            trendHeight = 100;
        }

        let $slideShow = $( "input.slide-show", $container.parent().parent() );
        let $trendSlide = $( ".trend-slide", $container.parent() );
        if ( ( $slideShow.length > 0 )
            && $slideShow.is( ":checked" ) ) {

            let trendWidth = Math.round( $performanceTab.outerWidth( true ) / 5 ) + 10;
            let originalWidth = trendWidth;
            //            if ( trendWidth < 200 ) {
            //                trendWidth = 200 ;
            //            }
            trendHeight = Math.round( trendWidth / 2 );
            $container.css( "display", "inline-block" );
            $container.css( "width", trendWidth + "px" );

            console.log( `slideshow mode: originalWidth: ${ originalWidth }, trendWidth ${ trendWidth }, trendHeight: ${ trendHeight }` );

            let slideHeight = Math.round( $( window ).outerHeight() / 2 );
            $trendSlide.css( "height", slideHeight );
            $trendSlide.show();

            // only clear when SELECTED slide is being redisplayed
            let pageKey = $container.parent().parent().attr( "id" );
            let resetSlideView = slideIndex[ pageKey ] == plotId;
            if ( resetSlideView ) {
                $trendSlide.empty();

                $trendSlide.append( jQuery( '<div/>', {
                    class: "loadingPanel loading-message",
                    text: `Building: ${ theTitle }`
                } ) )
            }
        } else {
            $trendSlide.hide();
            $container.css( "display", "" );
            $container.css( "width", "" );
        }
        $plotContainer.css( "height", trendHeight + "px" );

        $container.append( $plotContainer );

        let $loadingMessage = jQuery( '<div/>', {
            class: "loadingPanel",
            text: `Building: ${ theTitle }`
        } );

        $plotContainer.append( $loadingMessage );

        let $topSelect = $( "select.trend-top", $container.parent().parent() );
        let topFilter = $topSelect.val();
        let lowHostCount = 0;
        let topHostCount = 0;

        if ( byHost && ( topFilter != 0 ) ) {
            console.log( `Filter top graphs: ${ topFilter }` );
            $topSelect.css( "visibility", "visible" );
            if ( topFilter > 0 ) {
                topHostCount = topFilter;
            } else {
                lowHostCount = Math.abs( topFilter );
            }
            if ( topHostCount == 1 ) {
                lowHostCount = 1;
            }
        }

        // return;

        // event direct
        //        let reportUrl = utils.getMetricsUrl() + "../report/" + report ;
        //        let reportParameters = {
        //            appId: utils.getAppId(),
        //            perVm: byHost,
        //            life: utils.getEnvironment(),
        //            numDays: numberOfDays,
        //            project: utils.getActiveProject(),
        //            metricsId: selectedColumn
        //        } ;

        // event cached via admin - ensures a non cors call
        let reportUrl = utils.getTrendUrl();

        let environment = utils.getActiveEnvironment();
        let appId = utils.getAppId();
        let project = utils.getActiveProject( false );
        if ( $( "input.show-by-env", $performanceTab ).is( ":checked" ) ) {
            environment = null;
            $topSelect.css( "visibility", "visible" );
            if ( utils.adminHost() == "localhost" ) {
                if ( _showFirstNotify ) {
                    alertify.notify( `Debug graphs via adminhost ${ utils.adminHost() }` );
                    _showFirstNotify = false;
                }
                project = null;
                appId = "csap-performance-app";
            }
        }
        let reportParameters = {
            report: report,
            metricsId: metricId,
            appId: appId,
            project: project,
            life: environment,
            trending: 1,
            perVm: byHost,
            allVmTotal: !byHost,
            serviceName: serviceName,
            trendDivide: divideBy,
            numDays: numberOfDays,
            top: topHostCount,
            low: lowHostCount
        };

        if ( utils.getActiveEnvironment().includes( "desktop" ) ) {
            console.warn( `DESKTOP TEST: swapping appIds and others` );
            reportParameters.appId = "csap-performance-app";
            if ( reportParameters.life ) {
                reportParameters.life = "oms-perf-01";
            }
        }



        if ( report == USER_ACTIVITY_REPORT ) {
            $.extend( reportParameters, {
                "allVmTotal": true
            } );
        }


        let viewLoadedPromise = _net.httpGet( reportUrl, reportParameters );

        viewLoadedPromise
            .then( reportResponse => {

                _dom.logArrow( `reportResponse: ${ reportResponse.length }` );
                buildSummaryReportTrend(
                    byHost, retryFunction, theTitle,
                    attemptKey, metricId, plotId,
                    report, serviceName, reportResponse );

            } )
            .catch( ( e ) => {
                console.warn( e );
                $( "#" + plotId ).text( "Data not available. Ensure the latest agent is installed." );
                //alertify.notify( "Failed getting trending report for: " + report );

            } );


        return viewLoadedPromise;

        // $.getJSON( reportUrl,
        //     reportParameters )

        //     .done( function ( reportResponse ) {
        //         buildSummaryReportTrend(
        //             byHost, retryFunction, theTitle,
        //             attemptKey, metricId, plotId,
        //             report, serviceName, reportResponse );

        //     } )

        //     .fail( function ( jqXHR, textStatus, errorThrown ) {
        //         $( "#" + plotId ).text( "Data not available. Ensure the latest agent is installed." );
        //         alertify.notify( "Failed getting trending report for: " + report );

        //     } );


    }


    function buildSummaryReportTrend(
        byHost, retryFunction, theTitle,
        attemptKey, selectedColumn, containerId,
        report, serviceName, reportData ) {

        console.log( `buildSummaryReportTrend() : ${ theTitle }` );


        let $plotContainer = $( "#" + containerId );

        $plotContainer.empty();

        if ( ( reportData.lastUpdateMs == 0 ) ) {

            console.log( "buildSummaryReportTrend() no data available, scheduling a refresh" );

            $plotContainer.addClass( "flex-centered" );

            let $loadingMessage = jQuery( '<div/>', {
                class: "loadingPanel loading-message",
                html: "loading: " + theTitle
            } )

            // vmid is old
            let $refreshDiv = jQuery( '<div/>', {
                class: ""
            } ).append( $loadingMessage );

            $plotContainer.html( $refreshDiv );
            setTimeout( function () {
                retryFunction();
            }, TREND_CHECK_FOR_DATA_SECONDS * 1000 );
            return;
        }

        attemptCounts[ attemptKey ] = 0;

        let checkForData = reportData.data[ 0 ];
        if ( checkForData == undefined || checkForData[ "date" ] == undefined ) {
            let message = `Unable to retrieve data for graph: ${ theTitle }`;
            $plotContainer.text( message ).css( "height", "2em" );
            console.log( `${ message }. Report: ${ report }`, reportData );
            return;
        }

        // let seriesToPlot = [  currData[  selectedColumn]  ];
        let graphValues = new Array();
        let graphLabels = new Array();


        let timeFormat = '%b %#d'; // month day
        let showMarker = true;

        let sortKey = "host";
        if ( reportData.data[ 0 ].serviceName ) {
            sortKey = "serviceName";
        }
        let sortedReports = ( reportData.data ).sort( ( a, b ) => ( a[ sortKey ] > b[ sortKey ] ) ? 1 : -1 );

        for ( let intervalReport of sortedReports ) {

            //console.log( "buildComputeTrends() Series size: " + currData[  "date"].length) ;

            let timePeriods = intervalReport[ "date" ];
            if ( intervalReport[ "timeStamp" ] ) {
                timePeriods = intervalReport[ "timeStamp" ];
                timeFormat = '%H:%M'  // {formatString:'%H:%M:%S'} 
            }
            if ( timePeriods.length > 20 ) {
                showMarker = false;
            }
            graphValues.push( buildSeries( timePeriods, intervalReport[ selectedColumn ] ) );
            let seriesLabel = intervalReport[ "lifecycle" ];


            if ( intervalReport[ "host" ] != undefined ) {
                //curLabel += ":" + currData["host"] ;
                seriesLabel = intervalReport[ "host" ];

                if ( utils.getHostTag( seriesLabel ) ) {
                    seriesLabel = utils.getHostTag( seriesLabel );
                }
                if ( intervalReport[ "lifecycle" ]
                    && $( "input.show-by-env", $performanceTab ).is( ":checked" ) ) {
                    seriesLabel += " " + intervalReport[ "lifecycle" ];
                }
            } else if ( intervalReport[ "serviceName" ] != undefined
                && theTitle !== `Host Response (ms)` ) {
                //curLabel += ":" + currData["host"] ;
                seriesLabel = intervalReport[ "serviceName" ];

            }


            graphLabels.push( seriesLabel );
        }

        _csapCommon.padMissingPointsInArray( graphValues );

        let $plotParent = $plotContainer.parent();
        let $slideShow = $( "input.slide-show", $plotParent.parent().parent() );
        let isSlideShowMode = ( $slideShow.length > 0 ) && $slideShow.is( ":checked" );

        let plotSettings = buildPlotSettings(
            graphValues, graphLabels,
            theTitle, byHost,
            showMarker, timeFormat,
            report,
            $plotParent, isSlideShowMode );

        //
        // http://www.jqplot.com/docs/files/jqPlotOptions-txt.html
        //

        console.log( `plotting: `, graphValues) ;
        let thePlot = $.jqplot( containerId, graphValues, plotSettings );
        _activePlots.push( thePlot );

        let $jqPlotTitle = $( ".jqplot-title", $plotParent );
        let pageKey = $plotParent.parent().parent().attr( "id" );
        $jqPlotTitle.off().click( function () {

            slideIndex[ pageKey ] = $( this ).parent().attr( "id" );
            console.log( `selected slide: ${ pageKey }: ${ slideIndex[ pageKey ] } `, slideIndex );
            $slideShow
                .prop( "checked", true )
                .trigger( "change" );
        } );


        if ( isSlideShowMode ) {


            let $trendSlide = $( ".trend-slide", $plotParent.parent() );
            let plotSlideId = $trendSlide.attr( "id" ) + "-plot";

            if ( !slideIndex[ pageKey ] ) {
                slideIndex[ pageKey ] = containerId;
            }

            console.log( `Checking ${ containerId }`, slideIndex );
            if ( slideIndex[ pageKey ] == containerId ) {
                console.log( `slideId: ${ plotSlideId }` );
                $trendSlide.empty();
                let $slidePlot = jQuery( '<div/>', { id: plotSlideId } );
                $slidePlot.appendTo( $trendSlide );

                let isShowLegend = ( graphLabels.length > 1 );
                plotSettings.legend.show = isShowLegend;
                let gridSize = ""; // default 
                if ( !isShowLegend ) {
                    gridSize = "auto 1em";
                }
                $trendSlide.css( "grid-template-columns", gridSize );
                let theslide = $.jqplot( plotSlideId, graphValues, plotSettings );
                _activePlots.push( theslide );

                $( "table.jqplot-table-legend", $trendSlide )
                    .css( "left", "0" )
                    .css( "top", "0" )
                    .appendTo( $trendSlide );

                registerTrendEvents( byHost, graphLabels, $trendSlide, report, selectedColumn, serviceName );


                let $plotButtons = $( ".jqplot-title span.show-slide", $trendSlide );
                let $closeButton = jQuery( '<button/>', {
                    class: "csap-icon csap-remove",
                    title: "Close slide show mode"
                } )
                    .css( "margin-left", "4px" );
                $plotButtons.append( $closeButton );

                $closeButton.off().click( function () {
                    $slideShow
                        .prop( "checked", false )
                        .trigger( "change" );
                } );

                $( ".jqplot-title span.show-slide", $trendSlide ).removeClass( "show-slide" );
            }
        } else {
            registerTrendEvents( byHost, graphLabels, $plotParent, report, selectedColumn, serviceName );
        }

        $( "table.jqplot-table-legend", $plotContainer )
            .css( "left", "0" )
            .css( "top", "0" )
            .css( "height", Math.round( $plotContainer.outerHeight() ) )
            .appendTo( $plotContainer.parent() );

    }

    function buildPlotSettings(
        graphValues, graphLabels,
        theTitle, byHost,
        showMarker, timeFormat,
        report,
        $plotParent, isSlideShowMode ) {

        let yAxisSettings = { min: calculateGraphMin( graphValues ) };

        let $topSelect = $( "select.trend-top", $plotParent.parent().parent() );
        let topFilter = $topSelect.val();
        let filterCount = Math.abs( $topSelect.val() );

        if ( filterCount == 1 ) {
            filterCount = 2; // handle ui of top/low 1
        }

        if ( ( topFilter != 0 )
            && ( graphValues.length > filterCount ) ) {

            console.log( `Client Filter top graphs: ${ topFilter }` );
            let graphSums = new Array();
            for ( let graph of graphValues ) {
                // time, value array
                //console.log( `graph:`, graph ) ;

                //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/reduce
                //let graphSum = graph.reduce((a, b) => a[1] + b[1], 0) ;
                let graphSum = graph.reduce( function ( accumulator, currentValue, currentIndex, array ) {
                    //console.log(`accumulator ${accumulator}, currentValue: ${currentValue}`) ;
                    let sum = 0;
                    if ( accumulator[ 1 ] )
                        sum = accumulator[ 1 ];
                    return sum + currentValue[ 1 ];
                } )
                //console.log( `graphSum ${ graphSum }` ) ;
                graphSums.push( graphSum );
            }

            let topGraphs = new Array();
            let topLabels = new Array();

            for ( let i = 0; ( i < filterCount ) && ( i < graphValues.length ); i++ ) {
                // js spread operator
                let indexOfMaxValue = graphSums.indexOf( Math.max( ...graphSums ) );
                let indexOfMinValue = graphSums.indexOf( Math.min( ...graphSums ) );
                console.log( `Max index: ${ indexOfMaxValue }, Min index: ${ indexOfMinValue }` );

                if ( topFilter > 0 ) {
                    // perform top operation
                    graphSums[ indexOfMaxValue ] = 0;
                    topGraphs.push( graphValues[ indexOfMaxValue ] );
                    topLabels.push( graphLabels[ indexOfMaxValue ] );
                }
                if ( topFilter <= 1 ) {
                    // perform low operation
                    graphSums[ indexOfMinValue ] = graphSums[ indexOfMaxValue ] + 1; // exclude from next iterations
                    topGraphs.push( graphValues[ indexOfMinValue ] );
                    topLabels.push( graphLabels[ indexOfMinValue ] );
                }

                if ( topFilter == 1 ) {
                    // special case for top/low 1
                    break;
                }
            }

            // update reference for plot operation
            graphValues.length = 0;
            Array.prototype.push.apply( graphValues, topGraphs );

            // this is only used in this function - straight assignment
            graphLabels = topLabels;
        }

        if ( $( "#isZeroGraph" ).is( ':checked' ) ) {
            yAxisSettings = { 
                min: 0 
            };
        }

        // if ( theTitle.toLowerCase().contains("millions") ) {

        //     yAxisSettings.tickOptions = {
        //             formatString: "%#.2f"
        //     };

        // }

        console.log(`theTitle: ${theTitle} yAxisSettings: `, yAxisSettings) 

        let fillGraphs = $( "input.stack-report-lines", $performanceTab ).is( ":checked" );
        let stack = $( "input.stack-report-lines", $performanceTab ).is( ":checked" ); 

        let plotMessage = "";
        if ( report == HEALTH_REPORT || USER_ACTIVITY_REPORT == report ) {
            plotMessage = `<div class="trendOpen">Click to view daily report</div>`
        }

        let showLegend = false;

        $plotParent.css( "display", "block" );



        if ( isSlideShowMode ) {
            $plotParent.css( "display", "inline-block" );

            //        } else if ( byHost ) {
        } else if ( graphLabels.length > 1 ) {
            showLegend = true;
            $plotParent.css( "display", "grid" );
        }

        theTitle = `<span class="show-slide" title="${ theTitle }">${ theTitle }</span>`;
        let plotSettings = {
            title: theTitle,
            seriesColors: CSAP_THEME_COLORS,
            stackSeries: stack,
            seriesDefaults: {
                fill: fillGraphs,
                fillAndStroke: true,
                fillAlpha: 0.5,
                pointLabels: { //http://www.jqplot.com/docs/files/plugins/jqplot-pointLabels-js.html
                    show: false,
                    ypadding: 0
                },
                rendererOptions: {
                    smooth: true
                },
                markerOptions: {
                    show: showMarker,
                    size: 4,
                    color: "black"
                }
            },
            cursor: {
                show: false,
                tooltipLocation: 'nw',
                zoom: true
            },

            axesDefaults: {
                tickOptions: { showGridline: false }
            },
            axes: {
                xaxis: {
                    // http://www.jqplot.com/tests/date-axes.php
                    renderer: $.jqplot.DateAxisRenderer,
                    tickOptions: { formatString: timeFormat }
                },
                yaxis: yAxisSettings

            },
            // http://www.jqplot.com/docs/files/plugins/jqplot-highlighter-js.html
            highlighter: {
                show: true,
                showMarker: true,
                sizeAdjust: 20,
                tooltipLocation: "n",
                //tooltipAxes: "y",
                tooltipOffset: 5,
                tooltipContentEditor: function ( str, seriesIndex, pointIndex, plot ) {
                    //the str is the ready string from tooltipFormatString
                    //depending on how do you give the series to the chart you can use plot.legend.labels[seriesIndex] or plot.series[seriesIndex].label
                    return '<span class=tip-legend>' + plot.legend.labels[ seriesIndex ] + ': </span>' + str;
                }, // %#.2f
                formatString: `%s : <span class=summary-value>%s</span> ${ plotMessage } `
            },
            legend: {
                labels: graphLabels,
                placement: "outside", // outside outsideGrid insideGrid
                show: showLegend
            }
        }

        return plotSettings;
    }

    function registerTrendEvents( byHost, graphLabels, $trendContainer, report, selectedColumn, serviceName ) {

        console.log( `registerTrendEvents() report: ${ report } metric: ${ selectedColumn }` );

        let trendEventsFunction = function () {
            //console.log( "rebinding events - because cursor zooms will loose them" );
            let $jqPlotTitle = $( ".jqplot-title", $trendContainer );

            let $buttonContainer = jQuery( '<span/>', { class: "b-container" } );
            $jqPlotTitle.append( $buttonContainer );

            //$( "button.csap-button-icon", $jqPlotTitle ).remove() ;

            let $viewButton = jQuery( '<button/>', {
                class: "csap-icon csap-graph",
                title: "Open in Analytics Portal"
            } );
            $buttonContainer.append( $viewButton );

            $viewButton.off().click( function () {
                let launchUrl = `${ ANALYTICS_URL }&project=${ utils.getActiveProject() }&appId=${ utils.getAppId() }&`;


                if ( selectedColumn ) {
                    let metric = selectedColumn;
                    if ( report && report.startsWith( "custom" ) ) {
                        metric = "totalUsrCpu,totalSysCpu";
                    }
                    if ( metric === "totalUsrCpu,totalSysCpu" ) {
                        metric = "totalUsrCpu__totalSysCpu"
                    }
                    console.log( `metric: '${ metric }'` );
                    launchUrl += `&metric=${ metric }`;
                }

                if ( report ) {

                    let targetTrend = report;
                    if ( targetTrend == "host" ) {
                        targetTrend = "tableHost";

                    } else if ( targetTrend.startsWith( "custom" ) ) {
                        targetTrend = "tableHost";

                    } else if ( serviceName ) {
                        targetTrend += "/detail";
                    }
                    launchUrl += "&report=" + targetTrend;
                }

                if ( serviceName != undefined ) {
                    let targetService = serviceName;
                    if ( targetService.includes( "," ) ) {
                        console.log( "Picking first service in set: " + targetService );
                        let services = targetService.split( "," );
                        targetService = services[ 0 ];
                    }
                    launchUrl += "&service=" + targetService;
                }


                console.log( `launching: ${ launchUrl }` );
                launchUrl = utils.updateUrlIfDesktop ( launchUrl ) ;
                // if ( utils.getActiveEnvironment().contains( "desktop" ) ) {
                //     launchUrl = launchUrl.replaceAll( "8021", "8022" ).replaceAll( "desktop-dev-01", "oms-perf-01" ).replaceAll( "csap-desktop", "csap-performance-app" );
                //     console.warn( `DESKTOP TEST: launching: ${ launchUrl }` );
                // }

                utils.launch( launchUrl );
            } );

        };

        trendEventsFunction();


        if ( report === HEALTH_REPORT || USER_ACTIVITY_REPORT === report ) {
            $trendContainer.unbind( 'jqplotDataClick' );
            $trendContainer.on(
                'jqplotDataClick',
                function ( ev, seriesIndex, pointIndex, data ) {

                    console.log( `selected: ${ report }: seriesIndex: ${ seriesIndex } ` +
                        "pointIndex: " + pointIndex + " graph: " + data );

                    //                        if ( report == HEALTH_REPORT || report == LOGROTATE_REPORT || report == CORE_REPORT ) {

                    let cat = "/csap/reports/health";
                    if ( report == LOGROTATE_REPORT ) {
                        cat = "/csap/reports/logRotate";
                    } else if ( report == CORE_REPORT ) {
                        cat = "/csap/reports/host/daily";
                    } else if ( report == USER_ACTIVITY_REPORT ) {
                        cat = "/csap/ui/*";
                    }

                    //let lifeHostArray = ( seriesLabel[seriesIndex] ).split( ":" ) ;
                    let hostString = "";
                    if ( byHost ) {
                        //                                hostString = "&hostName=" + lifeHostArray[1] ;
                        hostString = "&hostName=" + graphLabels[ seriesIndex ];
                    }

                    let reportUrl = utils.getMetricsUrl()
                        + `../..?life=${ utils.getActiveEnvironment() }`
                        + hostString
                        + "&category=" + cat
                        + "&date=" + data[ 0 ]
                        + "&project=" + utils.getActiveProject() + "&appId=" + utils.getAppId();
                    openWindowSafely( reportUrl, "_blank" );

                    //                        } else if ( USER_ACTIVITY_REPORT == report ) {
                    //                            //let lifeHostArray = ( seriesLabel[seriesIndex] ).split( ":" ) ;
                    //
                    //                            let reportUrl = utils.getAnalyticsUrl() + "&report=tableUser&"
                    //                                    + "&appId=" + utils.getAppId() + "&project=" + utils.getActiveProject() ;
                    //                            openWindowSafely( reportUrl, "_blank" ) ;
                    //                        }

                } );

        }

        //        // reregister on redraws
        //        $( '#' + containerId ).off() ;
        //        $( '#' + containerId ).bind( 'jqplotClick',
        //                function ( ev, seriesIndex, pointIndex, data ) {
        //                    if ( isDisplayZoomHelpOnce ) {
        //                        isDisplayZoomHelpOnce = false ;
        //                        alertify.notify( "double click to reset zoom to original" ) ;
        //                    }
        //                    // graph re-rendering deletes the title and button - so redraw
        //                    trendEventsFunction() ;
        //                }
        //        ) ;
    }

    function calculateGraphMin( seriesToPlot ) {
        // set the min
        let lowestValue = -999;

        for ( let i = 0; i < seriesToPlot.length; i++ ) {
            let lineSeries = seriesToPlot[ i ];
            for ( let j = 0; j < lineSeries.length; j++ ) {
                let pointsOnLine = lineSeries[ j ];
                let current = pointsOnLine[ 1 ];
                if ( lowestValue == -999 ) {
                    lowestValue = current;
                } else if ( current < lowestValue ) {
                    lowestValue = current;
                }
            }
        }
        //console.log( "theMinForY pre lower: ", lowestValue) ;
        let loweredBy80Percent = lowestValue * 0.8;
        if ( loweredBy80Percent < 5 ) {
            //            loweredBy80Percent = parseFloat( loweredBy80Percent.toFixed( 1 ) );
            loweredBy80Percent = Math.floor( loweredBy80Percent * 100 ) / 100;

        } else {
            loweredBy80Percent = Math.floor( loweredBy80Percent );
        }



        //console.debug( "theMinForY: " + lowestValue + " loweredBy80Percent: ", loweredBy80Percent ) ;
        return loweredBy80Percent;
    }

    function buildSeries( xArray, yArray ) {
        var graphPoints = new Array();

        for ( var i = 0; i < xArray.length; i++ ) {
            var resourcePoint = new Array();
            resourcePoint.push( xArray[ i ] );

            var metricVal = yArray[ i ];
            if ( $( "#nomalizeContainer" ).is( ":visible" ) ) {
                metricVal = metricVal * $( "#nomalizeContainer select" ).val();
            }

            resourcePoint.push( metricVal );

            graphPoints.push( resourcePoint );
        }

        xArray = null;
        yArray = null;
        // console.log( "Points: " + JSON.stringify(graphPoints ) );

        return graphPoints;
    }



}