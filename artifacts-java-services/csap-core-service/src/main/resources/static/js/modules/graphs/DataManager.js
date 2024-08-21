

import _dom from "../utils/dom-utils.js";

import _csapCommon from "../utils/csap-common.js";


_dom.logHead( "Module loaded" );


let MS_IN_MINUTE = 60000;


export default function DataManager( hostArray ) {


    _dom.logHead( "Rebuilding datamanager" );

    let selectedHosts = hostArray;

    let hostsWithMissingData = 0;


    let _stackedGraphCache = new Object();
    this.clearStackedGraphs = function () {
        _stackedGraphCache = new Object();
    };

    this.removeHost = function ( hostName ) {
        console.log( `warning: host ${ hostName } does not contain valid data` );
        selectedHosts.splice( $.inArray( hostName, selectedHosts ), 1 );
        //$(".csap-dev04Container").remove() ;
    };

    this.getStackedGraph = function ( graphName ) {
        return _stackedGraphCache[ graphName ];
    };

    this.getStackedGraphCount = function ( graphName ) {
        return _stackedGraphCache[ graphName ].length;
    };

    this.initStackedGraph = function ( graphName ) {
        _stackedGraphCache[ graphName ] = new Array();
    };
    this.pushStackedGraph = function ( graphName, graphData ) {
        _stackedGraphCache[ graphName ].push( graphData );
        //console.log( `pushed: ${ graphName }`, graphData ) ;
    };

    this.getHosts = function () {
        return selectedHosts;
    };
    this.getHostCount = function () {
        return selectedHosts.length;
    };

    // graphsArray reserved for garbageCollection
    let _graphsArray = new Array();

    this.removeAllHostGraphs = function () {

        for ( let i = 0; i < _graphsArray.length; i++ ) {
            delete _graphsArray[ i ];
        }

        _graphsArray = new Array();


    }

    this.getHostGraph = function ( host ) {

        let flotInstances = _graphsArray[ host ];

        console.log( `flotInstances for ${ host }:` );
        _dom.csapDebug( `flotInstances: `, flotInstances );

        return _graphsArray[ host ];
    };

    this.updateHostGraphs = function ( host, flotInstances ) {

        console.log( `adding flotInstances: ${ host }` );

        _dom.csapDebug( `flotInstances: `, flotInstances );

        _graphsArray[ host ] = flotInstances;
    };

    this.getAllHostGraphs = function () {
        return _graphsArray;
    };

    // data is cached to support appending latest, redraw with options modified, etc
    let _hostToDataMap = new Array();

    this.clearHostData = function ( hostName ) {
        const hostData = _hostToDataMap[ hostName ];

        _dom.logSection( `clearing host data ${ hostName }` );
        _dom.csapDebug( `hostData settings: `, hostData );

        _hostToDataMap[ hostName ] = null;
        return hostData;
    }

    this.addHostData = function ( hostGraphData ) {
        let host = hostGraphData.attributes.hostName;

        console.log( `Adding Data for host ${ host }` );
        // alert("_metricsJsonCache[host]: " + _metricsJsonCache[host] ) ;
        if ( !_hostToDataMap[ host ] ) {

            _hostToDataMap[ host ] = hostGraphData;
        } else {
            // flot chokes on out of order data. Old data is appended to newer
            // data

            for ( let key in hostGraphData.data ) {
                _hostToDataMap[ host ].data[ key ] = hostGraphData.data[ key ]
                    .concat( _hostToDataMap[ host ].data[ key ] );
            }

        }

        return _hostToDataMap[ host ];
    }

    let hackLengthForTesting = -1;
    let _dataAutoSampled = false;

    this.isDataAutoSampled = function () {
        return _dataAutoSampled;
    };

    this.getLocalTime = getLocalTime;
    function getLocalTime( originalTimestamps, offsetString ) {

        //return originalTimestamps ;
        let timestampsWithOffset = new Array();
        for ( const gmtTimeString of originalTimestamps ) {

            let origTime = parseInt( gmtTimeString );

            let offsetAmount = MS_IN_MINUTE * parseInt( offsetString );

            let offsetTime = origTime - offsetAmount;
            //console.log(`origTime: ${origTime} offsetAmount: ${ offsetAmount } offsetTime: ${offsetTime}`)

            timestampsWithOffset.push( offsetTime );
        }

        //console.log( `originalTimestamps: ${ originalTimestamps[0] }  timestampsWithOffset: ${ timestampsWithOffset[0] }`)

        return timestampsWithOffset;

    }

    /**
     * Helper function to build x,y points from 2 arrays
     * 
     * Test: alertify.alert( JSON.stringify(buildPoints([1,2,3],["a", "b",
     * "c"]), null,"\t") )
     * 
     * @param xArray
     * @param yArray
     * @returns
     */

    this.buildPoints = buildPoints;
    function buildPoints( timeStamps, metricValues, $GRAPH_INSTANCE, graphWidth ) {
        let graphPoints = new Array();
        console.log( `building array coordinates` );

        if ( typeof graphWidth == "string" && graphWidth.contains( "%" ) ) {
            let fullWidth = $( window ).outerWidth( true )
            let percent = graphWidth.substring( 0, graphWidth.length - 1 );
            graphWidth = Math.floor( fullWidth * percent / 100 );
        }

        let spacingBetweenSamples = $( ".samplingPoints", $GRAPH_INSTANCE ).val();


        let samplingInterval = Math.ceil( timeStamps.length / ( graphWidth / spacingBetweenSamples ) );

        let samplingAlgorithm = $( ".zoomSelect", $GRAPH_INSTANCE ).val()
        let filteringLevels = $( ".meanFilteringSelect", $GRAPH_INSTANCE ).val()
        let isSample = isAutoSample( timeStamps, $GRAPH_INSTANCE );

        if ( isSample && samplingAlgorithm == "Auto" ) {
            samplingAlgorithm = "Mean";
        }

        console.debug( `timeStamps: ${ timeStamps.length }, metricValues: ${ metricValues.length } \n isSample: ${ isSample } samplingAlgorithm: '${ samplingAlgorithm }'`
            + ` samplingInterval: ${ samplingInterval } graphWidth:  ${ graphWidth }` );


        _dom.csapDebug( ` grapmetricValueshPoints: `, metricValues );
        _dom.csapDebug( ` timeStamps: `, timeStamps );


        let metricAlgorithmValue = -1;
        let numberOfPoints = 0;

        let filteredValues = new Array();

        let filterCount = 0;
        if ( filteringLevels != 0 ) {
            console.log( `metricValues: ` );
            _dom.csapDebug( ` metricValues: `, metricValues );

            let mean = _csapCommon.findMedian( metricValues );
            let maxFilter = filteringLevels * mean;
            let minFilter = mean / filteringLevels;
            for ( let i = 0; i < metricValues.length; i++ ) {
                filteredValues[ i ] = false;
                if ( metricValues[ i ] > maxFilter || metricValues[ i ] < minFilter ) {
                    filteredValues[ i ] = true;
                    filterCount++;
                }
            }
            console.log( "Pruning: items: ", filterCount, " from total items: ", metricValues.length );
        }

        for ( let forwardIndex = 0; forwardIndex < timeStamps.length; forwardIndex++ ) {


            try {
                let metricValue = 0;

                // metrics are reversed for forward processing....
                let reverseIndex = timeStamps.length - 1 - forwardIndex;

                if ( filteredValues.length > 0 && filteredValues[ reverseIndex ] ) {
                    continue;
                }

                if ( metricValues.length ) {
                    metricValue = metricValues[ reverseIndex ];
                } else {
                    metricValue = metricValues;
                }

                if ( isSample ) {

                    switch ( samplingAlgorithm ) {

                        case "Max":
                            if ( metricValue > metricAlgorithmValue )
                                metricAlgorithmValue = metricValue;
                            break;

                        case "Min":
                            if ( metricValue < metricAlgorithmValue || metricAlgorithmValue < 0 )
                                metricAlgorithmValue = metricValue;
                            break;

                        case "Auto":
                        case "Mean":
                            metricAlgorithmValue += metricValue;
                            break;

                    }

                    numberOfPoints++;
                    if ( forwardIndex % samplingInterval != 0 && forwardIndex != reverseIndex ) {
                        continue;
                    } else {

                        metricValue = metricAlgorithmValue;
                        let resetValue = -1;
                        if ( samplingAlgorithm == "Mean" ) {

                            metricValue = metricAlgorithmValue / numberOfPoints;
                            resetValue = 0;
                            numberOfPoints = 0;
                        }
                        metricAlgorithmValue = resetValue;
                    }
                }

                let resourcePoint = new Array();


                //let timeToSecond=Math.floor(xArray[xArray.length -i]/30000)*30000 ;
                //console.log("orig: " + xArray[i] + " rounded: " + timeToSecond) ;

                // points are reversed for flot stacking to work
                resourcePoint.push( timeStamps[ reverseIndex ] );
                resourcePoint.push( metricValue );

                graphPoints.push( resourcePoint );
            } catch ( err ) {
                console.warn( `failed adding item`, err );
            }

        }

        console.debug( `series built: ${ graphPoints.length }` );

        _dom.csapDebug( ` graphPoints: `, graphPoints );

        timeStamps = null;
        metricValues = null;
        // console.log( "Points: " + JSON.stringify(graphPoints ) );

        return graphPoints;
    }


    /**
     * too much data slows down UI; data is trimmed if needed
     */
    this.isAutoSample = isAutoSample;
    function isAutoSample( timeArray, $GRAPH_INSTANCE ) {


        _dataAutoSampled = false;

        let $zoomSelect = $( ".zoomSelect option:selected", $GRAPH_INSTANCE ) ;
        let zoomSelection = "";
        if ( $zoomSelect.length > 0 ) {
            zoomSelection = $zoomSelect.text() ;
        }


        let displaySelection = $( "#numSamples option:selected", $GRAPH_INSTANCE ).text();

        let sampleLimit = $( ".samplingLimit", $GRAPH_INSTANCE ).val();
        let maxAutoShiftDays = $( ".max-auto-shift-days", $GRAPH_INSTANCE ).val()

        console.log( `isAutoSample timeArray.length :${ timeArray.length } , maxAutoShiftDays: ${ maxAutoShiftDays }, sampleLimit: ${ sampleLimit } displaySelection: ${ displaySelection }, zoomSelection ${ zoomSelection }` )  ;

        if ( zoomSelection == "All" ) {
            console.log(`Enhanced: escapping on all selected`) ;
            return false ;
        }

        if ( timeArray.length > sampleLimit ) {

            if ( "Mean Min Max".contains( displaySelection ) ) {
                _dataAutoSampled = true;

            } else if ( ( "Auto" == displaySelection )
                && ( timeArray.length - 100 )  > ( maxAutoShiftDays * sampleLimit ) ) {
                _dataAutoSampled = true;
            }
        }


        console.debug( `displaySelection: '${ displaySelection }', number of Points: '${ timeArray.length }', _dataAutoSampled: ${ _dataAutoSampled }` );
        return _dataAutoSampled;
    }

}

