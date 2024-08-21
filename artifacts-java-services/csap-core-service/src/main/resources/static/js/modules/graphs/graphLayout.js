// define( [ ], function () {

//     console.log( "Module loaded: graphPackage/graphLayout" ) ;


import _dom from "../utils/dom-utils.js";

const graphLayout = graphs_layouts();

export default graphLayout


function graphs_layouts() {

    _dom.logHead( "Module loaded" );

    let _graphSettings = null;

    //	 let graphCookieName = "csapGraphs_Demo";
    //	 let grapCookieExpireDays = 365;

    let _customGraphSizes = new Object();

    let _customLayoutSelected = {};
    let _customRestoreCount = 0;

    return {
        //
        customLayoutSelected() {
            _customLayoutSelected = {};
            _customRestoreCount = 0;
        },
        //
        addContainer: function ( baseContainerId, $plotContainer ) {
            addContainer( baseContainerId, $plotContainer );
        },
        publishPreferences: function ( resetResource ) {
            publishPreferences( resetResource );
        },
        //
        restore: function ( resourceGraph, $hostPlotContainer ) {
            restore( resourceGraph, $hostPlotContainer );
        },
        isAGraphMaximized: function ( $graphContainer ) {
            return isAGraphMaximized( $graphContainer )
        },
        //
        getWidth: function ( graphName, $graphContainer ) {

            console.log( "layout.getWidth(): _customGraphSizes" );
            _dom.csapDebug( "_customGraphSizes", _customGraphSizes );

            let size = "50%"
            if ( $( ".layoutSelect", $graphContainer ).val() != "default"
                && _customGraphSizes[ graphName ] != undefined ) {
                size = _customGraphSizes[ graphName ].width;
            }
            if ( isAGraphMaximized( $graphContainer ) ) {
                size = "100%";
            }
            console.log( `${ graphName } width: ${ size }`
                + `\n   layout: ${ $( ".layoutSelect", $graphContainer ).val() }`
                + `\n   container:  ${ $graphContainer.attr( "id" ) }` );

            return size;
        },
        //
        getHeight: function ( graphName, $graphContainer ) {

            let size = "200"
            if ( $( ".layoutSelect", $graphContainer ).val() != "default"
                && _customGraphSizes[ graphName ] != undefined ) {
                size = _customGraphSizes[ graphName ].height;
            }
            if ( isAGraphMaximized( $graphContainer ) ) {
                size = "100%";
            }
            //console.log("getHeight(): " + graphName + " : "+ size) ;
            return size;
        },
        setSize: function ( graphName, size, $graphContainer, $plotContainer ) {
            console.debug( `layout.setSize() Updating '${ graphName }' width: ${ size.width }  height: ${ size.height } ` );
            // _customGraphSizes[ graphName ] = size;

            //console.log( "layout.setSize() pre1 : _customGraphSizes", _customGraphSizes );

            if ( graphName in _customGraphSizes ) {
                console.debug( `updating ${ graphName }` );
                _customGraphSizes[ graphName ].width = size.width;
                _customGraphSizes[ graphName ].height = size.height;
            } else {
                console.debug( `creating ${ graphName }` );
                _customGraphSizes[ graphName ] = {
                    width: size.width,
                    height: size.height
                }
                //_customGraphSizes[ graphName ] = size ;
            }

            //console.log("layout.setSize() pre2 : _customGraphSizes", _customGraphSizes[ graphName ]) ;
            //console.log("layout.setSize() pre3 : _customGraphSizes", _customGraphSizes ) ;
            saveLayout( $graphContainer, $plotContainer );

            _dom.csapDebug( "_customGraphSizes", _customGraphSizes );


            //console.log( "layout.setSize() post: _customGraphSizes", _customGraphSizes );
        }
    };

    function isAGraphMaximized( $graphContainer ) {
        // console.log("isAGraphMaximized() : " + $( ".graphCheckboxes :checked", $graphContainer ).length )
        return $( ".graphCheckboxes :checked", $graphContainer ).length == 1;
    }

    function addContainer( $graphContainer, $plotContainer ) {

        //alert(`registered`) ;
        $plotContainer.sortable( {
            handle: '.graphTitle',
            update: function ( event, ui ) {
                //alert(`update called`) ;
                console.log( "panel moved: " + ui.helper );
                saveLayout( $graphContainer, $plotContainer );

                $( ".layout-top optgroup.report-views", $graphContainer ).trigger( "change" );

            }
        } );

        // $plotContainer.on( 'resize', function ( e ) {
        //     // otherwise resize window will be continuosly called
        //     e.stopPropagation();
        // } );
    }

    function loadPreferences() {

        _graphSettings = new Object();
        let paramObject = {
            "dummy": jsonToString( _graphSettings )
        }
        $.ajax( {
            dataType: "json",
            url: OS_URL + "/user/settings",
            //async: false,
            data: paramObject,
            success: function ( responseJson ) {
                // console.log( "loadPreferences():  " + jsonToString( responseJson ) );
                if ( responseJson && responseJson.response != undefined ) {
                    _graphSettings = responseJson.response;
                } else {
                    //  alertify.warning( "Warning: failed to load user preferencs " )
                    console.log( "loadPreferences(): User preferences not found" )
                }
            }
        } );

    }
    // push to csap event service for loading in other labs
    function publishPreferences( resetResource ) {

        if ( resetResource != false ) {
            delete _graphSettings[ resetResource ];
        }
        let paramObject = {
            "new": jsonToString( _graphSettings )
        }

        $.ajax( {
            method: "post",
            dataType: "json",
            url: OS_URL + "/user/settings",
            async: true,
            data: paramObject,
            success: function ( responseJson ) {
                alertify.notify( "Default view stored, and will be used in all Applications",
                    "success", 1 );
            },
            error: function ( jqXHR, textStatus, errorThrown ) {

                // handleConnectionError("Performance Intervals", errorThrown);
                // console.log("Performance Intervals" + JSON.stringify(jqXHR, null, "\t")) ;
                handleConnectionError( "settingsUpdate", errorThrown );
            }
        } );


    }

    function saveLayout( $graphContainer, $plotContainer ) {

        layoutSelectCheck( "Current*", $graphContainer );
        $( ".savePreferencesButton", $graphContainer ).show();
        $( ".layoutSelect", $graphContainer ).val( "Current*" );

        let allGraphs = new Array();

        $( ">div.plotPanel", $plotContainer ).each( function ( index ) {
            // console.log( "FOund: " + $( this ).data( "graphname" ) );
            let graphDetails = new Object();
            graphDetails.name = $( this ).data( "graphname" );
            graphDetails.size = _customGraphSizes[ graphDetails.name ];
            allGraphs.push( graphDetails );
        } );

        _graphSettings[ $graphContainer.data( "preference" ) ] = allGraphs;

    }


    function layoutSelectCheck( layoutName, $graphContainer ) {
        if ( $( ".layoutSelect option[value='" + layoutName + "']", $graphContainer ).length == 0 ) {
            let customOption = jQuery( '<option/>', {
                text: layoutName,
                value: layoutName
            } );

            $( ".layoutSelect", $graphContainer ).append( customOption );
            $( ".layoutSelect", $graphContainer ).val( layoutName );
        }

    }

    function restore( resourceGraph, $hostPlotContainer ) {

        if ( _graphSettings == null ) {

            if ( window.csapGraphSettings != undefined ) {
                console.log( "loadPreferences() skipping window.csapGraphSettings" );
                _graphSettings = {};
            } else {
                loadPreferences();
            }
        }

        let $rootContainer = $hostPlotContainer.parent().parent().parent();

        let baseContainerId = $rootContainer.data( "preference" );
        //console.log("baseContainerId: " + baseContainerId) ;

        if ( _graphSettings[ baseContainerId ] != null ) {
            layoutSelectCheck( "My Layout", $rootContainer );
        }

        let layoutSelected = $( ".layoutSelect", $rootContainer ).val();
        console.log( `restore() layoutSelected: ${ layoutSelected }` );

        if ( layoutSelected == "default" ) {
            return;
        }



        let settingsForGraphs = _graphSettings[ baseContainerId ];
        _dom.csapDebug( `settingsForGraphs`, settingsForGraphs );
        if ( !settingsForGraphs ) {
            return;
        }

        // customized view support for JMX, possible others.
        let customView = resourceGraph.getSelectedCustomView();
        if ( customView != null ) {




            console.log( `restore() customView` );

            $( "> div.plotPanel", $hostPlotContainer ).each( function ( index ) {
                let graphName = $( this ).data( "graphname" );
                //console.log("graphName: "+ graphName );
                if ( $.inArray( graphName, customView.graphs ) == -1 ) {
                    $( this ).hide();
                }

            } );

            // 
            // Outer loop provides ordering via the sortable api
            //   - make DEFAULT the customView Graphs however.....


            //let graphNameOrder = 

            let graphOrderedNames = customView.graphs;
            // graphs are double restored - so order on 2nd and later attempts

            if ( _customRestoreCount++ > 1 ) {
                // Use names ordered via sortable...
                graphOrderedNames = new Array();
                for ( let graphDetails of settingsForGraphs ) {
                    graphOrderedNames.push( graphDetails.name );
                }
                console.log( `Updated Ordering: ${ graphOrderedNames }` );
            }

            console.log( `Drawing: ${ graphOrderedNames }` );
            for ( let graphName of graphOrderedNames ) {
                if ( customView.graphs[ graphName ] ) {
                    console.log( `Warning: found unexpected name: ${ graphName }` );
                    continue;

                }
                //let graphName = customGraphOrder[ i ]
                let $targetGraph = $( '>div.' + graphName, $hostPlotContainer );
                $hostPlotContainer.append( $targetGraph );

                if ( customView?.graphSize && customView?.graphSize[ graphName ] != undefined ) {
                    let graphSize = customView?.graphSize[ graphName ];
                    //console.log( `Restoring graph ${ graphName } size from custom view: width: ${ graphSize.width }, height: ${ graphSize.height } ` );
                    // _customGraphSizes[ graphName ] = customView.graphSize[ graphName ];

                    //
                    //  Only on fresh load do a hard restore....
                    //
                    if ( !_customLayoutSelected[ graphName ] ) {
                        _customLayoutSelected[ graphName ] = true;
                        if (  _customGraphSizes[ graphName ] ) {
                            _customGraphSizes[ graphName ].width = graphSize.width;
                            _customGraphSizes[ graphName ].height = graphSize.height;
                        } else {
                            console.debug( `Custom graph sizes not found: ${ graphName }` ) ;

                            _customGraphSizes[ graphName ] = {
                                width: graphSize.width,
                                height: graphSize.height
                            }
                        }
                    }
                }
            }

            if ( _customRestoreCount == 2 ) {

                setTimeout( function() {
                    console.log( `Saving layout after initial load of custom view`) ; 
                    saveLayout( resourceGraph.getCurrentGraph(), $hostPlotContainer );
                }, 500 )
            }

            //console.log( `restore: _customGraphSizes`, _customGraphSizes );
            return;
        }


        let $plotContainer = $( ".ui-sortable", $rootContainer );
        console.log( "baseContainerId: " + baseContainerId
            + " graphs.length: " + settingsForGraphs.length
            + " Number of Containers: " + $plotContainer.length )

        for ( let graphDetails of settingsForGraphs ) {

            // console.log("Restoring: " + graphDetails.name) ;
            let $targetGraph = $( '>div.' + graphDetails.name, $hostPlotContainer );

            //	console.log( " $targetGraph: " + $targetGraph.attr( "id" ) + "  $plotContainer: " + $plotContainer.prop( 'className' ) )
            // $targetGraph.appendTo( $plotContainer );
            // do the move.
            $hostPlotContainer.append( $targetGraph );
            if ( graphDetails.size != undefined ) {
                _customGraphSizes[ graphDetails.name ] = graphDetails.size;
            }
        }
    }


}