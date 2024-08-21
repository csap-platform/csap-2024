import _dom from "../../utils/dom-utils.js";

import _net from "../../utils/net-utils.js";

import { aceEditor } from "../../libs/file-editor.js"

import _dialogs from "../../utils/dialog-utils.js";

import utils from "../utils.js"


// const stats = service_statistics();

// export default stats


// function service_statistics() {


_dom.logHead( "Module loaded" );

export default class Statistics {


    serviceName;
    hostName;
    $container;

    initialized = false;

    _statsEditor;

    _latestListing;

    _last_report;

    _csv_report_text = "" ;

    _defaultReport = `bootstats_per_tenant`;

    constructor( $container ) {

        this.$container = $container;

        this.$options = $( "div.options", this.$container );

        this.$statsServiceSelect = $( `#statistics-service`, this.$options );
        this.$statsFolderSelect = $( `#statistics-folder`, this.$options );
        this.$statsFilesSelect = $( `#statistics-files`, this.$$statsFilesSelect );
        this.$statsFileGroup = $( `#file-group`, this.$statsFilesSelect );
        this.$statsReportGroup = $( `#report-group`, this.$statsFilesSelect );
        this.$statsFileLimit = $( `#statistics-limit`, this.$options );
        this.$linesLoaded = $( `#statistics-lines-loaded`, this.$options );

        this.$statsEditorContainer = $( `#statistics-editor`, this.$statsTab );
        this.$statsBrowser = $( `#statistics-browser`, this.$statsTab );

        this.$closeSettingsButton = $( '.close-menu', this.$container );

        this.$fixedColumns = $( 'input.fixed-column', this.$container );
        this.$convertFriendly = $( 'input.convert-unit-columns', this.$container );
        this.$pruneEmptyColumns = $( 'input.prune-empty-columns', this.$container );
        this.$pruneIdColumns = $( 'input.prune-id-columns', this.$container );
        this.$mergeStatsView= $( 'input.merge-stats-view', this.$container );
        this.$alternateBasePath= $( 'input.alternate-stats-location', this.$container );
        this.$discoveredFolders= $( 'select.discovered-stat-folders', this.$container );



    }

    load( forceHostRefresh, serviceName, hostName, allServiceNames ) {

        console.log( `serviceName: ${ serviceName } allServiceNames: ${ allServiceNames }` );

        if ( serviceName != this.serviceName
            || hostName != this.hostName ) {
            forceHostRefresh = true;
        }
        this.serviceName = serviceName;
        this.hostName = hostName;

        if ( allServiceNames ) {
            this.$statsServiceSelect.empty().show();
            for ( let selServiceName of allServiceNames ) {
                let attributes = {
                    value: selServiceName,
                    html: selServiceName
                };
                if ( serviceName === selServiceName ) {
                    attributes.selected = "selected";
                }
                jQuery( '<option/>', attributes ).appendTo( this.$statsServiceSelect );
            }
            let _me = this;
            this.$statsServiceSelect.off().change( function () {
                _me.serviceName = $( this ).val();
                console.log( ` updated ${ _me.serviceName }` );
                _me.$statsFilesSelect.trigger( "change" );
            } );
        }

        this.info();

        if ( !this.initialized ) {
            this.initialize();
            this.initialized = true;
        }

        if ( forceHostRefresh ) {
            return this.loadStatsFolder();
        }

        return;

    }

    info() {
        _dom.logArrow(
            `this.serviceName: ${ this.serviceName }`,
            `this.hostName: ${ this.hostName }`,
            `this.$container: ${ this.$container }` );
    }


    initialize() {

        let _me = this;

        this.buildStatsCombo();

        this.$statsEditorContainer.hide();
        this.$statsBrowser.hide();

        $( "#copy-stats-table", _me.$container ).click( function () {

            let $activeTable = $( "table:visible", _me.$container );
            utils.copyItemToClipboard( $activeTable );

        } );


        this.$statsFilesSelect.change( function () {
            _me.loadStatsContent( $( this ).val() );
        } )

        this.$statsFileLimit.change( function () {

            _me.$statsFilesSelect.trigger( "change" );
        } );

        this._statsEditor = aceEditor.edit( this.$statsEditorContainer.attr( `id` ) );

        this._statsEditor.setOptions( utils.getAceDefaults( "ace/mode/yaml", true ) );


        $( '#stats-customize', this.$container ).click( function () {
            _me.settingsShow();
        } )


        _dom.loadScript( `${ BASE_URL }webjars/papaparse/5.3.2/papaparse.js`, true );

        console.log( `this.$closeSettingsButton: ${ this.$closeSettingsButton.length }` );

    }

    settingsShow() {


        let _me = this;

        let graphId = "statsSettingsDialog";

        console.log( `showing: ${ graphId }` )

        let $helpDialog = $( '#stats-dialog', this.$container );

        if ( !alertify[ graphId ] ) {
            console.log( `building: ${ graphId }` )

            let configuration = {
                content: $helpDialog[ 0 ],


                onresize: function () {
                },
                getWidth: function () {
                    return Math.round( $helpDialog.outerWidth( true ) ) + 20;
                },
                getHeight: function () {
                    return Math.round( $helpDialog.outerHeight( true ) ) + 0;
                },


                onclose: function () {
                    _me.buildReportView( _me._last_report );
                }

            }
            let csapDialogFactory = _dialogs.dialog_factory_builder( configuration );

            alertify.dialog( graphId, csapDialogFactory, false, 'alert' );


        }

        let settingsDialog = alertify[ graphId ]();
        settingsDialog.show();

        this.$closeSettingsButton.off().click( function () {
            settingsDialog.close();
        } )
    }

    buildStatsCombo() {


        let _me = this;

        let customName = "stats-combo";

        $.widget( `custom.${ customName }`, {
            _create: function () {
                this.wrapper = $( "<span>" )
                    .addClass( "custom-combobox" )
                    .insertAfter( this.element );

                this.element.hide();
                this._createAutocomplete();
                this._createShowAllButton();
            },

            _createAutocomplete: function () {


                console.log( `build _createAutocomplete` );

                let selected = this.element.children( ":selected" ),
                    value = selected.val() ? selected.text() : "";

                this.input = $( "<input>" )
                    .appendTo( this.wrapper )
                    .val( value )
                    .attr( "title", "" )
                    .addClass( "custom-combobox-input ui-widget ui-widget-content ui-state-default ui-corner-left" )
                    .autocomplete( {
                        delay: 0,
                        minLength: 0,
                        source: $.proxy( this, "_source" )
                    } )
                    .click( function () {
                        $( this ).select();
                    } )
                    .tooltip( {
                        classes: {
                            "ui-tooltip": "ui-state-highlight"
                        }
                    } );

                this._on( this.input, {
                    autocompleteselect: function ( event, ui ) {
                        ui.item.option.selected = true;
                        this._trigger( "select", event, {
                            item: ui.item.option
                        } );
                    },

                    autocompletechange: "_removeIfInvalid"
                } );
            },

            _createShowAllButton: function () {
                let input = this.input,
                    wasOpen = false;

                $( "<a>" )
                    .attr( "tabIndex", -1 )
                    .attr( "title", "Show All Items" )
                    .tooltip()
                    .appendTo( this.wrapper )
                    .button( {
                        icons: {
                            primary: "ui-icon-triangle-1-s"
                        },
                        text: false
                    } )

                    .removeClass( "ui-corner-all" )

                    .addClass( "custom-combobox-toggle ui-corner-right" )

                    .on( "mousedown", function () {
                        wasOpen = input.autocomplete( "widget" ).is( ":visible" );
                    } )

                    .on( "click", function () {
                        input.trigger( "focus" );

                        // Close if already visible
                        if ( wasOpen ) {
                            return;
                        }

                        console.log( `invoking autocomplete` );

                        // Pass empty string as value to search for, displaying all results
                        input.autocomplete( "search", "" );
                    } );
            },

            _source: function ( request, response ) {

                console.log( `build source2` );

                let matcher = new RegExp( $.ui.autocomplete.escapeRegex( request.term ), "i" );
                //                response( this.element.children( "option" ).map( function () {
                response( $( "option", _me.$statsFolderSelect ).map( function () {
                    let text = $( this ).text();
                    //                    console.log( `build source: ${text}`) ;
                    if ( this.value && ( !request.term || matcher.test( text ) ) )
                        return {
                            label: text,
                            value: text,
                            option: this
                        };
                } ) );
            },

            _removeIfInvalid: function ( event, ui ) {

                // Selected an item, nothing to do
                if ( ui.item ) {
                    return;
                }

                // Search for a match (case-insensitive)
                let value = this.input.val(),
                    valueLowerCase = value.toLowerCase(),
                    valid = false;
                this.element.children( "option" ).each( function () {
                    if ( $( this ).text().toLowerCase() === valueLowerCase ) {
                        this.selected = valid = true;
                        return false;
                    }
                } );

                // Found a match, nothing to do
                if ( valid ) {
                    return;
                }

                // Remove invalid value
                this.input
                    .val( "" )
                    .attr( "title", value + " didn't match any item" )
                    .tooltip( "open" );
                this.element.val( "" );
                this._delay( function () {
                    this.input.tooltip( "close" ).attr( "title", "" );
                }, 2500 );
                this.input.autocomplete( "instance" ).term = "";
            },

            _destroy: function () {
                this.wrapper.remove();
                this.element.show();
            }
        } );

        console.log( `Building combo box for ${ $( "option", this.$statsFolderSelect ).length } items` );
        this.$statsFolderSelect[ customName ]( {
            select: function ( event, ui ) {
                _me.$statsFolderSelect.trigger( "change" );
            }
        } );

        this.$statsFolderSelect.change( function () {
            console.log( `statsFolderSelect updated ${ _me.$statsFolderSelect.val() }  ` );
            _me._csv_report_text="" ;
            _me.loadStatsFiles( _me,  _me.$statsFolderSelect.val() );
            //alertify.notify(`value updated ${ $statsFolderSelect.val() }`) ;
        } )

    }


    loadStatsFolder() {

        let _me = this;

        let currentProject = utils.getActiveProject();

        //        if ( currentProject !== _lastProject ) {
        //            forceHostRefresh = true ;
        //        }

        let parameters = {
            hostName: this.hostName,
            serviceName: this.serviceName,
            statsPath: "",
            alternateBasePath: this.$alternateBasePath.val()
        };

        console.log( `loadStatsFolder - getting listing ` ) ;

        let viewLoadedPromise = _net.httpGet( utils.getFileUrl() + "/service/stats", parameters );

        viewLoadedPromise
            .then( statsReport => {

                console.log( `listing:  ${ Object.keys( statsReport ) }`  )

                if ( statsReport.error ) {
                    alertify.csapInfo( `Unable to get listing - verify log folder exists.`
                    + `\n\n Alternately - specify an alternate location using the customize option\n\n ${ statsReport.error }` )
                    return ;
                }

                if ( statsReport.discoveredFolders ) {
                    this.$discoveredFolders.empty() ;

                    jQuery( '<option/>', {
                        text: "Source...",
                        val: "default"
                    } ).appendTo( this.$discoveredFolders );
                    jQuery( '<option/>', {
                        text: "default",
                        val: "default"
                    } ).appendTo( this.$discoveredFolders );

                    this.$discoveredFolders.off().change( function() {
                        _me.$alternateBasePath.val( $(this).val() )
                        _me.$discoveredFolders.val("default") ;
                        alertify.closeAll() ;
                        _me.$statsFilesSelect.trigger( "change" );
                    })

                    for ( let previousFolder of statsReport.discoveredFolders ) {

                        let pathParts = previousFolder.split( `/` ) ;
                        let desc = previousFolder ;
                        if ( pathParts.length > 2 ) {
                            desc =  pathParts [ pathParts.length - 2 ] ;
                        }

                        jQuery( '<option/>', {
                            value: previousFolder,
                            text: desc
                        } ).appendTo( this.$discoveredFolders );
                    }
                }

                if ( statsReport.listing ) {
                    this.$statsFolderSelect.empty();

                    let defaultPath = false;

                    if ( statsReport.docker ) {
                        defaultPath = "missing-stats";
                        jQuery( '<option/>', {
                            value: defaultPath,
                            selected: "selected",
                            text: "stats folder not found"
                        } ).appendTo( this.$statsFolderSelect );

                        this.$statsFolderSelect.data( 'combobox', 'refresh' );
                        $( "input.custom-combobox-input", _me.$options )
                            .val( "stats folder not found" )
                            .trigger( "change" );

                        this.buildViewlisting( statsReport ) ;
                        return ;
                    }


                    for ( let item of statsReport.listing ) {
                        let $option = jQuery( '<option/>', {
                            value: item.path,
                            text: item.name
                        } );

                        // oms_task_stats
                        if ( item.name === _me._defaultReport ) {
                            defaultPath = item.path;
                        }

                        this.$statsFolderSelect.append( $option );
                    }
                    this.$statsFolderSelect.sortSelect()
                    //buildStatsCombo() ;
                    this.$statsFolderSelect.data( 'combobox', 'refresh' );

                    if ( defaultPath ) {
                        setTimeout( function () {
                            $( "input.custom-combobox-input", _me.$options )
                                .val( _me._defaultReport )
                                .trigger( "change" );
                            _me.$statsFolderSelect
                                .val( defaultPath )
                                .trigger( "change" );
                        }, 200 )
                    }
                }

            } )
            .catch( ( e ) => {
                console.warn( e );
            } );
        ;


        return viewLoadedPromise;

    }

    buildViewlisting( statsReport ) {
        _dom.csapDebug( `statsReport`, statsReport )


        if ( statsReport.listing ) {

            this._latestListing = statsReport.listing;
            this.$statsFileGroup.empty();

            let isDocker=false ;
            for ( let item of statsReport.listing ) {
                if ( statsReport.docker ) isDocker=true ;
                let $option = jQuery( '<option/>', {
                    value: item.path,
                    "data-file": true,
                    "data-docker": isDocker,
                    text: item.name
                } );

                this.$statsFileGroup.append( $option );
            }

            if ( isDocker ) {
                this.$statsReportGroup.hide() ;
                this.$statsFilesSelect.val( statsReport.listing[0].path ) ;
            } else {
                this.$statsReportGroup.show() ;
            }
        }

        this.$statsFilesSelect.trigger( "change" );
    }

    loadStatsFiles( _me, statsFolder ) {


        let parameters = {
            hostName: _me.hostName,
            serviceName: _me.serviceName,
            statsPath: statsFolder,
            alternateBasePath: this.$alternateBasePath.val()
        };


        let theStatsUrl = utils.getFileUrl() + "/service/stats" ;
        _net.httpGet( theStatsUrl, parameters )
            .then( statsReport => {
                _me.buildViewlisting( statsReport )
            } )
            .catch( ( e ) => {
                _dom.logError ( `Failed retrieving stats ${ theStatsUrl }`, e ) ;
            } );
        ;


        return;

    }


    isReportSelected() {

        let $optionSelected = $( "option:selected", this.$statsFilesSelect );

        if ( $optionSelected.data( "report" ) ) {
            return true;
        }

        return false;
    }


    loadStatsContent( statsPath ) {

        console.log( `loadStatsContent: ${ statsPath }` );

        let $selectedItem=$( "option:selected", this.$statsFolderSelect ) ;
        utils.loading( `Loading ${ $selectedItem.text() }: ${ statsPath }` );

        let specPath = null;


        let isReportSelected = this.isReportSelected();

        if ( isReportSelected && this._latestListing) {
            for ( let item of this._latestListing ) {
                if ( item.name.endsWith( ".log" ) ) {
                    statsPath = item.path;
                } else if ( item.name.endsWith( ".json" ) ) {
                    specPath = item.path;
                }
            }
        }


        let parameters = {
            hostName: this.hostName,
            serviceName: this.serviceName,
            statsPath: statsPath,
            // kbLimit: this.$statsFileLimit.val(),
            specPath: specPath,
            alternateBasePath: this.$alternateBasePath.val()
        };

        //
        //  Files will used default limits
        //
        let $selectedFileOption=$( "option:selected", this.$statsFilesSelect ) ;

        if ( isReportSelected
            || $selectedFileOption.data("docker") ) {

            let $optionLimitSelected = $( "option:selected", this.$statsFileLimit );
            let limitType = $optionLimitSelected.data( "type" );

            if ( limitType == "lines" ) {
                parameters.lineCount = $optionLimitSelected.val();
            } else {
                parameters.kbLimit = $optionLimitSelected.val();
                if ( limitType == "mb" ) {
                    parameters.kbLimit = $optionLimitSelected.val() * 1024;
                }
            }

        }

        let theStatsUrl = utils.getFileUrl() + "/service/stats" ;
        _net.httpGet( theStatsUrl, parameters )
            .then( ( statsResponse ) => this.processStatsResponse( statsResponse, statsPath ) )
            .catch( ( e ) => {
                _dom.logError ( `Failed retrieving stats ${ theStatsUrl }`, e ) ;
                // console.warn( "Error", e );
            } )
            .finally( () => {
                utils.loadingComplete();
            } );


    }

    processStatsResponse( statsReport, statsPath ) {

        console.log( `processStatsResponse222: ${ statsPath }` ) ;
        _dom.csapDebug( `statsReport`, statsReport )

        this.$statsEditorContainer.hide();
        this.$statsBrowser.hide();



        if ( statsReport?.spec?.content ) {

            this.$statsBrowser.show();
            this.buildReportView( statsReport );

        } else if ( statsReport.content ) {

            this.$statsEditorContainer.show();

            this._statsEditor.getSession().setMode( this.determineAceExtension( statsPath ) );

            this._statsEditor.getSession().setValue( statsReport.content );

            this.$linesLoaded.text( `${ this._statsEditor.getSession().getLength() } lines` );

        } else if ( statsReport.error ) {
            alertify.csapInfo( `Unable to build stats report: '${ statsPath }'  \n\n`
                + statsReport.error );
        }

        if ( statsPath.contains( this._defaultReport )
            && statsReport.note ) {
            alertify.csapInfo( statsReport.note ) ;
        }


    }

    buildReportView( statsReport ) {

        let _me = this;


        let pruneIdColumns = this.$pruneIdColumns.prop( "checked" );

        let mergeStatsView = this.$mergeStatsView.prop( "checked" );

        let pruneEmptyColumns = this.$pruneEmptyColumns.prop( "checked" );

        let isConvertHumanReadable = this.$convertFriendly.prop( "checked" );

        this._last_report = statsReport;
        if ( mergeStatsView ) {
            this._csv_report_text += statsReport?.content ;
        } else {
            this._csv_report_text = statsReport?.content ;
        }


        let selectedView = this.$statsFilesSelect.val();
        if ( selectedView === "full" ) {
            pruneIdColumns = false;
            pruneEmptyColumns = false;
        }

        console.log( ` selectedView: ${ selectedView } isConvertHumanReadable: ${ isConvertHumanReadable }, pruneIdColumns: ${ pruneIdColumns }, pruneEmptyColumns: ${ pruneEmptyColumns }` );


        this.$statsBrowser.empty();


        const statsSpec = JSON.parse( statsReport?.spec?.content );

        // let $csvContent = jQuery( '<div/>', {
        //     text: statsReport.content
        // } );

        // https://www.papaparse.com/docs#config

        let csvText = this._csv_report_text  ;

        if ( pruneIdColumns ) {
            csvText = csvText.replace( /([A-Z][a-z])/g, ' $1' ).trim();
        }

        let csvParseResults = Papa.parse( csvText, {
            skipEmptyLines: true
        } );

        let tableRows = csvParseResults.data;
        this.$linesLoaded.text( `${ tableRows.length } rows` );


        // https://mottie.github.io/tablesorter/docs/example-widget-filter.html

        //console.log( `statsSpec: `, statsSpec) ;

        let fields = statsSpec?.avro_schema?.fields;

        let titles;
        let docs ;
        if ( fields.length > 0 ) {
            titles = new Array();
            docs = new Array();

            let index = 0;
            let filterFields = new Array();
            for ( let field of fields ) {

                let fieldText = field.name.replaceAll( "_", " " );
                titles.push( fieldText );
                docs.push( field.doc );

                if ( ( pruneIdColumns && fieldText.includes( " id" ) )
                    || ( fieldText == "time" && index == 0 ) ) {
                    filterFields.push( index )
                }

                index++;

            }

            console.log( `isFilterId: ${ pruneIdColumns }  filterFields: `, filterFields );
            //
            // tablesorter builder want titles at the from
            //
            tableRows.unshift( titles );

            for ( let filterIndex of filterFields.reverse() ) {
                // titles are a simply array
                docs.splice(filterIndex, 1);
                tableRows = tableRows.map( function ( arr ) {
                    return arr.filter( function ( el, idx ) {
                        return idx !== filterIndex
                    } );
                } );
            }


            if ( pruneEmptyColumns ) {
                let filterFields = new Array();

                for ( let fieldIndex = 0; fieldIndex < fields.length; fieldIndex++ ) {

                    let emptyCount = 0;
                    for ( let rowIndex = 0; rowIndex < tableRows.length; rowIndex++ ) {
                        if ( ( tableRows[ rowIndex ][ fieldIndex ] == "" )
                            || ( tableRows[ rowIndex ][ fieldIndex ] == 0 )) {
                            emptyCount++;
                        }
                    }

                    if ( emptyCount == ( tableRows.length - 1 ) ) {
                        // for title
                        filterFields.push( fieldIndex );
                    }

                }

                console.debug( `emptyCount filterFields: `, filterFields )
                console.debug(`before `, docs) ;
                // filter tableRows csv to remove empty columns
                for ( let filterIndex of filterFields.reverse() ) {
                    // titles are a simply array
                    docs.splice(filterIndex, 1);

                    // tableRows are a 2d array
                    tableRows = tableRows.map( function ( arr ) {
                        return arr.filter( function ( el, idx ) {
                            return idx !== filterIndex
                        } );
                    } );
                }
                console.debug(`after `, docs) ;

            }

        }


        console.debug( `fields.length: ${ fields.length } csv parse results`, tableRows )
        //
        // table builder: https://mottie.github.io/tablesorter/docs/example-widget-build-table.html
        //

        let tableHeight = Math.round( this.$statsBrowser.outerHeight( true ) - 200 );

        let useScroller = this.$fixedColumns.prop( "checked" );

        console.log( `useScroller: ${ useScroller }` );
        let theWidgets = [ "filter", "headerTitles" ];

        let padding = 0
        if ( useScroller ) {
            padding = "1em";
            theWidgets.push( "scroller" );
        }
        this.$statsBrowser.css( "padding", padding );

        let updateTooltips = function($cell, txt) {
            // dynamically update tipsy
            let columnIndex= $cell.index() ;
            console.debug(`xxx showing: index ${ columnIndex } , text: ${txt }`) ;
            return `${ txt } \n\n ${ docs[ columnIndex ] }`;
        };

        const tableOptions = {
            cancelSelection: false,
            theme: 'csapSummary',
            widgets: theWidgets,
            widgetOptions: {
                //
                //  table builder
                //
                build_type: 'array', // csv, array, 
                build_source: tableRows,   // $csvContent,

                build_headers: {
                    rows: 1
                },
                build_footers: {
                    rows: 0
                },
                headerTitle_useAria  : true,
                headerTitle_tooltip  : 'tooltip',
                rheaderTitle_tooltip : docs,
                headerTitle_callback: updateTooltips ,

                //
                // filter options
                //
                filter_hideFilters: true,

                //
                // scroller
                //
                scroller_height: tableHeight,
                scroller_fixedColumns: 1,
                // scroller_addFixedOverlay: true
            },
            initialized: function ( table ) {

                if ( useScroller ) {
                    $( window ).resize();
                    // $( table ).addClass( "csap" );
                    $( "table", _me.$container ).addClass( "csap" );
                } else {
                    $( table ).addClass( "csap sticky-header" );

                    let offset = Math.round( $( "tr.tablesorter-headerRow", table ).outerHeight( true ) );

                    $( "tr.tablesorter-filter-row", table ).css( "top", `${ offset }px` )
                }

                //console.log(`_me.$statsFolderSelect.val() ${_me.$statsFolderSelect.val()} `) ;

                let testTitles = tableRows[ 0 ];
                console.debug( `testTitles`, testTitles )
                if ( selectedView == "default" ) {


                    if ( isConvertHumanReadable ) {
                        for (let i = 0; i < docs.length; i++) {
                            let descriptionLowerCase = docs[ i].toLowerCase() ;
                            console.debug( ` Checking ${ descriptionLowerCase }` )

                            //
                            if ( descriptionLowerCase.contains("end time in millis")
                                || (descriptionLowerCase.contains("millisecond") && descriptionLowerCase.contains("absolute time in")) ) {
                                console.debug(`xxx Converting column ${i} to friendly time`);
                                $(`tbody tr td:nth-child(${i + 1})`, table).each(function () {
                                    // let friendlyTime = utils.adjustTimeUnitFromMs($(this).text());
                                    $(this).css("text-align", "right") ;
                                    let friendlyTime = utils.adjustTimeUnitFromMs( Date.now() - $(this).text() ) ;
                                    $(this).html(`<span class="stat-friendly">${ friendlyTime }</span>`);
                                })
                            } else if ( descriptionLowerCase.contains("millisecond")
                                        || descriptionLowerCase.contains("time in millis") ) {
                                console.debug(`xxx Converting column ${i} to friendly time`);
                                $(`tbody tr td:nth-child(${i + 1})`, table).each(function () {
                                    $(this).css("text-align", "right") ;
                                    let friendlyTime = utils.adjustTimeUnitFromMs($(this).text());
                                    $(this).html(`<span class="stat-friendly">${ friendlyTime }</span>`);
                                })
                            } else if ( descriptionLowerCase.contains("(nanoseconds)")) {
                                console.debug(`xxx Converting column ${i} to friendly time`);
                                $(`tbody tr td:nth-child(${i + 1})`, table).each(function () {
                                    $(this).css("text-align", "right") ;
                                    let ms = parseFloat($(this).text()) / 1000000;
                                    let friendlyNs = utils.adjustTimeUnitFromMs( ms  );
                                    $(this).html(`<span class="stat-friendly">${ friendlyNs }</span>`);
                                })
                            } else if ( descriptionLowerCase.contains("(mb)")
                                    || descriptionLowerCase.contains(" mb" ) ) {
                                console.debug(` Converting column ${i} to friendly byts`);
                                $(`tbody tr td:nth-child(${i + 1})`, table).each(function () {
                                    $(this).css("text-align", "right") ;
                                    let mbInBytes = parseInt($(this).text()) * 1024 * 1024;
                                    let friendlyBytes = utils.bytesFriendlyDisplay(mbInBytes);
                                    $(this).html(`<span class="stat-friendly">${ friendlyBytes }</span>`);
                                })
                            } else if ( descriptionLowerCase.contains("bytes")
                                || descriptionLowerCase.contains("amount of used memory")
                                || descriptionLowerCase.contains("amount of memory")  ) {
                                console.debug(` Converting column ${i} to friendly bytes`);
                                $(`tbody tr td:nth-child(${i + 1})`, table).each(function () {
                                    $(this).css("text-align", "right") ;
                                    let mbInBytes = parseInt($(this).text()) ;
                                    let friendlyBytes = utils.bytesFriendlyDisplay(mbInBytes);
                                    $(this).html(`<span class="stat-friendly">${ friendlyBytes }</span>`);
                                })
                            } else if ( descriptionLowerCase.contains("count")
                                || descriptionLowerCase.contains("number of")
                                || descriptionLowerCase.contains("number ") ) {
                                console.debug(` Converting column ${i} to friendly bytes`);
                                $(`tbody tr td:nth-child(${i + 1})`, table).each(function () {
                                    $(this).css("text-align", "right") ;
                                    let eznum = utils.numberWithCommas( $(this).text() ) ;
                                    $(this).html(`<span class="stat-friendly">${ eznum }</span>`);
                                })
                            }
                        }
                    }

                    if(  _me.$statsFolderSelect.val() == `/${ _me._defaultReport }`
                        && testTitles[ 4 ] == "tenant n" ) {
                        console.log(`Adding links for ${_me._defaultReport} `);
                        $(`tbody tr td:nth-child(5)`, table).each(function () {

                            let tenantName = $(this).text();

                            let $tenantContainer = jQuery('<span/>', {} );

                            $tenantContainer.append( tenantName ) ;

                            let $tenantLink = jQuery('<button/>', {
                                title: "Open User Interface",
                                class: "csap-icon csap-window"
                            })
                                .css("margin-left", "2em" )
                                .click( function() {
                                    utils.launch( `https://my.stats.host/${ tenantName }/d/home.htmld` ) ;
                                });
                            $tenantContainer.append( $tenantLink ) ;

                            let statsUrl=`https://my.stats.host`
                            let $statsLink = jQuery('<button/>', {
                                title: "Open Stats",
                                class: "csap-icon csap-graph"
                            })
                                .css("margin-left", "2em" )
                                .click( function() {
                                    utils.launch( statsUrl ) ;
                                });
                            $tenantContainer.append( $statsLink ) ;

                            $(this).html( $tenantContainer );

                        });
                    }
                }



                $( table ).on( 'sortStart', function () {
                    console.log( `table built` );

                } );
            }
        };

        this.$statsBrowser.tablesorter( tableOptions );

        console.log( `ending table build` );
    }

    determineAceExtension( theFile ) {
        let modelist = aceEditor.require( "ace/ext/modelist" );
        let testFileForExtension = theFile;

        if ( theFile.endsWith( ".jmx" ) ) {
            testFileForExtension = "assumingJmxIsXml.xml";
        } else if ( theFile.endsWith( ".jsonnet" )
            || theFile.endsWith( ".libsonnet" ) ) {
            testFileForExtension = "assumingJmxIsXml.json5";
        } else if ( theFile.endsWith( ".properties" ) ) {
            testFileForExtension = "test.sh";
        }

        let fileMode = modelist.getModeForPath( testFileForExtension ).mode;
        console.log( `testFileForExtension: ${ testFileForExtension }, fileMode: ${ fileMode } ` );

        return fileMode;
    }
}