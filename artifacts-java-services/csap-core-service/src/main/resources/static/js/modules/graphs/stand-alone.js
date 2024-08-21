
console.log( `loading imports` );


import "../libs/csap-modules.js";
import utils from "../browser/utils.js"

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";



import ResourceGraph from "./ResourceGraph.js"



_dom.onReady( function () {

    _utils.prefixColumnEntryWithNumbers( $( "table" ) )

    let appScope = new standalone_main( globalThis.settings );

    // _dialogs.loading( "start up" );

    appScope.initialize();


} );

function standalone_main() {

    // const customViews = {
    //     "Java Heap": {
    //         "graphs": [ "Cpu_As_Reported_By_JVM", "heapUsed", "minorGcInMs" ],
    //         "graphMerged": { "heapUsed": "heapMax", "minorGcInMs": "majorGcInMs" },
    //         "graphSize": {
    //             "Cpu_As_Reported_By_JVM": { "width": "100%", "height": "100" },
    //             "heapUsed": { "width": "100%", "height": "45%" },
    //             "minorGcInMs": { "width": "100%", "height": "45%" }
    //         }
    //     },
    //     "Tomcat Http": {
    //         "graphs":
    //             [ "Cpu_As_Reported_By_JVM", "sessionsCount", "httpRequestCount", "httpKbytesReceived", "httpProcessingTime" ],
    //         "graphMerged": { "httpKbytesReceived": "httpKbytesSent", "sessionsCount": "sessionsActive", "httpRequestCount": "tomcatConnections" }
    //     },
    //     "Java Thread": { "graphs": [ "Cpu_As_Reported_By_JVM", "jvmThreadCount", "tomcatThreadsBusy" ], "graphMerged": { "jvmThreadCount": "jvmThreadsMax", "tomcatThreadsBusy": "tomcatThreadCount" } }
    // }


    this.initialize = function () {

        _dom.logSection( `initializing main`, uiSettings );


        //
        // add global date picker
        //
        const globalDayOffset = `<label id=global-graph-date
               title="The global date for all the graphs: this can by overwritten using the graph extended options"
               className="csap flex-right">
            <input type="text"
                   placeholder="Graph Date">
        </label>`

        const $dayContainer =  jQuery( '<span/>', { }).html( globalDayOffset );
        $("#page-info").append( $dayContainer ) ;

        let $globalDate = $( "#global-graph-date" );
        let today = new Date();
        let yesterday = new Date() ;
        yesterday.setDate( yesterday.getDate() - 1 ) ;
        // yesterday.setDate(yesterday.getDate() - 1);
        $( "input", $globalDate ).datepicker( {
            maxDate: '0',
        } );

        utils.setGlobalDate( $( "input", $globalDate ) );

        console.log( `currentDate: ${ $( "input", $globalDate ).val() }` );

        //$( "input" , $globalDate).val( utils.getMonthDayYear( yesterday  ) );

        //
        // Add Day Remover
        //
        const dayRemover = `<label className="csap">Graph Slice</label>
        <input
            id=perform-graph-slice
            type="checkbox"/>
        <span>

				<label className="csap" title="Offset from start of data to slice. 2880 is 1 day">
					<span className="six-block">Offset:</span>
					<input
                        id="graph-slice-start"
                        type="text"
                        value="1880"/>

				</label>

				<label className="csap" style="margin-left: 2em"
                       title="Number of items to remove from array. 2880 is 1 day">
					<span className="six-block">Amount:</span>
					<input
                        id="graph-slice-amount"
                        type="text"
                        value="3880"/>

				</label>

			</span>`


        const $dayRemover =  jQuery( '<span/>', { }).html( dayRemover );
        $("#page-info").append( $dayRemover ) ;


        //
        // Initialize graph component using settings from graph page
        //
        const theGraph = new ResourceGraph( uiSettings.containerId,
            uiSettings.metricType,
            uiSettings.life,
            uiSettings.eventUser,
            uiSettings.eventMetricsUrl );
        theGraph.addCustomViews( );




        // let viewLoadedPromise = _net.httpGet( OS_URL + "/graph/layouts" );

        // viewLoadedPromise
        //     .then( customViews => {
        //         theGraph.addCustomViews( customViews );
        //     } )
        //     .catch( ( e ) => {
        //         console.warn( e );
        //     } );

        // if ( uiSettings.metricType == METRICS_JAVA ) {
        //     var serviceName = $.urlParam( "service" );
        //     if ( serviceName != null ) {
        //         serviceName = serviceName.substr( 0, serviceName.indexOf( "_" ) );

        //         document.title = uiSettings.pageTitle;

        //         $( "header" ).html( '<div class="noteHighlight" >' + serviceName + " : Java Graphs" + '</div>' );
        //         //		$("#csapPageLabel").text( serviceName + ":"  );
        //         //		$("#csapPageVersion").text("").css("margin-right", "3em");
        //     }

        //     theGraph.addCustomViews( customViews );
        // }

    }

}