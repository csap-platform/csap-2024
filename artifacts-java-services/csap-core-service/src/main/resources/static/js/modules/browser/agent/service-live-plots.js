


//
//  plotly is large - run minified: plotly.js versus plotly.min.js
//

// define( ["../../../../../webjars/plotly.js-dist-min/2.9.0/plotly.min.js"], function ( Plotly ) {

//     console.log( "Module  loaded" ) ;


import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";

import "../../../../../webjars/plotly.js-dist-min/2.9.0/plotly.min.js";


const alertPlot = Live_Plots();

export default alertPlot;


function Live_Plots() {

    _dom.logHead( "Module loaded" );


    const $liveContent = $( "#agent-tab-live", "#agent-tab-content" );

    var collectedData = new Object();
    var timeCollectedArrayName = "collected";
    var plotIdIndex = 0;

    var needsInitialize = true, windowResizeTimer = null;

    var doAutoRefreshOnce = true;

    return {

        addPlot: function ( meterName ) {
            if ( needsInitialize ) {
                needsInitialize = false;
                initialize();
            }
            addPlot( meterName );
        },

        drawPlots: function () {
            drawPlots();
        },

        addData: function ( meterName, collectionDurationInSeconds, count, histoOptional ) {

            return addData( meterName, collectionDurationInSeconds, count, histoOptional )
        },

        addTimeNow: function () {
            addTimeNow();
        },

        p2: function () {

        },

        p3: function () {

        },

        p4: function () {

        }

    }

    function initialize() {
        $( window ).resize( function () {

            clearTimeout( windowResizeTimer );
            windowResizeTimer = setTimeout( () => windowResize(), 1200 );
        } );
    }



    function addPlot( meterName, type, $panel ) {

        console.log( `addPlot() ${ meterName }, type: ${ type } ` );

        var $meterPanel = $panel;
        let $typeGraph = null;


        let graphType = "change"; // default graph to display
        if ( type ) {
            graphType = type;
        } else {
            $( "#meter-plot-template input.select-" + graphType ).prop( "checked", true );
        }

        if ( !$panel ) {
            console.log( `addPlot() Creating a new panel` );
            $meterPanel = $( "#meter-plot-template" ).clone();
            $typeGraph = $( "div.meter-graph", $meterPanel );

        } else {
            // re add a plot div
            $typeGraph = jQuery( '<div/>', {
                class: "meter-graph"
            } )
            $typeGraph.appendTo( $meterPanel );
        }



        let index = plotIdIndex++;

        let panelId = "panel-" + index;
        $meterPanel.attr( "id", panelId )

        $meterPanel.attr( "title", meterName );
        $( ".graph-title", $meterPanel ).text( meterName );
        $meterPanel.appendTo( $( "#meter-plots" ) );

        let typePlotId = "plot-" + index;
        $( "input.select-" + graphType, $meterPanel ).data( "plotid", typePlotId );
        $typeGraph
            .data( "name", meterName )
            .data( "type", graphType )
            .attr( "id", typePlotId );

        $( "button.close-panel", $meterPanel ).off().click( function () {
            removePlot( panelId );
            drawPlots();
        } );


        $( "input", $meterPanel ).off().change( function () {
            let type = $( this ).data( "type" );

            if ( $( this ).is( ":checked" ) ) {
                addPlot( meterName, type, $meterPanel );

            } else {
                $( "#" + $( this ).data( "plotid" ) ).remove();
            }
            drawPlots();
        } );

        if ( doAutoRefreshOnce ) {
            doAutoRefreshOnce = false;
            // $( "#refreshData", $liveContent ) .val( 5 ) .trigger( 'change' );
            $( "#refreshData", $liveContent ).trigger( 'change' );
        } else {
            drawPlots();
        }

    }


    function removePlot( panelId ) {

        console.log( `removePlot(): ${ panelId }` );
        $( "#" + panelId ).remove();

    }

    function windowResize() {
        let $plots = $( "#meter-plots div.meter-graph" );
        $plots.each( function () {
            let $graph = $( this );
            let plotId = $graph.attr( "id" );

            Plotly.relayout( plotId, {
                width: Math.floor( $graph.parent().outerWidth( false ) - 40 )
            } )
        } )
    }


    function drawPlots() {

        let $plots = $( "#meter-plots div.meter-graph" );
        console.debug( `drawPlots() Number of plots:  ${ $plots.length }` );

        if ( $plots.length > 0 ) {
            $( "#meter-plots" ).show();
        }
        $plots.each( function () {

            let $graph = $( this );

            let plotId = $graph.attr( "id" );
            let plotName = $graph.data( "name" );
            let plotType = $graph.data( "type" );

            let title = plotType;
            if ( title == "rate" || title == "throughput" ) {
                title += " per second"
            } else if ( title == "distribution" ) {
                title += " in milliseconds"
            }
            let plotData=buildPlotData( plotName, plotType ) ;


            // console.log( `drawPlots() plotName: ${ plotName }  x size: ${ plotData[0].x.length }, y size: ${ plotData[0].x.length }, \n x: ${ plotData[0].x } \n y: ${ plotData[0].y } ` )

            Plotly.react( plotId,
                plotData,
                buildLayout(
                    title,
                    getMeterArray( timeCollectedArrayName ).length )
            );
            // {responsive: true} layout bugs
            Plotly.Plots.resize( plotId );

        } );
        //console.log( `drawPlot() `, collectedData ) ;
    }

    function addTimeNow() {

        let meterArray = getMeterArray( timeCollectedArrayName );
        meterArray.push( plotlyDate() );

    }

    function addData( meterName, collectionDurationInSeconds, count, meter ) {


        // if ( meterName.contains( `changesethousekeeper` ) ) {
        //     console.log( `adding data from ${ meterName }, seconds: ${ collectionDurationInSeconds }`, meter );
        // }

        if ( window.csapdebug ) {
            _dom.csapDebug( meter )
        }

        let meterCountArray = getMeterArray( meterName + "count" );
        meterCountArray.push( count );


        // if ( meterName.includes( "sessions.created" ) ) {
        //     console.log( `adding: ${ meterName } ${ count }  values: ${ meterCountArray }`  ) ;
        // }


        let meterChangeArray = getMeterArray( meterName + "change" );

        let change = 0;
        if ( meterCountArray.length > 1 ) {
            change = count - meterCountArray[ meterCountArray.length - 2 ];
            //console.log(`delta: ${delta}`) ;
        }
        meterChangeArray.push( change );
        let latestDiff = change;


        let meterTimeChangeArray = getMeterArray( meterName + "timechange" );

        let meterRateArray = getMeterArray( meterName + "rate" );
        let rate = 0;

        if ( change != 0 ) {
            rate = change / collectionDurationInSeconds;
        }
        meterRateArray.push( rate.toFixed( 2 ) );

        if ( meter && meter[ "total-ms" ] != undefined ) {

            let totalNowSeconds = meter[ "total-ms" ] / 1000;
            let meterTotalServerArray = getMeterArray( meterName + "totalms" );

            meterTotalServerArray.push( totalNowSeconds );

            let meterRateServerArray = getMeterArray( meterName + "throughput" );
            let serverRate = 0;

            if ( change != 0 ) {
                let totalLastSeconds = meterTotalServerArray[ meterTotalServerArray.length - 2 ]
                meterTimeChangeArray.push( totalNowSeconds - totalLastSeconds );
                serverRate = change / ( totalNowSeconds - totalLastSeconds );
            }
            meterRateServerArray.push( serverRate.toFixed( 2 ) );

            if ( meter[ "bucket-0.5-ms" ] != undefined ) {
                let bucket5Array = getMeterArray( meterName + "bucket5" );
                bucket5Array.push( meter[ "bucket-0.5-ms" ] );

                let bucket95Array = getMeterArray( meterName + "bucket95" );
                bucket95Array.push( meter[ "bucket-0.95-ms" ] );

                let bucketmaxArray = getMeterArray( meterName + "bucketmax" );
                bucketmaxArray.push( meter[ "bucket-max-ms" ] );
            }
        } else if ( meter && meter[ "mean-ms" ] != undefined ) {
            // prometheus support uses mean instead of bucket 05

            let bucket5Array = getMeterArray( meterName + "bucket5" );
            bucket5Array.push( meter[ "mean-ms" ] );
            if ( meter[ "bucket-0.95-ms" ] != undefined ) {
                let bucket95Array = getMeterArray( meterName + "bucket95" );
                bucket95Array.push( meter[ "bucket-0.95-ms" ] );
            }

        }


        if ( meterName.startsWith( "csap.jms" ) ) {
            //console.log( `collectionDurationInSeconds: ${collectionDurationInSeconds}, count: ${count}, change: ${change}` ) ;
        }

        return latestDiff;

    }

    function getMeterArray( meterName ) {

        let meterArray = collectedData[ meterName ];
        if ( meterArray === undefined ) {
            meterArray = new Array();
            collectedData[ meterName ] = meterArray;
        }
        return meterArray;
    }


    //        var data = [
    //            {
    //                x: ['2013-10-04 22:23:00', '2013-11-04 22:23:00', '2013-12-04 22:23:00'],
    //                y: [1, 3, 6],
    //                type: 'scatter'
    //            }
    //        ] ;

    function addToPlotIfDataExists( plotData, plotKey, description ) {

        if ( getMeterArray( plotKey ).length > 0 ) {

            let itemLines = {
                x: getMeterArray( timeCollectedArrayName ),
                y: getMeterArray( plotKey ),
                type: 'lines',
                name: description
            }
            plotData.push( itemLines );
        }


    }
    function buildPlotData( plotKey, plotType ) {

        let plotData = new Array();

        if ( plotType == "distribution" ) {

            addToPlotIfDataExists( plotData, plotKey + "bucketmax", "max" );

            addToPlotIfDataExists( plotData, plotKey + "bucket95", "95%" );

            addToPlotIfDataExists( plotData, plotKey + "bucket5", "mean" );

        } else {

            let series = {
                x: getMeterArray( timeCollectedArrayName ),
                y: getMeterArray( plotKey + plotType ),
                type: 'lines'
            }
            plotData.push( series );

        }


        _dom.csapDebug( "buildPlotData()  ", plotData );
        if ( !plotData[ 0 ] || plotData[ 0 ].y.length == 0 ) {
            console.warn( `Warning: no data found for '${ plotKey + plotType }'` )
        }

        return plotData;
    }



    function plotlyDate() {
        // yyyy-mm-dd HH:MM:SS
        let d = new Date();
        let dformat = [
            d.getFullYear(),
            ( d.getMonth() + 1 ).padLeft(),
            d.getDate().padLeft()
        ].join( '-' ) + ' ' +
            [ d.getHours().padLeft(),
            d.getMinutes().padLeft(),
            d.getSeconds().padLeft() ].join( ':' );

        return dformat;
    }

    function buildLayout( title, version ) {

        var layout = {
            title: title,
            datarevision: version,
            uirevision: true,
            margin: {
                l: 100,
                r: 50,
                b: 50,
                t: 50,
                pad: 10
            },
            xaxis: {
                //                title: 'Time',
                showgrid: false,
                showline: true,
                zeroline: true
            },
            yaxis: {
                // title: plotType,
                zeroline: true,
                showline: true
            }
        };

        if ( window.csapdebug ) {
            _dom.csapDebug( layout )
        }
        return layout;
    }
};


Number.prototype.padLeft = function padLeft( base, chr ) {
    let len = ( String( base || 10 ).length - String( this ).length ) + 1;
    return len > 0 ? new Array( len ).join( chr || '0' ) + this : this;
}
