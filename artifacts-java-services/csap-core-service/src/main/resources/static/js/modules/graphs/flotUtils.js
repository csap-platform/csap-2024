// define( [ "./graphLayout", "mathjs" ], function ( graphLayout, mathjs ) {

//     console.log( "Module loaded: graphPackage/flotUtils" ) ;


import _dom from "../utils/dom-utils.js";
import _dialogs from "../utils/dialog-utils.js";
import graphLayout from "./graphLayout.js"


import _csapCommon from "../utils/csap-common.js";


import utils from "../browser/utils.js"

const hostGraphs = graphs_host();

export default hostGraphs

function graphs_host() {

    _dom.logHead( "Module loaded2" );


    let hackLengthForTesting = -1;
    let months=["Jan", "Feb", "Mar", "Apr", "May", "June", "July", "Aug", "Sept", "Oct", "Nov", "Dec"]

    let bindTimer, tipTimer;

    let _lastRange = {};
    let _snapshot = {};
    let _latestStats = {};

    let _captureAllStats = false;
    let _captureAllCount;
    let _latestHost, _latestDate;

    let _mergedSeries, _snapMergedSeries;

    let STATS_FORMAT = { notation: 'fixed', precision: 2 };


    return {
        //
        buildHostStatsPanel: function ( graphContainerId, $statsPanel, graphTitle ) {
            return buildHostStatsPanel( graphContainerId, $statsPanel, graphTitle );
        },
        //
        buildPlotPanel: function ( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder, titleTip ) {
            return buildPlotPanel( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder, titleTip )
        },
        //
        getPlotOptionsAndXaxis: function ( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost ) {
            return getPlotOptionsAndXaxis( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost );
        },
        //
        addPlotAndLegend: function ( resourceGraph, numGraphs, graphName, host, plotPanel, plotContainer ) {
            return addPlotAndLegend( resourceGraph, numGraphs, graphName, host, plotPanel, plotContainer )
        },
        //
        configurePanelEvents: function ( $currentGraph, $targetPanel, $panelContainer, graphsArray, originalTimestamps ) {
            configurePanelEvents( $currentGraph, $targetPanel, $panelContainer, graphsArray, originalTimestamps )
        }
    }




    /**
     * http://www.flotcharts.org/, http://flot.googlecode.com/svn/trunk/API.txt
     * 
     * @param newHostContainerJQ
     * @param graphMap
     */

    function buildPlotPanel( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder, titleTip ) {

        //console.log( "title: " + title );

        let $graphContainer = resourceGraph.getCurrentGraph();


        $( '.take-snapshot', $graphContainer ).off().click( function () {

            performFullSnapshot( $graphContainer );

        } );




        let $plotPanel = jQuery( '<div/>', {
            id: host + "_plot_" + graphName,
            class: "plotPanel " + graphName,
            "data-graphname": graphName
        } );

        let titleHoverText = "Click and drag graph title to re order";
        if ( titleTip ) {
            titleHoverText = titleTip + "\n" + titleHoverText;
        }
        let $graphTitleBar = jQuery( '<div/>', {
            id: "Plot" + host + graphName + "title",
            title: titleHoverText,
            class: "graphTitle"
        } );

        jQuery( '<span/>', {
            html: title,
            class: "name"
        } ).appendTo( $graphTitleBar );



        $plotPanel.append( $graphTitleBar );

        jQuery( '<label/>', {
            text: "",
            class: "graphNotes"
        } ).appendTo( $graphTitleBar );


        let $panelControls = jQuery( '<span/>', {
            text: "",
            class: "panel-controls"
        } ).appendTo( $graphTitleBar );

        jQuery( '<button/>', {
            class: "plotAboutButton csap-icon csap-info-black",
            title: "View Summary Information about data points"
        } )
            .off().click( function () { showStatsDialog( statsBuilder, title, graphName ) } )
            .data( "graphType", checkId )
            .appendTo( $panelControls );

        jQuery( '<button/>', {
            class: "plotMinMaxButton csap-icon csap-window",
            title: "clock to toggle size"
        } )
            .off().click( function () {
                console.log( `minMaxButton clicked` );

                if ( !graphLayout.isAGraphMaximized( $graphContainer ) ) {
                    $( ".graphCheckboxes input.graphs", $graphContainer ).prop(
                        "checked", false );
                    $( "#" + $( this ).data( "graphType" ), ".graphCheckboxes" ).prop( "checked",
                        true );
                } else {

                    $( ".graphCheckboxes input.graphs", $graphContainer ).prop(
                        "checked", true );
                }
                resourceGraph.reDraw();
            } )
            .data( "graphType", checkId )
            .appendTo( $panelControls );

        return $plotPanel;
    }

    function performFullSnapshot( $graphContainer ) {

        utils.loading( `Taking Snapshot` );


        _captureAllStats = true;
        _captureAllCount = 0;

        let numberToWaitFor = $( "button.plotAboutButton", $graphContainer ).length;

        setTimeout( function () {

            $( "button.plotAboutButton", $graphContainer ).each( function () {

                let $aboutPlotButton = $( this );


                $aboutPlotButton.click();

            } );
        }, 100 )

        let interval = 500;
        const checkForComplete = function () {

            if ( numberToWaitFor <= _captureAllCount ) {
                utils.loadingComplete();
                _captureAllStats = false;
            } else {
                setTimeout( checkForComplete, interval );
            }
        }
        checkForComplete();

    }


    function showStatsDialog( statsBuilder, title, graphName ) {
        console.log( `plotAboutButton clicked: title: ${ title } graphName: ${ graphName }` );

        _mergedSeries = new Array();

        let $dialogContainer = jQuery( '<div/>', {} );

        let $statsDialog = jQuery( '<div/>', {
            class: "stats-dialog"
        } ).appendTo( $dialogContainer );


        let $header = jQuery( '<div/>', {
            class: "flex-container"
        } ).appendTo( $statsDialog );


        let $title = jQuery( '<span/>', {
            class: "stats-title",
            html: title
        } ).appendTo( $header );

        let $labelContainer = jQuery( '<span/>', { class: "stats-labels" } ).appendTo( $title );
        $labelContainer.css( `font-size`, "9pt") ;

        jQuery( '<input/>', {
            id: "update-current-label",
            placeholder: `current label`
        } ).appendTo( $labelContainer );

        jQuery( '<input/>', {
            id: "update-baseline-label",
            placeholder: `baseline label`
        } ).appendTo( $labelContainer );



        let $mergeButton = jQuery( '<button/>', {
            id: `merge-series-button`,
            class: "csap-button-icon csap-folder",
            "data-graph": title,
            text: "Build Aggregate Report"
        } )
            .css( "padding-left", "33px" ).appendTo( $header );



        jQuery( '<span/>', {
            text: `id: ${ graphName }`
        } ).appendTo( $header );




        for ( let i = 0; i < statsBuilder.lines.length; i++ ) {

            let seriesOnGraph = statsBuilder.lines[ i ] ;
            console.log( `graphName: ${graphName} seriesOnGraph:`, seriesOnGraph.length) ;

            let statsPanelHtml = statsBuilder.buildStatsPanelHtml(
                statsBuilder.containerId,
                seriesOnGraph,
                statsBuilder.title[ i ] );

            jQuery( '<div/>', {
                class: "stats-panels",
                html: statsPanelHtml
            } ).appendTo( $statsDialog );

        }
        // console.log("$aboutButton: " + content);
        $( ".graph-hover-container" ).hide();
        //alertify.alert( "Summary Statistics: " + title, content );
        //alertify.csapInfo( content );
        if ( !alertify.plotMetrics ) {

            let configuration = {
                onresize: statsResize,
                content: $dialogContainer.html()
            }
            let csapDialogFactory = _dialogs.dialog_factory_builder( configuration );

            alertify.dialog( 'plotMetrics', csapDialogFactory, false, 'alert' );
        }

        if ( _captureAllStats ) {
            // this captured the stats, now take the snapshot

        } else {
            let instance = alertify.plotMetrics( $dialogContainer.html() );


            let registerAfterDomUpdatedFunction = function () {

                let $statsDialog = $( `.stats-dialog` );


                $( "#update-current-label", $statsDialog ).off().change( function() {
                    $( ".current-label", $statsDialog ).text( $(this).val() ) ;
                } );
                $( "#update-baseline-label", $statsDialog ).off().change( function() {
                    $( ".baseline-label", $statsDialog ).text( $(this).val() ) ;
                } );


                let registerCopyFunction = function () {

                    let $activeTable = $( $( this ).closest( $( "table" ) ) );
                    utils.copyItemToClipboard( $activeTable );
                }

                $( `#${ $mergeButton.attr( 'id' ) }`, $statsDialog ).off().click( function() {
                    $(this).css("opacity", "0.1").attr("disabled","disabled") ;
                    let graphName = $(this).data("graph") ;
                    mergeSeries( graphName ) ;

                    $( "button.csap-copy", $statsDialog ).off().click( registerCopyFunction );
                } );

                $( "button.csap-copy", $statsDialog ).off().click( registerCopyFunction );

                $( "button.csap-check", $statsDialog ).off().click( function () {

                    let $activeTable = $( $( this ).closest( $( "table" ) ) );

                    $( `tbody`, $activeTable ).toggle();

                    if ( !$( `tbody`, $activeTable ).is( ":visible" ) ) {
                        $( this ).addClass( "csap-clear" );
                    } else {
                        $( this ).removeClass( "csap-clear" );
                    }

                } );
            }


            setTimeout( registerAfterDomUpdatedFunction, 300 );
        }


        //setTimeout( instance.show, 500);

    }

    function statsResize( dialogWidth, dialogHeight ) {

        setTimeout( function () {

            let $statsPanel = $( ".stats-panels" );

            let maxWidth = dialogWidth - 80;
            $statsPanel.css( "width", maxWidth );

            let maxHeight = dialogHeight
                - 100;
            // $statsPanel.css( "height", maxHeight );
            $statsPanel.parent().css( "height", maxHeight );


        }, 500 );

    }

    function buildHostStatsPanel( graphContainerId, graphLines, graphTitle ) {
        console.log( `buildHostStatsPanel() ${ graphContainerId } graphTitle: ${ graphTitle } numLines: ${ graphLines.length }` );

        // let $statsPanel = jQuery( '<div/>', { class: " " } );
        let $statsPanel = jQuery( '<div/>', {} );

        for ( let graphLineDefinition of graphLines ) {

            let statsSeriesName = graphLineDefinition.label;
            let seriesNameWithSpaces = statsSeriesName.replaceAll( "_", "_ " ).replaceAll( ",", ", " )

            // if ( i == (linesOnGraphArray.length-1))  console.log( JSON.stringify( graphDefinition, null, "\t" ) )
            let $seriesContainer = jQuery( '<div/>', { class: "csap-blue series-panel" } );

            let $header = jQuery( '<header/>', {
                class: "header",
                html: seriesNameWithSpaces
            } );


            $seriesContainer.append( $header );

            if ( !graphLineDefinition.timeValues ) {
                //console.log( `time series not found 0, assuming constant value.` ) ;
                $seriesContainer.append(
                    buildStatsTable( graphTitle, "Constant Value", "Series Skipped" )
                );
                $statsPanel.append( $seriesContainer );
                continue;
            }

            //console.log( `graphLineValues: {}`, graphLineValues )
            console.log( `checking for graphContainerId: '${graphContainerId}' `) ;


            let dataValues = graphLineDefinition.dataValues;
            if ( _lastRange && _lastRange[ graphContainerId ] && _lastRange[ graphContainerId ]?.xaxis?.from ) {
                dataValues = new Array();
                let timeValues = graphLineDefinition.timeValues;
                let index = 0;
                for ( let time of timeValues ) {
                    if ( time >= _lastRange[ graphContainerId ].xaxis.from
                        && time <= _lastRange[ graphContainerId ].xaxis.to ) {
                        dataValues.push( graphLineDefinition.dataValues[ index ] );
                    }
                    index++;
                }

            } else {
                console.log( `Did not find selected range for graphContainerId: '${graphContainerId}' `) ;
            }

            _mergedSeries.push( ...dataValues ) ;

            // console.log( `_mergedSeries`, _mergedSeries) ;

            let stats = graphStats( dataValues );
           

            if ( !stats ) {
                continue;
            }
            let storageName = `${ graphTitle }-${ statsSeriesName }`

            _latestStats[ storageName ] = stats;
            console.debug( `loading '${ storageName }'` );

            _dom.csapDebug( ` _snapshot[ storageName ]: `, _snapshot[ storageName ] );



            // console.log( ` _snapshot[ storageName ]: `, _snapshot[ storageName ] );
            $seriesContainer.append(
                buildStatsTable( graphTitle, seriesNameWithSpaces, stats.all, _snapshot[ storageName ]?.all )
            );

            $seriesContainer.append(
                buildStatsTable( graphTitle, seriesNameWithSpaces, stats.nonZero, _snapshot[ storageName ]?.nonZero, true )
            );

            //$statsLabel.append ( JSON.stringify( graphDefinition.stats, null, "<br/>" ) );
            $statsPanel.append( $seriesContainer );
        }

        let takeSnapshot = function () {
            _dom.logSection( `snapshot taken ${ graphTitle } ` );
            for ( let graphLineDefinition of graphLines ) {

                let statsSeriesName = graphLineDefinition.label;
                let storageName = `${ graphTitle }-${ statsSeriesName }`

                _snapshot[ storageName ] = _latestStats[ storageName ];

                console.debug( `snapshot taken '${ storageName }' `, _snapshot[ storageName ] );
            }


            _snapshot[ `merge-${ graphTitle }` ] = _mergedSeries;
            console.log( `_mergedSeries ${ graphTitle }`,  _snapshot[ `merge-${ graphTitle }` ].length ) ;
        }

        if ( _captureAllStats ) {

            takeSnapshot();
            _captureAllCount++;

            return;
        }

        return $statsPanel.html();
    }



    function buildStatsTable( graphTitle, statsSeriesName, samples, compareSamples, filterZero = false ) {


        if ( !samples || !samples[ `sample size` ] ) {
            if ( typeof samples === "string" ) {

                return jQuery( '<div/>', {
                    class: "csap-white",
                    text: samples
                } );
            }

            // no samples
            return "";

        }
        // $seriesContainer.append( jQuery( '<div/>', {
        //     class: "stats-category",
        //     text: categoryTitle + " : "
        // } ) );

        let $statsTable = jQuery( '<table/>', { class: "csap" } );

        let $head = jQuery( '<thead/>', {} ).appendTo( $statsTable );
        let $headRow = jQuery( '<tr/>', {} ).appendTo( $head );

        let itemLabel = "Options: ";
        let description="Click on the options to customize the view"
        if ( filterZero ) {
            description="Data set is filtered by removing all 0 values from the statistics. Useful for intermittent activities like garbage collections.";
            itemLabel = "== Filtered ==<br/>NON ZERO ONLY";
        }

        let $itemLabel = jQuery( '<th/>', { 
            title: description,
            html: `${ itemLabel }` 
        } ).appendTo( $headRow );
        jQuery( '<th/>', { html: utils.graphCompareLabel( false) } ).appendTo( $headRow );

        if ( compareSamples ) {
            jQuery( '<th/>', { text: `% Diff` } ).appendTo( $headRow );
            jQuery( '<th/>', { html: utils.graphCompareLabel( true) } ).appendTo( $headRow );
        }

        $itemLabel.append( jQuery( '<button/>', {
            title: `Copy table to clipboard`,
            class: "csap-icon csap-copy",
        } ) );

        $itemLabel.append( jQuery( '<button/>', {
            title: `Toggle display of table body`,
            class: "csap-icon csap-check",
        } ) );


        //
        //  source rows for sceen
        //
        $statsTable.append(
            buildTableSourceRow( graphTitle, statsSeriesName )
        );



        for ( let type in samples ) {
            // console.log( "type: " + type );

            let rowStyle = "";
            if ( type === "sum" ) {
                rowStyle = "high-row"
            }

            let $row = jQuery( '<tr/>', { class: `${ rowStyle }` } ).appendTo( $statsTable );


            $row.append(
                jQuery( '<td/>', {
                    html: `${ type }`
                } ) )


            let rawVal = samples[ type ];
            let rawCompare = 0;
            let diff = 0
            if ( compareSamples ) {
                rawCompare = compareSamples[ type ];


                if ( rawCompare > 0 ) {
                    diff = utils.numberPercentDifference( rawVal, rawCompare );
                    //console.log(`rawVal: ${ rawVal } rawCompare: ${ rawCompare } diff: ${ diff }%`) ;

                    if ( Math.abs( diff ) > 20 ) {
                        diff = `<span class=big-diff>${ diff }%</span>`
                    } else {
                        diff = `${ diff }%`
                    }

                } else {
                    diff = `-`;
                }
            }


            let suffix = "";

            // if ( type != "sample size" && graphTitle.includes( "ms" ) && rawVal > 2000 ) {
            //     rawVal = ( rawVal / 1000 ).toFixed( 1 );
            //     rawCompare = ( rawCompare / 1000 ).toFixed( 1 );

            //     if ( rawVal > 60 ) {
            //         rawVal = Math.round( rawVal );
            //         rawCompare = Math.round( rawCompare );
            //     }
            //     suffix = '<span class="statsUnits">s</span>'
            // }

            let metricValue = numberWithCommas( rawVal );
            let compareValue = numberWithCommas( rawCompare );



            let $currentValue = jQuery( '<span/>', {
                class: "current",
                html: `${ metricValue }  ${ suffix }`
            } );
            $row.append(
                jQuery( '<td/>', {
                    class: `numeric`,
                    html: $currentValue
                }
                ) );

            if ( compareSamples ) {


                $row.append(
                    jQuery( '<td/>', {
                        class: `numeric`,
                        html: diff
                    } ) )

                $row.append(
                    jQuery( '<td/>', {
                        class: `numeric`,
                        html: `${ compareValue }  ${ suffix }`
                    } ) )
            }

            //$seriesContainer.append( $itemLabel );
        }

        return $statsTable;

    }


    function buildTableSourceRow( graphTitle, statsSeriesName ) {
        let $row = jQuery( '<tr/>', { class: "copy-only" } );

        let graphDate = utils.getMonthDayYear( new Date() );
        let globDate = "" ;
        if ( utils.getGlobalDate()  ) {
            globDate = utils.getGlobalDate().val() ;
        }
        console.log( `buildTableSourceRow today; ${ graphDate} , global date: ${ globDate }`) ;
        if ( globDate && globDate.length > 4 ) {
            graphDate = globDate; // $( ".datepicker", $graphContainer ).val();
        }

        let sourceDetails = `source: <div>${ graphDate }</div>`;
        jQuery( '<td/>', {
            html: sourceDetails
        } ).appendTo( $row );

        let $sourceLinkColumn = jQuery( '<td/>', { colspan: "3" } ).appendTo( $row );


        let $hostAndTitle = jQuery( '<div/>', {
        } ).appendTo( $sourceLinkColumn );


        // http://localhost.csap.org:8011/app-browser#agent-tab,system
        let launchLocation = document.location.toString();
        if ( launchLocation.includes( `app-browser?defaultService` ) ) {
            launchLocation = launchLocation.replaceAll(
                `browser?defaultService`,
                `browser?graphDate=${ graphDate.replaceAll( "/", "-" ) }&defaultService`
            );
        } else if ( launchLocation.includes( `app-browser#agent` ) ) {
            launchLocation = launchLocation.replaceAll(
                `browser#agent`,
                `browser?graphDate=${ graphDate.replaceAll( "/", "-" ) }#agent`
            );
        }
        //
        // launch with date future
        // 
        jQuery( '<a/>', {
            class: "csap-link",
            target: "_blank",
            html: `${ _latestHost }: ${ graphTitle }`,
            href: `${ launchLocation }`
        } ).appendTo( $hostAndTitle );


        jQuery( '<div/>', {
            html: `${ statsSeriesName }`,
        } ).appendTo( $sourceLinkColumn );

        return $row;
    }

    function graphStats( graphLineValues ) {

        //console.log( "graphStats(): " + JSON.stringify( graphLineValues ) );
        console.log( `xxx graphStats graphLineValues: ${ graphLineValues.length } ` );

        let allStats = new Object();

        try {

            allStats.all = calculateStats( graphLineValues, true );

            let non0Values = new Array();
            for ( let i = 0; i < graphLineValues.length; i++ ) {
                if ( graphLineValues[ i ] != 0 ) {
                    non0Values.push( graphLineValues[ i ] )
                }

            }

            let percentZeros = ( graphLineValues.length - non0Values.length ) / graphLineValues.length;

            let limit = utils.graphZeroFilterPercentage() ;
            console.log( `percentZeros: ${ percentZeros }, limit: ${ limit }`) ;
            if ( percentZeros >  limit) {
                // for low hit services eliminate the 0's to avoid weighting the diffs
                allStats.nonZero = calculateStats( non0Values );
            }

        } catch ( err ) {
            console.log( "failed:", err );
        }

        //console.log( "graphStats(): " + JSON.stringify( allStats ) );

        return allStats;
    }


    function mergeSeries( graphName ) {
        console.log( `Merging series ${ graphName }` );

        console.log( `_snapshot keys ${ Object.keys( _snapshot) }` );

        let mergedStats = calculateStats( _mergedSeries );

        let snapMerge;
        let snapValues = _snapshot[ `merge-${ graphName }` ] ;
        if (  snapValues  ) {
            snapMerge = calculateStats( snapValues );
            //console.log( `snapMerge`, snapMerge, `\n source`, snapValues ) ;
        }


        let $statsDialog = $( `.stats-dialog` );

        console.log( `dialog: ${ $( '.stats-dialog' ).length } mergedStats` );

        $( `.stats-panels`, $statsDialog ).prepend(
            buildStatsTable( "All Series", "All Series", mergedStats, snapMerge )
        );

    }

    function calculateStats( dataArray ) {

        console.debug( `calculateStats` );

        _dom.csapDebug( ` dataArray: `, dataArray );

        if ( !Array.isArray( dataArray ) || dataArray.length == 0 ) {
            console.debug( `Not an array - skipping` );
            return;
        }


        console.log( `xxx calculateStats dataArray: ${ dataArray.length } _mergedSeries: ${ _mergedSeries.length }` );


        let arrayForCalculations = [ ...dataArray ];

        const asc = statsArray => statsArray.sort( ( a, b ) => a - b );

        const sum = statsArray => statsArray.reduce( ( a, b ) => a + b, 0 );

        const mean = statsArray => sum( statsArray ) / statsArray.length;

        // sample standard deviation
        const std = ( statsArray ) => {
            const mu = mean( statsArray );
            const diffArr = statsArray.map( a => ( a - mu ) ** 2 );
            return Math.sqrt( sum( diffArr ) / ( statsArray.length - 1 ) );
        };

        const quantile = ( statsArray, requestedQuartile ) => {
            const sortedValues = asc( statsArray );
            const quartilePosition = ( sortedValues.length - 1 ) * requestedQuartile;
            const quartileIndex = Math.floor( quartilePosition );
            const quartileDifference = quartilePosition - quartileIndex;

            if ( sortedValues[ quartileIndex + 1 ] !== undefined ) {
                //
                // quartile calculation needed when NOT and exact index
                //
                let qcalc = sortedValues[ quartileIndex ]
                    + quartileDifference *
                    ( sortedValues[ quartileIndex + 1 ] - sortedValues[ quartileIndex ] );;
                return qcalc.toFixed( 2 );
            } else {
                return sortedValues[ quartileIndex ];
            }
        };

        const q25 = statsArray => quantile( statsArray, .25 );

        const q50 = statsArray => quantile( statsArray, .50 );

        const q95 = statsArray => quantile( statsArray, .95 );

        const median = statsArray => q50( statsArray );




        let stats = new Object();

        const initialValue = 0.0;
        const sumWithInitial = dataArray.reduce(
            ( previousValue, currentValue ) => previousValue + currentValue,
            initialValue
        );
        stats[ "sum" ] = sumWithInitial.toFixed( 0 );

        let numMinutes = dataArray.length / 2; // 30 second collection
        stats[ "duration" ] = utils.adjustTimeUnitFromMs( numMinutes * 60 * 1000 );

        stats[ "sample size" ] = dataArray.length;

        //let mean = _csapCommon.calculateAverage( dataArray );
        // stats[ "mean" ] = _csapCommon.calculateAverage( dataArray ).toFixed( 2 );
        stats[ "mean" ] = mean( arrayForCalculations ).toFixed( 2 );
        stats[ "25th percentile" ] = q25( arrayForCalculations );
        stats[ "50th percentile" ] = q50( arrayForCalculations );
        stats[ "95th percentile" ] = q95( arrayForCalculations );
        stats[ "std. deviation" ] = _csapCommon.calculateStandardDeviation( dataArray ).toFixed( 2 );

        //stats[ "2x mean samples" ] = countItemsGreaterThan( dataArray, mean, 2 );;
        //stats[ "median" ] = _csapCommon.findMedian( dataArray );
        stats[ "minimum" ] = Math.min( ...dataArray );
        stats[ "maximum" ] = Math.max( ...dataArray );

        return stats;
    }



    function countItemsGreaterThan( dataArray, mean, scale ) {

        let threshhold = mean * scale;
        let numOverThreshold = 0;

        for ( const dataItem of dataArray ) {
            if ( dataItem > threshhold ) {
                numOverThreshold++;
            }
        }

        return numOverThreshold;
    }

    function numberWithCommas( x ) {
        return x.toString().replace( /\B(?=(\d{3})+(?!\d))/g, "," );
    }

    function getPlotOptionsAndXaxis( $plotPanel, graphName, flotTimeOffsetArray, originalTimestamps, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost ) {

        let isStack = $( '.useLineGraph', $GRAPH_INSTANCE ).prop( "checked" );

        if ( graphName == "OS_Load" ) {
            isStack = false;
        }


        let isMouseNav = $( '.zoomAndPan', $GRAPH_INSTANCE ).prop( "checked" );

        let mouseSelect = "xy"; // x, y, or xy

        //        if ( isMouseNav )
        //            mouseSelect = "xy" ;

        let plotWidth = Math.floor( $( ".plotPanel ." + graphName, $GRAPH_INSTANCE ).outerWidth( true ) );

        if ( plotWidth < 400 ) {
            console.log( `getPlotOptionsAndXaxis() plotWidth: ${ plotWidth }, updating to 400 minimal` );

            plotWidth = 400;
            $( ".plotPanel ." + graphName, $GRAPH_INSTANCE ).css( "width", plotWidth );
        }

        let numLegendColumns = Math.floor( plotWidth / 170 );
        // console.log( "graphName: " + graphName + " width: "  + plotWidth + " numLegendColumns" + numLegendColumns) ;


        //

        let plotOptions = {
            series: {
                stack: isStack,

                lines: {
                    show: true,
                    fill: isStack
                },
                points: {
                    show: false
                },
            },
            legend: {
                position: "nw",
                show: true,
                noColumns: numLegendColumns,
                container: null
            },
            selection: {
                // "xy"
                mode: mouseSelect
            },
            yaxis: {
                zoomRange: false,
                showMinorTicks: false,
                axisLabel: 'y-csap-label'
                //                autoScaleMargin: 2
            },
            zoom: {
                interactive: isMouseNav
            },
            pan: {
                interactive: isMouseNav
            },
            xaxis: buildTimeAxis(
                flotTimeOffsetArray,
                originalTimestamps,
                sampleInterval,
                $( "#numSamples", $GRAPH_INSTANCE ).val()
                , isSampling, plotWidth )
        };

        if ( utils.findInPreferences( "#show-grid-lines" ).prop( "checked" )
            || $( ".show-flot-grid", $GRAPH_INSTANCE).prop( "checked" ) ) {

            plotOptions.grid= {
                hoverable: true,
                clickable: false
            }
        } else {

            plotOptions.grid= {
                hoverable: true,
                clickable: false,
                color: "white",
                borderColor: "#9ab2c7"
            }
        }

        if ( isOutsideLegend( $GRAPH_INSTANCE ) ) {
            plotOptions.legend.container = $( ".Legend" + graphName, $GRAPH_INSTANCE )[ 0 ];
        }




        // 
        try {
            scaleLabels( $GRAPH_INSTANCE, $plotPanel, linesOnGraphArray, plotOptions )
        } catch ( e ) {
            console.log( "Failed to scaleLabel: ", e );
        }


        //for ( let i = graphSeries.data.length - 5; )


        return plotOptions;
    }

    function scaleLabels( $GRAPH_INSTANCE, $plotPanel, linesOnGraphArray, plotOptions ) {

        let $titleSpan = $( ".graphTitle span.name", $plotPanel );
        let title = $titleSpan.text();
        console.log( `scaleLabels() title: ${ title }` );


        // big assumption - use last value of  first series.
        let graphSeries = linesOnGraphArray[ 0 ];
        // let firstY = (graphSeries.data[ 1 ])[1] ;
        //plotOptions.yaxis.min = firstY ; this defaults in line mode
        let lastPointInSeries = graphSeries.data[ graphSeries.data.length - 1 ];
        let lastY = lastPointInSeries[ 1 ];

        // + " firstY: " + firstY
        //				console.log( "graphName: " + graphName 
        //						   + " last y: " + lastY )

        let unitAdjusted = false;


        let isShowRate = $( ".show-as-rate", $GRAPH_INSTANCE ).prop( "checked" );
        if ( isShowRate ) {
            title += ` <span style="font-size: 10pt; font-weight: normal; margin-left: 2em">per second</span>`;
            $titleSpan.html( title );

            let sampleSeconds = $( ".sampleIntervals", $GRAPH_INSTANCE ).val();
            lastY = lastY / sampleSeconds;

            plotOptions.yaxis.tickFormatter =
                function ( val, axis ) {
                    val = val / sampleSeconds;
                    return val.toFixed( 1 );
                }
        }

        if ( lastY > 1000 ) {
            console.log( title + " lasty: " + lastY );

            if ( title.toLowerCase().includes( "(mb)" ) ) {
                unitAdjusted = true;
                //$titleSpan.text( title.substring( 0, title.length - 4 ) );

                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        val = val / 1000;
                        return val.toFixed( 1 ) + "Gb";
                    }
            } else if ( title.toLowerCase().includes( "(kb)" ) ) {

                unitAdjusted = true;
                //$titleSpan.text( title.substring( 0, title.length - 4 ) );
                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        val = val / 1000;
                        return val.toFixed( 1 ) + "Mb";
                    }
            } else if ( title.toLowerCase().includes( "(b)" ) ) {

                unitAdjusted = true;

                //$titleSpan.text( title.substring( 0, title.length - 3 ) );
                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        return utils.bytesFriendlyDisplay( val );
                    }

            } else if ( title.toLowerCase().includes( "(ms)" ) ) {

                unitAdjusted = true;
                //$titleSpan.text( title.substring( 0, title.length - 4 ) );
                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        val = val / 1000;
                        return val.toFixed( 1 ) + "s";
                    }
            } else {
                $titleSpan.html( `${ title } <span title="Scaled to thousands" style="font-size: 10pt; font-weight: normal; margin-left: 2em">(thousands)</span>` );
                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        val = val / 1000;
                        return val.toFixed( 1 ) + "K";
                    }
            }
        }
        if ( ( lastY > 1000000 )
            && !unitAdjusted ) {
            // console.log( "OsMpStat: " + jsonToString( graphSeries.data ) + " lasty: " + lastY );
            $titleSpan.html( `${ title } <span title="Scaled to millions"  style="font-size: 10pt; font-weight: normal; margin-left: 2em">(millions)</span>` );
            plotOptions.yaxis.tickFormatter =
                function ( val, axis ) {
                    val = val / 1000000;
                    return val.toFixed( 2 ) + "M";
                }
        }

    }


    /**
     * http://www.flotcharts.org/
     * http://flot.googlecode.com/svn/trunk/API.txt
     * @param newHostContainerJQ
     * @param graphMap
     */

    function buildTimeAxis( flotTimeOffsetArray, originalTimestamps, sampleIntervalInSeconds, maxWindowSamples, isSampled, plotWidth ) {

        let flotXaxisConfig = {
            mode: "time",
            timeBase: "milliseconds",
            timeformat: "%H:%M<br>%b %d",
            showMinorTicks: false,
            axisLabel: 'x-csap-label'
        };

        //		  console.log( "buildTimeAxis() time array size: " + flotTimeOffsetArray.length + " isSampled:" + isSampled
        //					 + " plotWidth: " + plotWidth);
        let numItems = flotTimeOffsetArray.length - 1;

        //maxWindowSamples = 10;
        let samplesOnGraph = maxWindowSamples;
        if ( maxWindowSamples > numItems ) {
            samplesOnGraph = numItems;
        }


        console.log( `buildTimeAxis: maxWindowSamples: ${ maxWindowSamples },  display points: ${ samplesOnGraph } ` );

        // handle time scrolling
        if ( !isSampled ) {

            flotXaxisConfig.autoScale = "none" // "none" or "loose" or "exact" or "sliding-window"
            flotXaxisConfig.min = flotTimeOffsetArray[ samplesOnGraph ];
            flotXaxisConfig.max = flotTimeOffsetArray[ 0 ];
            // flotXaxisConfig.minTickSize =  [1, "minute"] ;
            //            flotXaxisConfig.panRange = [
            //                flotTimeOffsetArray[0], 
            //                flotXaxisConfig.max
            //            ] ;
            //            flotXaxisConfig.panRange = false ;

            // flotXaxisConfig.zoomRange = [graphMap.usrArray[numItems][0] ,
            // graphMap.usrArray[0][0] ] ;

        }
        if ( plotWidth < 270 ) {
            flotXaxisConfig.ticks = 3;
        }

        if ( utils.isGraphSlicingActive(  ) ) {
            // To slice unused time periods, data is graphed as numeric, then labels custom formated
            flotXaxisConfig.mode = null;
            flotXaxisConfig.tickFormatter= function(val) { return formTicks(val, originalTimestamps) }
        }

        return flotXaxisConfig;
    }

    // formTicks function
    function formTicks(val, ticksArr) {

        if ( !ticksArr ) {
            return val ;
        }
        let timestamp = ticksArr[val];

        if ( timestamp != undefined ) {
            let pointDate = new Date( timestamp );

            const zeroPad = (num, places) => String(num).padStart(places, '0')

            let hours = zeroPad(pointDate.getHours() , 2),
                minutes = zeroPad(pointDate.getMinutes() , 2) ;
            const month = months [pointDate.getMonth() ];
            const day = pointDate.getDate();


            return `${hours}:${minutes}<br>${month} ${day}`;
        } else {
            return val ;
        }

    }



    function addPlotAndLegend( resourceGraph, numGraphs, graphName, host, $plotFullPanel, $plotContainer ) {

        console.log( `adding: ${ graphName } on host ${ host } to ${ $plotFullPanel.attr( "id" ) }` );

        let $graphContainer = resourceGraph.getCurrentGraph();

        _latestHost = host;
        _latestDate = $( ".datepicker", $graphContainer ).val();

        let $graphPlot = jQuery( '<div/>', {
            id: "Plot" + host + graphName,
            class: "graphPlot " + graphName
        } ).appendTo( $plotFullPanel );

        // Support panel resizing
        setTimeout( function () {

            $plotFullPanel.resizable( {
                stop: function ( event, ui ) {
                    // console.log("width: " + ui.size.width + " height: " + ui.size.height) ;
                    graphLayout.setSize( graphName, ui.size, $graphContainer, $plotContainer );
                    resourceGraph.reDraw();
                },
                start: function ( event, ui ) {
                    console.log( `starting resize` );
                    $( "div.graphPlot", $plotFullPanel ).remove();
                }
            } );
            $plotFullPanel.on( 'resize', function ( e ) {
                // otherwise resize window will be continuosly called
                e.stopPropagation();
            } );

        }, 500 )

        $plotContainer.append( $plotFullPanel );
        $graphPlot.css( "height", "100%" );
        $graphPlot.css( "width", "100%" );


        let containerOffset = 20;

        if ( $( ".hostContainer", $graphContainer.parent() ).length > 0
            && $( ".graphOptions", $graphContainer.parent().length > 0 ) ) {
            containerOffset = $( ".hostContainer", $graphContainer.parent() ).offset().top
                + $( ".graphOptions", $graphContainer.parent() ).offset().top
                + 20;
        } else {
            console.warn( ` failed to resolve ".hostContainer", $graphContainer.parent()  ` );
        }
        containerOffset = Math.round( containerOffset ) ;
        let browserWindowHeightPx = Math.round( $( window ).outerHeight( true ) - containerOffset );

        console.log( `addPlotAndLegend() containerOffset: ${ containerOffset } fullHeight: ${ browserWindowHeightPx } ` );
        let targetHeight = browserWindowHeightPx;

        if ( graphLayout.getHeight( graphName, $graphContainer ) != null ) {
            targetHeight = graphLayout.getHeight( graphName, $graphContainer );
            //console.log("addPlotAndLegend() targetHeight: " + targetHeight  + " type: " + (typeof targetHeight) )   ;

            if ( typeof targetHeight == "string" && targetHeight.includes( "%" ) ) {
                let percent = targetHeight.substring( 0, targetHeight.length - 1 );
                targetHeight = Math.floor( browserWindowHeightPx * percent / 100 );
            }

            if ( targetHeight < 150 ) {
                targetHeight = 150;
            }
        }
        // Support for nesting on other pages
        if ( window.csapGraphSettings != undefined ) {
            targetHeight = window.csapGraphSettings.height;
            console.log( "getCsapGraphHeight: " + targetHeight );
        }



        //		  
        // plotDiv.css( "height", targetHeight ); // height is applied to plot div
        //plotPanel.css( "height", targetHeight ) ;
        $graphPlot.css( "height", targetHeight - 50 );

        if ( $( ".includeFullLegend", $graphContainer ).is( ":checked" ) ) {
            $plotContainer.addClass( "autohide" );
            //plotPanel.css( "height", "auto" ) ;
            //plotDiv.css( "height", targetHeight ) ;
        }


        let widthPadding = 100;
        let fullWidth = $( window ).outerWidth( true ) - widthPadding;
        let $newNav = $( "article.navigation" );
        if ( $newNav.length > 0 ) {
            let oldWidth = fullWidth;
            fullWidth = fullWidth - $newNav.outerWidth( true ) - 10;
            fullWidth += 30;
            console.log( `new nav detected, max width updated: from ${ oldWidth } to ${ fullWidth }` );
        }

        let targetWidth = fullWidth;

        if ( graphLayout.getWidth( graphName, $graphContainer ) != null ) {
            targetWidth = graphLayout.getWidth( graphName, $graphContainer );
            if ( typeof targetWidth == "string" && targetWidth.includes( "%" ) ) {
                let percent = targetWidth.substring( 0, targetWidth.length - 1 );
                targetWidth = Math.floor( fullWidth * percent / 100 );
            }

            if ( targetWidth < 400 )
                targetWidth = 400;
        }


        // Support for nesting on other pages
        if ( window.csapGraphSettings != undefined ) {
            targetWidth = window.csapGraphSettings.width;
            console.log( "getCsapGraphWidth: " + targetWidth );
        }
        // console.log( "targetWidth: " + targetWidth + " height: " + targetHeight );

        $plotFullPanel.css( "width", targetWidth ); // width is applied to entire panel

        let numHosts = $( ".instanceCheck:checked" ).length;
        // console.log(" num Hosts Checked" + numHosts) ;
        let isMultipleHosts = false;
        isMultipleHosts = numHosts > 1; // template is one, plus host is
        // another

        let useAutoSelect = $( ".useAutoSize", $graphContainer ).is(
            ':checked' );


        if ( useAutoSelect && numGraphs == 1 && !isMultipleHosts ) {
            $graphPlot.css( "height", "600px" );
        }


        if ( !isOutsideLegend( $graphContainer ) ) {
            // console.log("Need to add title") ;
        } else {
            //return plotDiv;
            let $graphBottomPanel = jQuery( '<div/>', {
                class: "graphBottomPanel"
            } ).appendTo( $plotFullPanel );

            let $plotLegendContainer = jQuery( '<div/>', {
                class: "legend Legend" + graphName
            } ).appendTo( $graphBottomPanel );
            //plotLegendDiv.css( "max-height", "50px" ) ;

            if ( targetWidth <= 300 ) {
                $plotLegendContainer.addClass( "legendOnHover" );
                //plotLegendDiv.hide();
                $( ".graphNotes, .titleHelp", $plotFullPanel ).hide();
            } else {

                $plotLegendContainer.show();
                $( ".graphNotes, .titleHelp", $plotFullPanel ).show();
            }

        }


        return $graphPlot;
    }




    function isOutsideLegend( $graphContainer ) {

        if ( graphLayout.isAGraphMaximized( $graphContainer ) )
            return false;
        return $( '.outsideLabels', $graphContainer ).prop( "checked" );
    }


    function configurePanelEvents( $currentGraph, $targetPanel, $panelContainer, graphsArray, originalTimestamps ) {

        configureToolTip( $currentGraph, $targetPanel, $panelContainer, originalTimestamps );
        configurePanelZooming( $targetPanel, $panelContainer, graphsArray )

    }

    function configureToolTip( $currentGraph, $targetPanel, $panelContainer, originalTimestamps ) {


        const $tipContainer = $( ".graph-hover-container", $currentGraph );


        console.log( `$targetPanel ${ $targetPanel.attr( "id" ) } `, );


        const hideWithTimeout = function () {
            tipTimer = setTimeout( function () {
                $( ".graph-hover-container", $panelContainer ).hide();
            }, 500 )
        }


        $tipContainer.off().hover( function () {
            // hoverIn
            clearTimeout( tipTimer );
        }, function () {
            // hoverOut
            //hideWithTimeout();
            $( ".graph-hover-container", $panelContainer ).hide();
        } )

        $targetPanel.off().bind( "plothover", function ( event, pos, flotPoint ) {

            //  $( ".graph-hover-container" ).hide();
            if ( flotPoint === null ) {
                clearTimeout( tipTimer );
                clearTimeout( bindTimer );
                hideWithTimeout();
                return;
            }
            // console.log( "flotPointDom" + jsonToString( flotPointDom ) );


            const performBinding = function () {

                let dateFromXAxis = new Date( flotPoint.datapoint[ 0 ] ) ;
                let yValue = flotPoint.datapoint[ 1 ];
                console.log( `dateFromXAxis: ${ dateFromXAxis } ` ) ;

                if ( utils.isGraphSlicingActive(  ) ) {
                    dateFromXAxis = new Date( originalTimestamps[ flotPoint.datapoint[ 0 ] ] ) ;
                } else {
                    dateFromXAxis.addMinutes(dateFromXAxis.getTimezoneOffset());
                }

                let formatedDate = dateFromXAxis.format( "HH:MM mmm d" );

                let label = flotPoint.series.label;

                if ( label == null ) {
                    console.log( `label is null skipping` );
                    return;
                }

                // support for mbean attributes versus entire location
                // if ( label.includes("type:") ) {
                //     label = label
                // }

                // if ( label.indexOf( ':' ) != -1 )
                //     label = label.substring( 0, label.indexOf( ':' ) );
                // if ( label.indexOf( '<' ) != -1 )
                //     label = label.substring( 0, label.indexOf( '<' ) );

                // let rateDetails = ``;
                let exactValue = ``

                let isShowRate = $( ".show-as-rate", $currentGraph ).prop( "checked" );

                let friendlyBaseValue = yValue;
                if ( isShowRate ) {
                    let sampleSeconds = $( ".sampleIntervals", $currentGraph ).val();
                    friendlyBaseValue = ( yValue / sampleSeconds );
                    exactValue = `<div class=tipInfo>${ yValue }</div>`;
                }



                let friendlyDisplay = numberWithCommas( friendlyBaseValue );

                console.log( `friendlyBaseValue: ${ friendlyBaseValue } friendlyDisplay: ${ friendlyDisplay }` );

                if ( !Number.isInteger( friendlyBaseValue ) ) {
                    friendlyDisplay = numberWithCommas( friendlyBaseValue.toFixed( 2 ) );
                    exactValue = `<div class=tipInfo>${ yValue }</div>`;
                }
                let title = $( "div.graphTitle span.name", $targetPanel ).text();

                if ( title.toLowerCase().includes( "(mb)" ) ) {
                    friendlyDisplay = utils.bytesFriendlyDisplay( Math.round( friendlyBaseValue * 1024 * 1024 ) );
                    exactValue = `<div class=tipInfo>${ yValue } </div>`;

                } else if ( title.toLowerCase().includes( "(kb" ) ) {
                    friendlyDisplay = utils.bytesFriendlyDisplay( Math.round( friendlyBaseValue * 1024 ) );
                    exactValue = `<div class=tipInfo>${ yValue } </div>`;

                } else if ( title.toLowerCase().includes( "(b)" ) ) {
                    friendlyDisplay = utils.bytesFriendlyDisplay( Math.round( friendlyBaseValue ) );
                    exactValue = `<div class=tipInfo>${ yValue } </div>`;
                }

                if ( isShowRate ) {
                    friendlyDisplay = friendlyDisplay + " / s";
                }


                let tipContent = '<div class="tipInfo">' + label + " <br>" + formatedDate
                    + "</div><div class='tipValue'>"
                    + friendlyDisplay
                    + "</div>"
                    + exactValue;

                let offsetY = 140;

                if ( window.csapGraphSettings != undefined ) {
                    offsetY = 160;
                }

                let offsetX = flotPoint.pageX ;
                let panelWidth = $targetPanel.outerWidth()
                let panelOffsetX = $targetPanel.offset().left ;
                let panelHalfWayX = ( panelWidth + panelOffsetX ) * 0.8 ;
                console.log( `plot point offsetX: ${ offsetX }, panelHalfWayX: ${ panelHalfWayX }, panelWidth: ${ panelWidth }, panelOffsetX: ${ panelOffsetX } ` ) ;
                if ( offsetX < panelHalfWayX ) {
                    offsetX += 15 ; // show to the right
                } else {
                    offsetX -= 160 ; // show to the left
                }

                // change y to stay in the graph to below.
                let pointY=flotPoint.pageY ;
                let panelOffsetY = $targetPanel.offset().top ;
                console.log( `plot pointY: ${ pointY }, panelOffsetY: ${ panelOffsetY }  ` ) ;
                if ( ( pointY - 140 ) < panelOffsetY ) {
                    offsetY = -30 ; // show below
                }


                if ( $( "body#manager" ).length > 0 ) {

                    //
                    //  Application Browser
                    //

                    if ( $( "#panelControls" ).length > 0
                        && $( "#panelControls" ).is( ":visible" ) ) {

                        // service deploy page
                        let panelOffset = $( "#panelControls" ).parent().css( "left" );
                        offsetX = offsetX - parseInt( panelOffset ) - 120;
                        offsetY = 160;
                    } else {
                        // offsetY = 100;
                        // offsetX = flotPointDom.pageX + 15;
                    }
                } else if ( $( "body#analytics-portal" ).length > 0 ) {
                    // analytics portal
                    offsetY = 180;
                    offsetX = flotPoint.pageX - 50;
                }

                console.log( `=== configureToolTip: offsetY ${ offsetY }`, flotPoint, ` position: `, ` event ${ event.currentTarget.clientLeft }...` );

                _dom.csapDebug( ` event: `, event );
                _dom.csapDebug( ` pos: `, pos );

                $tipContainer.html( tipContent )
                    .css( {
                        top: flotPoint.pageY - offsetY,
                        left: offsetX
                    } ).fadeIn( 200 );
            }

            clearTimeout( tipTimer );
            clearTimeout( bindTimer );
            bindTimer = setTimeout( performBinding, 500 );



        } );
    }

    function configurePanelZooming( $targetPanel, $panelContainer, graphsArray ) {

        $targetPanel.bind( "plotselected", function ( event, selectedRange ) {

            console.log( "Got " + $( this ).attr( "id" ) );

            console.log( `$targetPanel ${ $targetPanel.attr( "id" ) } $panelContainer ${ $panelContainer.attr( "id" ) }` );

            console.log( `last range: ${ _lastRange?.xaxis?.from } to ${ _lastRange?.xaxis?.to } , 
                current range: ${ selectedRange?.xaxis?.from } to ${ selectedRange?.xaxis?.to }` );


            let selectedGraphHost = $( this ).parent().parent().data( "host" );

            let graphParentId = $( this ).closest( `.scrolling-graphs` ).attr( `id` );
            if ( !graphParentId ) {
                // graphParentId = $( this ).closest( `.graphsContainer` ).attr( `id` );
                graphParentId = $( this ).closest( `.graphDiv` ).attr( `id` );
            }
            _lastRange[ graphParentId ] = selectedRange;

            // host stacking support
            let hostStackedId = `${ selectedGraphHost }-${ graphParentId }`
            _lastRange[ hostStackedId ] = selectedRange;

            console.log( `configurePanelZooming() id '${ graphParentId }' plot selected, host '${ selectedGraphHost }'`, );

            _dom.csapDebug( `selectedRange settings: `, selectedRange );

            let graphsForHost = graphsArray[ selectedGraphHost ];

            let hostOriginalZoom = new Array();
            for ( let graph of graphsForHost ) {

                let currentPlotOptions = graph.getOptions();

                let yaxis = graph.getAxes().yaxis;
                let yaxisOptions = currentPlotOptions.yaxes[ 0 ];

                if ( $( ".resetInterZoom", $panelContainer ).length == 0 ) {
                    //console.log( `axes: `, currentPlotOptions.xaxes[0], yaxisOptions.axisLabel, yaxis ) ;
                    let saved = new Object();
                    hostOriginalZoom.push( saved );
                    // preserver orig
                    saved.xmin = currentPlotOptions.xaxes[ 0 ].min;
                    saved.xmax = currentPlotOptions.xaxes[ 0 ].max;

                    //console.log( `${ yaxisOptions.axisLabel } yfrom: ${ yaxis.datamin } `  ) ;

                    saved.ymin = yaxis.datamin;
                    saved.ymax = yaxis.datamax;
                }

                if ( graph.getSelection() != null ) {
                    // only Zoom Y on current graph
                    currentPlotOptions.yaxes[ 0 ].min = selectedRange.yaxis.from;
                    currentPlotOptions.yaxes[ 0 ].max = selectedRange.yaxis.to;
                } else {
                    // console.log( `graph is null`) ;
                }


                currentPlotOptions.xaxes[ 0 ].min = selectedRange.xaxis.from;
                currentPlotOptions.xaxes[ 0 ].max = selectedRange.xaxis.to;

                graph.setupGrid();
                graph.draw();
                graph.clearSelection();
            }

            _dom.csapDebug( `hostOriginalZoom settings: `, hostOriginalZoom );

            if ( $( ".resetInterZoom", $panelContainer ).length == 0 ) {

                // console.log("Creating reset") ;
                let $resetPlotButton = jQuery( '<button/>', {
                    class: `csap-icon csap-remove resetInterZoom`,
                    title: `Click to restore graph view to default`,
                    text: "Reset View"
                } );
                $( "span.reset-graph-button", $targetPanel.parent().parent() ).append( $resetPlotButton );

                $resetPlotButton.off().click( function () {

                    console.log( `configurePanelZooming() $resetPlotButton clicked, graph count count: ${ graphsForHost.length }` );

                    $( this ).remove();
                    _lastRange[ graphParentId ] = null;
                    for ( let i = 0; i < graphsForHost.length; i++ ) {

                        let savedZoomSettings = hostOriginalZoom[ i ];

                        _dom.csapDebug( `Restoring graph ${ i }`, savedZoomSettings );
                        let targetPlot = graphsForHost[ i ];

                        let plotOptions = targetPlot.getOptions();
                        if ( !plotOptions ) {
                            console.warn( `Plot Options not defined` );
                        } else {

                            // latest plot requires numbers and precision set correctly on xmin

                            //targetPlot.clearSelection() ;

                            //                        console.log( `updating x min ${targetPlot.getOptions().xaxes[0].min} to ${hostOriginalZoom[i].xmin}`
                            //                            + ` type: ${ typeof hostOriginalZoom[i].xmin }`,
                            //                                targetPlot.getOptions().xaxes[0] ) ;

                            targetPlot.getOptions().xaxes[ 0 ].min = savedZoomSettings.xmin;
                            targetPlot.getOptions().xaxes[ 0 ].max = savedZoomSettings.xmax;

                            // plotOptions.xaxes[ 0 ].min = Number( hostOriginalZoom[ i ].xmin + ".0000" );
                            // plotOptions.xaxes[ 0 ].max = Number( hostOriginalZoom[ i ].xmax + ".0000" );

                            plotOptions.yaxes[ 0 ].min = savedZoomSettings.ymin;
                            plotOptions.yaxes[ 0 ].max = savedZoomSettings.ymax;

                            if ( savedZoomSettings.ymax === savedZoomSettings.ymin ) {
                                plotOptions.yaxes[ 0 ].max = savedZoomSettings.ymax + 1;
                            }
                        }

                        targetPlot.setupGrid();
                        targetPlot.draw();




                    }
                } );


            }

        } );
    }
}
