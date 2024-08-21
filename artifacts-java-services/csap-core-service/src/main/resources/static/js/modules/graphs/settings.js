// define( [ "./graphLayout", "csapflot" ], function ( graphLayout, csapflot ) {


import _dom from "../utils/dom-utils.js";


import graphLayout from "./graphLayout.js"

import csapflot from "../utils/legacy-globals.js"


import utils from "../browser/utils.js"

import _dialogs from "../utils/dialog-utils.js";




const settings = graphs_settings();

export default settings


function graphs_settings() {

    _dom.logHead( "Module loaded" );

    console.log( "Module loaded: graphPackage/settings" );
    let startButtonUrl = uiSettings.baseUrl + "images/16x16/play.svg";
    let pauseButtonUrl = uiSettings.baseUrl + "images/16x16/pause.png";

    let _plotFilterTimer;

    let _lastLayoutTop = "default";

    let _syncAllGraphSettings = false;



    let isKeepPlaying = true;

    return {
        //
        uiComponentsRegistration: function ( resourceGraph ) {

            miscSetup( resourceGraph );

            zoomSetup( resourceGraph );

            layoutSetup( resourceGraph );

            datePickerSetup( resourceGraph );

            numberOfDaysSetup( resourceGraph );


            plotFilterSetup( resourceGraph );
        },

        applyPlotFilter: function ( resourceGraph ) {
            applyPlotFilter( resourceGraph );
        },
        //
        addCustomViews: function ( resourceGraph, customViews ) {
            addCustomViews( resourceGraph, customViews );
        },
        //
        dialogSetup: function ( settingsChangedCallback, $GRAPH_INSTANCE ) {

            dialogSetup( settingsChangedCallback, $GRAPH_INSTANCE );
        },
        //
        modifyTimeSlider: function ( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph ) {
            modifyTimeSlider( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph );
        },
        //
        addToolsEvents: function () {
            addToolsEvents();
        },
        //
        addContainerEvents: function ( resourceGraph, container ) {
            addContainerEvents( resourceGraph, container );
        },
        postDrawEvents: function ( $newGraphContainer, $GRAPH_INSTANCE, numDays ) {
            postDrawEvents( $newGraphContainer, $GRAPH_INSTANCE, numDays );
        },

        checkForGlobalDate: function ( $graphInstance ) {
            checkForGlobalDate( $graphInstance );
        }
    };

    function miscSetup( resourceGraph ) {


        let resizeTimer = 0;
        $( window ).resize( function () {

            if ( !resourceGraph.getCurrentGraph().is( ':visible' ) )
                return;
            clearTimeout( resizeTimer );
            resizeTimer = setTimeout( function () {
                console.log( "window Resized" );
                resourceGraph.reDraw();
            }, 300 );

        } );

        jQuery( '.numbersOnly', resourceGraph.getCurrentGraph() ).keyup( function () {
            this.value = this.value.replace( /[^0-9\.]/g, '' );
        } );

        // refreshButton
        $( ".refreshGraphs", resourceGraph.getCurrentGraph() ).click( function () {
            resourceGraph.settingsUpdated( true );
        } );
        $( ".show-as-rate", resourceGraph.getCurrentGraph() ).change( function () {
            resourceGraph.settingsUpdated();
        } );

        $( ".sampleIntervals", resourceGraph.getCurrentGraph() ).click( function () {
            resourceGraph.settingsUpdated();
        } );

        if ( $( "#global-graph-time-zone" ).length > 0 ) {
            let $graphTimeSelect = $( ".graphTimeZone", resourceGraph.getCurrentGraph() );
            $graphTimeSelect.empty();
            $graphTimeSelect.attr( `title`, "CSAP settings view will update for all graphs" );
            jQuery( '<option/>', {
                text: `Use Settings View`
            } ).appendTo( $graphTimeSelect );
        }

        $( ".useLineGraph,.flot-line-thickness,.graphTimeZone", resourceGraph.getCurrentGraph() ).change( function () {
            console.log( "settings changed" );
            resourceGraph.reDraw();
        } );


        $( ".graph-display-options .close-menu", resourceGraph.getCurrentGraph() ).click( function () {
            $( ".graph-display-options", resourceGraph.getCurrentGraph() ).hide();
            return false;
        } )

        $( ".graphOptions .tool-menu", resourceGraph.getCurrentGraph() ).click( function () {
            console.log( `showing advanced options` );
            let $showButton = $( this );
            let $menu = $( ".graph-display-options", resourceGraph.getCurrentGraph() );
            if ( $menu.is( ":visible" ) ) {
                $menu.hide();
            } else {
                $menu.show();

            }
            return false;
        } )


        let $layoutTopGroup = $( "select.layout-top optgroup.graph-layout", resourceGraph.getCurrentGraph() );
        let $menuLayout = $( ".layoutSelect option", resourceGraph.getCurrentGraph() );
        $menuLayout.each( function () {
            let $sourceOption = $( this );
            let $optionItem = jQuery( '<option/>', {
                value: $sourceOption.attr( "value" ),
                text: $sourceOption.text(),
            } );
            $layoutTopGroup.append( $optionItem );
        } );
        //$layoutTopGroup.parent().val( $menuLayout.val() );

    }

    function layoutSetup( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph();

        $( ".savePreferencesButton", $graphContainer ).click( function () {
            showSavePreferencesDialog( resourceGraph );
        } ).hide();

        $( ".layout-top", $graphContainer ).change( function () {

            let $layoutSelect = $( this );

            let $optionSelected = $( `option[value='${ $layoutSelect.val() }']`, $layoutSelect );
            let valueSelected = $layoutSelect.val();
            let isCustomReport = $optionSelected.data( "report" );
            let isStackGraphs = $optionSelected.data( "stack" );
            let isCsv = $optionSelected.data( "csv" );
            let isShowGrid = $optionSelected.data( "showgrid" );
            let isView = $optionSelected.data( "view" );
            console.log( `isCustomLayout: ${ isCustomReport } ` );

            if ( isCustomReport ) {
                _lastLayoutTop = valueSelected;
                let $customSelect = $( "select.customViews", $graphContainer );
                $customSelect.val( $layoutSelect.val() ).trigger( `change` );

            } else if ( isShowGrid ) {

                $( "input.show-flot-grid", $graphContainer ).trigger( 'click' );
                // $layoutSelect.val( _lastLayoutTop );
                layoutChanged( resourceGraph );

            } else if ( isCsv ) {

                $( "input.csv", $graphContainer ).trigger( 'click' );
                // $layoutSelect.val( _lastLayoutTop );
                layoutChanged( resourceGraph );

            } else if ( isStackGraphs ) {

                $( ".useLineGraph", $graphContainer ).trigger( 'click' );
                // $layoutSelect.val( _lastLayoutTop );


            } else if ( isView ) {

                $( ".zoomSelect", $graphContainer ).val( valueSelected ).trigger( 'change' );
                // $layoutSelect.val( _lastLayoutTop );


            } else {
                _lastLayoutTop = valueSelected;

                $( ".layoutSelect", $graphContainer )
                    .val( valueSelected );


                layoutChanged( resourceGraph );


            }
            $layoutSelect.val( "none" );
            //$(this).val( `menu`) ;
        } );

        $( ".layoutSelect", $graphContainer ).change( function () {
            layoutChanged( resourceGraph )
        } )
        // $( ".layoutSelect", $graphContainer ).selectmenu( {
        //     width: "10em",
        //     change: function () {
        //         layoutChanged( resourceGraph ) ;
        //     }
        // } );
    }

    function layoutChanged( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph();
        let selectedLayout = $( ".layoutSelect", $graphContainer ).val();
        console.log( "layout selected: " + selectedLayout );

        $( ".savePreferencesButton", $graphContainer ).show();
        switch ( selectedLayout ) {
            case "spotlight1Small":
                setGraphsSize( "18%", "15%", $graphContainer, 1 );
                break;
            case "spotlight2Small":
                setGraphsSize( "18%", "15%", $graphContainer, 2 );
                break;
            case "spotlight1Medium":
                setGraphsSize( "48%", "15%", $graphContainer, 1 );
                break;
            case "spotlight2Medium":
                setGraphsSize( "48%", "15%", $graphContainer, 2 );
                break;
            case "small":
                setGraphsSize( "20%", "15%", $graphContainer );
                break;
            case "smallWide":
                setGraphsSize( "100%", "15%", $graphContainer );
                break;
            case "medium":
                setGraphsSize( "30%", "25%", $graphContainer );
                break;
            case "mediumWide":
                setGraphsSize( "100%", "30%", $graphContainer );
                break;
            case "largeWide":
                setGraphsSize( "100%", "48%", $graphContainer );
                break;
        }

        resourceGraph.reDraw();
    }

    function addCustomViews( resourceGraph, customViews ) {

        let graphType = resourceGraph._metricType;
        console.log( `graphType: ${ graphType }` );

        // let customViews = customViewReport[ graphType ] ;
        // if ( ! customViews ) {
        //     return ;
        // }

        // if ( graphType == METRICS_APPLICATION) {

        //     customViews = customViews[ resourceGraph.getCurrentServiceName() ] ;

        // }

        let $graphContainer = resourceGraph.getCurrentGraph();
        let $customSelect = $( "select.customViews", $graphContainer );

        jQuery( '<option/>', {
            text: `all`
        } ).appendTo( $customSelect );


        let $layOutReportViews = $( ".layout-top optgroup.report-views", $graphContainer );
        $layOutReportViews.empty();

        jQuery( '<option/>', {
            text: `all`,
            value: `all`,
            "data-report": true
        } ).appendTo( $layOutReportViews );


        for ( let viewKeys in customViews ) {
            let optionItem = jQuery( '<option/>', {
                value: viewKeys,
                text: viewKeys
            } );
            $customSelect.append( optionItem );


            let $layoutTopItem = jQuery( '<option/>', {
                value: viewKeys,
                text: viewKeys,
                "data-report": true
            } );
            $layOutReportViews.append( $layoutTopItem );
        }


        $layOutReportViews.show();


        $customSelect.change( function () {

            let selectedView = $( this ).val();
            console.log( `customViews selected: ${ selectedView } ` );

            graphLayout.customLayoutSelected() ;

            let customView = resourceGraph.getSelectedCustomView();
            _dom.csapDebug( `customView definition`, customView) ;
            if ( customView?.stack ) {
                $( ".useLineGraph", $graphContainer ).prop("checked", true);
            } else {
                $( ".useLineGraph", $graphContainer ).prop("checked", false );
            }

            let layouts = "default";
            if ( selectedView != "all" ) {
                layouts = "mediumWide";
            }
            $( ".layoutSelect", $graphContainer ).val( layouts );
            $( ".layoutSelect", $graphContainer );
            layoutChanged( resourceGraph );
            // _currentResourceGraphInstance.reDraw();
        }
        );
    }

    function setGraphsSize( width, height, $graphContainer, spotIndex ) {
        // save all sizes
        $( ".plotContainer > div.plotPanel", $graphContainer ).each( function ( index ) {
            let graphName = $( this ).data( "graphname" );

            // console.log("setGraphsSize(): " + spotIndex + " graph: " + graphName + " index: " + index) ;
            let sizeObject = {
                width: width,
                height: height
            };

            if ( spotIndex == 1 && index == 0 ) {
                sizeObject.width = "100%";
                sizeObject.height = "50%";
            }
            if ( spotIndex == 2 && index <= 1 ) {
                sizeObject.width = "48%";
                sizeObject.height = "50%";
            }
            graphLayout.setSize( graphName, sizeObject, $graphContainer, $( this ).parent() )
        } );
    }

    function showSavePreferencesDialog( resourceGraph ) {
        // 

        if ( !alertify.graphPreferences ) {
            let message = "Saving current settings will enable these to be used on all labs in all lifecycles"
            let startDialogFactory = function factory() {
                return {
                    build: function () {
                        // Move content from template
                        this.setContent( message );
                        this.setting( {
                            'onok': function () {
                                let $graphContainer = resourceGraph.getCurrentGraph();

                                let resetResource = false;
                                if ( $( ".layoutSelect", $graphContainer ).val() == "default" ) {
                                    resetResource = $graphContainer.data( "preference" );
                                }
                                graphLayout.publishPreferences( resetResource );
                            },
                            'oncancel': function () {
                                alertify.warning( "Cancelled Request" );
                            }
                        } );
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Save current settings", className: alertify.defaults.theme.ok, key: 0 },
                            { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                            ],
                            options: {
                                title: "Save Current Layout :", resizable: false, movable: false, maximizable: false,
                            }
                        };
                    }

                };
            };
            alertify.dialog( 'graphPreferences', startDialogFactory, false, 'confirm' );
        }

        alertify.graphPreferences().show();
    }

    function zoomSetup( resourceGraph ) {
        let $graphContainer = resourceGraph.getCurrentGraph();
        let max = $( "#numSamples > option", $graphContainer ).length;
        let current = $( "#numSamples", $graphContainer ).prop( "selectedIndex" );

        let $zoomSelect = $( ".zoomSelect", $graphContainer );
        $zoomSelect.empty();

        $( "#numSamples > option", $graphContainer ).each( function () {

            let sampleValue = $( this ).attr( "value" );
            if ( $( this ).data( "usetext" ) ) {
                sampleValue = $( this ).text();
            }
            jQuery( '<option/>', {
                text: $( this ).text(),
                value: sampleValue
            } ).appendTo( $zoomSelect );
        } );

        let zoomChange = function () {
            console.log( "Zoom changed" );

            $( "#numSamples", $graphContainer ).val( $zoomSelect.val() );

            console.log( "Zoom changed: " + $( "#numSamples", $graphContainer ).val() );
            // alertify.notify(" Selected: " + $( this ).val())
            if ( $( "#numSamples", $graphContainer ).val() != "99999" ) {
                $( ".sliderContainer", $graphContainer ).show();
            } else {
                //$( ".useLineGraph", $graphContainer ).prop( "checked", "checked" );

                $( ".sliderContainer", $graphContainer ).hide();
            }

            resourceGraph.reDraw();
        }

        $zoomSelect.change( zoomChange );
        // $( ".zoomSelect", $graphContainer ).selectmenu( {
        //     width: "6em",
        //     change: zoomChange
        // } );

        $( ".meanFilteringSelect", $graphContainer ).selectmenu( {
            width: "4em",
            change: function () {
                resourceGraph.reDraw();
            }
        } );




    }

    function datePickerSetup( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph();


        let now = new Date();

        console.log( `Registering datepicker: local time: ${ now }  Us Central:  ${ csapflot.getUsCentralTime( now ) } ` );


        const dateChangeFunction = function () {
            // $(".daySelect", $GRAPH_INSTANCE).val("...");

            let $dateInput = $( this );

            $dateInput.addClass( "modified" );

            let currentDateSelected = $dateInput.val();

            let msOffset = $dateInput.datepicker( "getDate" ).getTime();
            console.log( `currentDateSelected: ${ currentDateSelected }  msOffset: ${ msOffset }` )
            let dayOffset = utils.calculateOffsetDays( msOffset );

            console.log( "dayOffset: " + dayOffset );

            $( ".useHistorical", $graphContainer ).prop( 'checked', true );
            //            $( ".historicalOptions", $graphContainer ).css(     "display", "inline-block" ) ;

            $( "#dayOffset", $graphContainer ).val( dayOffset );

            if ( $( ".numDaysSelect", $graphContainer ).val() == 0 ) {
                $( ".numDaysSelect", $graphContainer ).val( 1 );
            }
            //resourceGraph.settingsUpdated();
            $( ".numDaysSelect", $graphContainer ).trigger( "change" );

            if ( _syncAllGraphSettings ) {
                console.log( `Update value in all started graphs` );
                $( ".datepicker" ).each( function () {

                    if ( $dateInput.val() != currentDateSelected ) {
                        $dateInput.val( currentDateSelected ).trigger( "change" );
                    }
                } );
            }

            return false; // prevents link
        };

        //
        // Global registry;
        //
        let $globalDateInput = utils.getGlobalDate();
        if ( $globalDateInput ) {
            _dom.logArrow( "registering for global date changes" );
            $globalDateInput.change( dateChangeFunction );
        }

        // allow for per graph changes
        $( ".datepicker", $graphContainer ).datepicker( {
            defaultDate: csapflot.getUsCentralTime( now ),
            maxDate: '0',
            minDate: '-120'
        } );


        $( ".datepicker", $graphContainer ).change( dateChangeFunction );

    }

    function checkForGlobalDate( $graphInstance ) {
        let $globalDateInput = utils.getGlobalDate();


        if ( $globalDateInput ) {
            _dom.logArrow( `checkForGlobalDate found global date: '${ $globalDateInput.val() }'` );

            // do no use global if local overrides
            let $graphDateInput = $( ".datepicker", $graphInstance );
            if ( $globalDateInput.val() != ""
                && $graphDateInput.val() == "" ) {


                $globalDateInput.addClass("modified") ;

                let dateSelected = $globalDateInput.datepicker( "getDate" );
                _dom.logSection( `Updateing date: ${ dateSelected }` );
                $( ".useHistorical", $graphInstance )
                    .prop( 'checked', true );


                if ( $( ".numDaysSelect", $graphInstance ).val() == 0 ) {
                    $( ".numDaysSelect", $graphInstance ).val( 1 );
                }


                // 06/21/2022
                // let mmddyyyy = $globalDateInput.val().split( "/" ) ;
                // let timeOffset = new Date( mmddyyyy[2], mmddyyyy[0], mmddyyyy[1] - 1);
                let dayOffset = utils.calculateOffsetDays( dateSelected.getTime() );
                console.log( ` dayOffset: ${ dayOffset }` );
                $( "#dayOffset", $graphInstance ).val( dayOffset );
            }

        }
    }

    function applyPlotFilter( resourceGraph ) {


        let $graphContainer = resourceGraph.getCurrentGraph();


        if ( $( ".customViews", $graphContainer ).val() != "all" ) {
            return;
        }


        let $filterInput = $( ".graph-filter", $graphContainer );
        let $clearButton = $( "button", $filterInput.parent() );

        let $plotsDisplayed = $( 'div.plotPanel', $graphContainer );

        let includeFilter = $filterInput.val();

        console.debug( ` includeFilter: ${ includeFilter } ` );



        if ( includeFilter.length > 0 ) {
            $filterInput.addClass( "modified" );
            $clearButton.css("visibility", "visible");
            $plotsDisplayed.hide();
            let filterEntries = includeFilter.split( "," );

            for ( let filterItem of filterEntries ) {
                // console.debug( `filterItem: ${ filterItem }` ) ;
                if ( filterItem.startsWith( "!" ) && filterItem.length > 1 ) {
                    $( `div.graphTitle:ignoreCaseForNotHidingPlot("${ filterItem.substring( 1 ) }")`, $plotsDisplayed ).parent().show();
                } else {
                    $( `div.graphTitle:ignoreCaseForHidingPlot("${ filterItem }")`, $plotsDisplayed ).parent().show();
                }
            }

        } else {
            $plotsDisplayed.show();
            $filterInput.removeClass( "modified" );
            $clearButton.css("visibility", "hidden");
        }
    }

    function plotFilterSetup( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph();
        let $filterInput = $( ".graph-filter", $graphContainer );
        $filterInput.parent().attr( "title", "filter output; comma separated items will be or'ed together. Optional: !csap will exclude csap" );

        let $clearButton = jQuery( '<button/>', { class: "csap-icon csap-remove" } )
            .appendTo( $filterInput.parent() )
            .click( function () {
                $filterInput.val( "" );
                $filterInput.trigger( "keyup" )
            } );


        jQuery.expr[ ':' ].ignoreCaseForHidingPlot = function ( plotPanel, i, m ) {

            let plotText = jQuery( plotPanel ).parent().text().toUpperCase();
            let filter = m[ 3 ].toUpperCase();

            console.debug( ` filter: ${ filter }  plotText: ${ plotText } ` );

            return plotText.indexOf( filter ) >= 0;
        };


        jQuery.expr[ ':' ].ignoreCaseForNotHidingPlot = function ( plotPanel, i, m ) {

            let plotText = jQuery( plotPanel ).parent().text().toUpperCase();
            let filter = m[ 3 ].toUpperCase();

            console.log( ` filter: ${ filter }  plotText: ${ plotText } ` );

            return plotText.indexOf( filter ) == -1;
        };


        $filterInput.off().keyup( function () {
            //console.log( "Applying template filter" ) ;
            clearTimeout( _plotFilterTimer );
            _plotFilterTimer = setTimeout( function () {
                applyPlotFilter( resourceGraph );
            }, 500 );
        } );

    }

    // binds the select
    function numberOfDaysSetup( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph();

        uiSetupForNumberOfDays( $graphContainer );

        // Handle change events
        $( ".numDaysSelect", $graphContainer ).change( function () {
            let numberOfDaysSelected = $( ".numDaysSelect", $graphContainer ).val();

            console.log( "setupNumberOfDaysChanged(): " + numberOfDaysSelected );

            if ( numberOfDaysSelected == 0 ) {
                $( ".useHistorical", $graphContainer ).prop( 'checked', false );
                utils.getGlobalDate().val("");
                $( "#dayOffset", $graphContainer ).val( 0 );
            } else {
                $( ".useHistorical", $graphContainer ).prop( 'checked', true );
            }

            // updates the dropDown
            $( "#numberOfDays", $graphContainer )
                .val( numberOfDaysSelected );

            resourceGraph.settingsUpdated();

            if ( _syncAllGraphSettings ) {
                // update ALL instances:
                console.log( `Update value in all started graphs` );
                $( ".numDaysSelect" ).each( function () {

                    if ( $( this ).val() != numberOfDaysSelected ) {
                        $( this ).val( numberOfDaysSelected ).trigger( "change" );
                    }
                } );
            }


            return false; // prevents link
        } );
    }

    function uiSetupForNumberOfDays( $graphContainer ) {
        if ( $( ".numDaysSelect", $graphContainer ).val() == 0 ) {
            $( ".useHistorical", $graphContainer ).prop( 'checked', false );
            //            $( ".historicalOptions", $graphContainer ).hide() ;
        } else {
            $( ".useHistorical", $graphContainer ).prop( 'checked', true );
            //            $( ".historicalOptions", $graphContainer ).css( "display",  "inline-block" ) ;
        }

        if ( $( ".numDaysSelect", $graphContainer ).length < 5 ) {
            for ( let i = 2; i <= 14; i++ ) {
                $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="' + i + '" >' + i + " days</option>" );
            }

            $( ".numDaysSelect", $graphContainer ).append(
                '<option value="21" >3 Weeks</option>' );
            $( ".numDaysSelect", $graphContainer ).append(
                '<option value="28" >4 Weeks</option>' );
            $( ".numDaysSelect", $graphContainer ).append(
                '<option value="42" >6 Weeks</option>' );
            $( ".numDaysSelect", $graphContainer ).append(
                '<option value="56" >8 Weeks</option>' );
            $( ".numDaysSelect", $graphContainer ).append(
                '<option value="112" >16 Weeks</option>' );
            $( ".numDaysSelect", $graphContainer ).append(
                '<option value="256" >32 Weeks</option>' );
            $( ".numDaysSelect", $graphContainer ).append(
                '<option value="336" >48 Weeks</option>' );
            $( ".numDaysSelect", $graphContainer ).append(
                '<option value="999" >All</option>' );
        }
    }

    function dialogSetup( settingsChangedCallback, $GRAPH_INSTANCE ) {


        // $('.padLatest', $GRAPH_INSTANCE).prop("checked", false) ;

        $( ".graph-display-options .sampleIntervals, .padLatest" ).click(
            function () {
                $( ".graph-display-options .useAutoInterval" )
                    .prop( 'checked', false );
            } );

        $( '.showSettingsDialogButton', $GRAPH_INSTANCE ).click( function () {

            try {
                dialogShow( settingsChangedCallback, $GRAPH_INSTANCE );
            } catch ( e ) {
                console.log( e );
            }

            $( ".pointToolTip" ).hide();


            return false; // prevents link
        } );
    }

    /**
     * Static function:  Alertify usage is non-trivial as scope for function
     * is inside ResourceGraph instances, and there are multple instances of Graph.
     * Unlike typical usage, Resource Graph content is pulled in when launched, and moved
     * back to original DOM location in order for 
     */
    function dialogShow( settingsChangedCallback, $GRAPH_INSTANCE ) {

        let dialogId = "graphSettingsDialog";

        $( ".graph-hover-container" ).hide();

        if ( !alertify.graphSettings ) {

            console.log( "Building: dialogId: " + dialogId );

            let configuration = {
                content: '<div id="' + dialogId + '"></div>',

                onresize: function ( dialogWidth, dialogHeight ) {
                    $( ".resourceConfigDialog", "#" + dialogId )
                        .css( "height", dialogHeight - 100 )
                        .css( "width", dialogWidth - 20 );

                },

                // onclose: function () {
                //     // Settings moved back to original location
                //     $( ".resourceConfig", $GRAPH_INSTANCE ).append( $( ".resourceConfigDialog", "#" + dialogId ) );
                //     settingsChangedCallback();
                // }
            }
            let csapDialogFactory = _dialogs.dialog_factory_builder( configuration );

            alertify.dialog( 'graphSettings', csapDialogFactory, false, 'alert' );


        }

        let settingsDialog = alertify.graphSettings();

        // Settings from associated ResourceGraph moved into dialog
        $( "#" + dialogId ).append( $( ".resourceConfigDialog", $GRAPH_INSTANCE ) );

        settingsDialog.setting( {
            'onclose': function () {
                // Settings moved back to original location
                $( ".resourceConfig", $GRAPH_INSTANCE ).append( $( ".resourceConfigDialog", "#" + dialogId ) );
                settingsChangedCallback();
            }
        } );


        $( ".graph-display-options" ).hide();

        settingsDialog.show();
    }

    function modifyTimeSlider( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph ) {

        if ( sampleTimeArray.length <= 0 ) {

            alertify
                .alert( "No data available in selected range. Select another range or try again later." );
            return;
        }
        let maxItems = sampleTimeArray.length - 1;

        let d = new Date( parseInt( descTimeArray[ maxItems ] ) );
        let mins = d.getMinutes();
        if ( mins <= 9 )
            mins = "0" + mins;
        let formatedDate = d.getHours() + ":" + mins + " "
            + $.datepicker.formatDate( 'M d', d );

        $( ".sliderTimeStart", $newGraphContainer ).val( formatedDate );

        // alert (maxItems + " timerArray[0]: " + timerArray[0] + "
        // timerArray[maxItems]:" + timerArray[maxItems]) ;
        // alert (new Date(parseInt(sampleTimeArray[0]))) ;

        let hostAuto = $( ".autoRefresh", $newGraphContainer );

        let minSlider = 0;
        let numSamples = $( "#numSamples", resourceGraph.getCurrentGraph() ).val();
        if ( sampleTimeArray.length > numSamples ) {
            minSlider = numSamples - 5;
        } else if ( sampleTimeArray.length > 10 ) {
            minSlider = 10;
        }

        let sliderConfig = {
            value: maxItems,
            min: minSlider,
            max: maxItems,
            step: 1,
            slide: function ( event, ui ) {
                //					 setSliderLabel( $newGraphContainer,
                //								descTimeArray[maxItems - ui.value] );

                resourceGraph.clearRefreshTimers();
                sliderUpdatePosition( $slider, ui.value, resourceGraph, sampleTimeArray, descTimeArray );
            },
            stop: function ( event, ui ) {
                resourceGraph.clearRefreshTimers();
                sliderUpdatePosition( $slider, ui.value, resourceGraph, sampleTimeArray, descTimeArray );
            }
        }


        let $slider = $( ".resourceSlider", $newGraphContainer ).slider( sliderConfig );


        $( ".playTimelineButton", $newGraphContainer ).off().click( function () {

            let $buttonImage = $( "img", $( this ) );
            resourceGraph.clearRefreshTimers();

            if ( $buttonImage.attr( "src" ) == startButtonUrl ) {
                isKeepPlaying = false;
                $buttonImage.attr( "src", pauseButtonUrl );
                let currentLocation = $slider.slider( "value" );
                ;
                if ( currentLocation > ( maxItems - 10 ) )
                    $slider.slider( "value", 0 ); // restart or resume
                setTimeout( function () {
                    isKeepPlaying = true;
                    playSlider( 1, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray );
                }, 500 )
            } else {
                isKeepPlaying = false;
                $buttonImage.attr( "src", startButtonUrl );
            }


        } );
        $( ".playTimelineBackButton", $newGraphContainer ).off().click( function () {

            let $buttonImage = $( "img", $( this ) );
            resourceGraph.clearRefreshTimers();

            if ( $buttonImage.attr( "src" ) == startButtonUrl ) {
                isKeepPlaying = false;
                $buttonImage.attr( "src", pauseButtonUrl );
                let currentLocation = $slider.slider( "value" );
                let zoomSetting = $( "#numSamples", resourceGraph.getCurrentGraph() ).val();
                if ( ( currentLocation - zoomSetting ) <= 10 )
                    $slider.slider( "value", maxItems ); // restart or resume
                setTimeout( function () {
                    isKeepPlaying = true;
                    playSlider( -1, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray );
                }, 500 )
            } else {
                isKeepPlaying = false;
                $buttonImage.attr( "src", startButtonUrl );
            }


        } );

        sliderUpdatePosition( $slider, maxItems, resourceGraph, sampleTimeArray, descTimeArray );
    }

    function playSlider( offset, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray ) {

        let $graphContainer = resourceGraph.getCurrentGraph();
        let newPosition = $slider.slider( "value" ) + offset;

        let zoomSetting = $( "#numSamples", $graphContainer ).val();
        //		  console.log( "Starting to play: " + newPosition + " max:" + descTimeArray.length
        //					 + " samples: " + zoomSetting );

        if ( offset > 0 && newPosition >= descTimeArray.length )
            isKeepPlaying = false;
        if ( offset < 0 && ( newPosition - zoomSetting ) <= 0 )
            isKeepPlaying = false;
        if ( !isKeepPlaying ) {
            $buttonImage.attr( "src", startButtonUrl );
            return;
        }


        sliderUpdatePosition( $slider, newPosition, resourceGraph, sampleTimeArray, descTimeArray );

        // do it again
        let delay = 5000 / zoomSetting;

        setTimeout( function () {
            playSlider( offset, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray );
        }, delay );

    }

    function sliderUpdatePosition( $slider, position, resourceGraph, sampleTimeArray, descTimeArray ) {

        let reversePosition = sampleTimeArray.length - position;
        // set the label

        setSliderLabel( resourceGraph.getCurrentGraph(), descTimeArray[ reversePosition ] );

        // move the slider
        $slider.slider( "value", position );
        let host = $slider.parent().parent().parent().data( "host" );


        // console.log( `sliderUpdatePosition() host: ${ host },  reversePosition: ${ reversePosition }` ) ;

        // console.log("host: " + host ) ;
        // move the grapsh 
        let graphsArray = resourceGraph.getDataManager().getHostGraph( host );

        //console.log("Slider Modified: updating graphs: ${ graphsArray.length } ") ;

        if ( graphsArray == undefined ) {
            return; // initial rendering
        }

        for ( let plot of graphsArray ) {
            // Custom Hook into FLOT nav plugin.

            if ( plot.jumpX ) {
                //                 console.log( `jumpX enabled into plot` ) ;
                plot.jumpX( {
                    // sample times are in reverse order
                    x: parseInt( sampleTimeArray[ reversePosition ] )
                } );
            } else {
                console.log( `jumpX disabled` );
            }
        }
    }

    function setSliderLabel( $newGraphContainer, newTime ) {

        let d = new Date( parseInt( newTime ) );
        let mins = d.getMinutes();
        if ( mins <= 9 )
            mins = "0" + mins;
        let formatedDate = d.getHours() + ":" + mins + " "
            + $.datepicker.formatDate( 'M d', d );

        // alert( formatedDate) ;
        $( ".sliderTimeCurrent", $newGraphContainer ).val( formatedDate );

    }

    function addToolsEvents() {

        $( '.hostLaunch' ).click(
            function () {


                let linkHost = $( this ).parent().parent().parent().data( "host" );
                let baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, linkHost );
                let theUrl = baseUrl + "/app-browser?u=1";
                openWindowSafely( theUrl, linkHost + "Stats" );

                return false; // prevents link
            } );
    }

    function addContainerEvents( resourceGraph, container ) {
        if ( resourceGraph.isCurrentModePerformancePortal() ) {
            $( '.clearMetrics', container ).hide();
            $( ".autoRefresh" ).hide();
        }

        $( '.clearMetrics', container ).click( function () {

            let containerHostName = $( this ).closest( ".hostPerf" ).data( "host" );

            let message = "Clear metrics removes all but the last 2 items. Clicking refresh will reload data from the server. ";
            alertify.notify( message );

            resourceGraph.getDataManager().clearHostData( containerHostName );

            //  _metricsJsonCache[linkHost] = undefined;

            setTimeout( function () {
                resourceGraph.getMetrics( 2, containerHostName );
                //resourceGraph.reDraw() ;
            }, 1000 );

        } );

        let hostAuto = $( ".autoRefresh", container );
        hostAuto.change( function () {
            // console.log( "hostAuto.is(':checked')" + hostAuto.is( ':checked' ) );
            if ( hostAuto.is( ':checked' ) ) {
                resourceGraph.getMetrics( 1, container.data( "host" ) );
            } else {
                // alert("Clearing timer" + newHostContainerJQ.data("host")
                // ) ;
                resourceGraph.clearRefreshTimers( container.data( "host" ) );
            }
            return false; // prevents link
        } );
    }

    function postDrawEvents( $newGraphContainer, $GRAPH_INSTANCE, numDays ) {
        let d = new Date();

        // ugly - but prefix single digits with a 0 consistent with times
        let curr_hour = ( "0" + d.getHours() ).slice( -2 );
        let curr_min = ( "0" + d.getMinutes() ).slice( -2 );
        let curr_sec = ( "0" + d.getSeconds() ).slice( -2 );
        $( ".refresh", $newGraphContainer ).html(
            "refreshed: " + curr_hour + ":" + curr_min + ":" + curr_sec );

        $( '.useHistorical', $GRAPH_INSTANCE ).off( 'change' ).change(
            function () {
                // console.log("Toggling Historical") ;
                $( '.historicalContainer', $GRAPH_INSTANCE ).toggle();
            } );


        // Finally Update the calendar based on available days
        $( ".datepicker", $GRAPH_INSTANCE ).datepicker( "option", "minDate",
            1 - numDays );


    }
}