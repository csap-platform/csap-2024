import _dom from "../utils/dom-utils.js";
import _net from "../utils/net-utils.js";
import jsonForms from "../editor/json-forms.js";


const utils = browser_utils();

export default utils

function browser_utils() {

    _dom.logHead( "Module loaded: utils" );

    const $browser = $( "body#manager" );
    const $betweenHeaderAndFooter = $( ">section", $browser );
    const $uiTemplates = $( ">aside", $browser );
    const $navigation = $( "article.navigation", $betweenHeaderAndFooter );
    const $activeContent = $( "article.content", $betweenHeaderAndFooter );
    const $hiddenContent = $( "aside", $browser ).first();
    //    console.log( `$content size: ${ $content.length }` ) ;

    const $hostServiceNames = $( "#host-service-names", $navigation );
    let lastMetricArrayLength;
    let defaultService, defaultGraphDate;
    let $loadingMessage = $( "#loading-project-message" );
    const UNREGISTERED = "unregistered";

    let pageParameters;

    let hostSettings = null;

    let hostSuffix = function () {

        let suffix = CSAP_HOST_NAME.substring( CSAP_HOST_NAME.length - 2 );
        let shortName = getHostShortName( CSAP_HOST_NAME ) ;
        if (shortName != CSAP_HOST_NAME ) {

            console.log( `CSAP_HOST_NAME ${ CSAP_HOST_NAME } shortName: ${ shortName }` );
            let nameParts = CSAP_HOST_NAME.split(".") ;
            if ( nameParts.length > 2 ) {
                suffix = nameParts[1] ;
            }
            suffix = shortName.substring( shortName.length - 2 ) + "-" + suffix ;
        }
        return suffix;
    }

    _net.httpGetAndWait( `${ OS_URL }/hostSettings` )
        .then( data => {
            hostSettings = data;
            // default name in aws - overide if force tag available
            console.log( `tag: ${ getHostTag( CSAP_HOST_NAME ) }` );
            let tag = getHostTag( CSAP_HOST_NAME, false );
            if ( tag && tag.includes( "!" ) ) {
                document.title = `${ tag.replaceAll( "!", "" ) }[${ hostSuffix() }]`;
            }

            let currentLocation = document.location.href;
            if ( !currentLocation.includes( CSAP_HOST_NAME ) ) {

                let $hostNameLink = jQuery( '<a/>', {
                    title: `Domain Mismatch consider launching using the defined domain`,
                    class: "csap-link-button csap-alt-colors csap-warn",
                    href: agentUrl( CSAP_HOST_NAME ),
                    html: `Domain Mismatch: ${ CSAP_HOST_NAME }`
                } ).css( "padding-left", "2em" ).css( "margin-left", "2em" );
                $hostNameLink.appendTo( $( "#application-name" ) );


            }
        } );

    let navigationChangeFunctions = new Array();
    const $preferences = $( "#preferences-tab-content" );
    const $disableLogFormat = $( "#disable-log-format", $preferences );
    const $useCsapIdForScm = $( "#use-csap-id-for-scm", $preferences );


    let refreshStatusFunction = null;
    let launchMenuFunction = null;
    let editServiceFunction = null;

    let activeProjectFunction = null;

    let browseServiceFunction = null;

    let logChoice = null;

    let forceLogReInit=false;

    let _statusReport = "not-found";

    let _previousLocation;

    let $globalDate;

    let _refreshInterval = 30 * 1000;

    let pageTitle="all" ;

    let aceDefaults = {
        tabSize: 2,
        useSoftTabs: true,
        newLineMode: "unix",
        theme: "ace/theme/chrome", //kuroir  Xcode tomorrow, tomorrow_night, dracula, crimson_editor
        printMargin: false,
        fontSize: "11pt",
        //        enableLinking: true,
        wrap: true
    };


    let defaultLogParsers = [
        {
            match: "netmet-field8Colon",
            about: "checks 10 fields:  if field 8 is a colon",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 4, "message:", 10, "\n" ],
            newLineWords: []
        },

        {
            match: "netmet-field6Dash",
            about: "checks 10 fields: if field 6 is ---",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 7, 8, 9, "message:", 10, "\n" ],
            newLineWords: []
        },

        {
            match: "crni-field6Colon",
            about: "checks 10 fields: if field 6 is a colon",
            columns: [ "   # level: ", 4, "time:", 1, 2, "source:", 3, 5, "message:", 7, 8, 9, 10, "\n" ],
            newLineWords: []
        },

        {
            match: "crni-field5Dash",
            about: "checks 10 fields: if field 5 is ---",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 6, 7, "message:", 8, 9, 10, "\n" ],
            newLineWords: []
        },

        {
            match: "crni-field9Tilde",
            about: "checks 10 fields: if field 9 is |~",
            columns: [ "   # level: ", 6, "time:", 1, 2, "source:", 8, "message:", 10, "\n\n" ],
            newLineWords: []
        },

        {
            match: "etcd",
            columns: [ "   # level: ", 3, "time:", 1, 2, "message:", 6, "\n" ],
            throttleWords: [ "health OK" ],
            newLineWords: []
        },
        {
            match: "nfs-client-provisioner",
            columns: [ "   # level: ", 1, "time:", 2, "source:", 4, "message:", 5, "\n" ],
            throttleWords: [ "health OK" ],
            newLineWords: []
        },
        {
            match: "kube-apiserver",
            columns: [ "   # level: ", 1, "time:", 2, "source:", 5, "message:", 6, "\n" ],
            throttleWords: [ "health OK" ],
            newLineWords: []
        },
        {
            match: "fluentd",
            columns: [ "   # level: ", 4, "time:", 1, " ", 2, "source:", 6, "message:", 7, "\n" ],
            throttleWords: [ "features are not enabled", "stats -" ],
            newLineWords: []
        },
        {
            match: "httpd",
            columns: [ "   # level: ", 6, "time:", 1, 2, 3, 4, 5, "message:", 10, "\n" ],
            newLineWords: []
        },
        {
            match: "calico",
            columns: [ "   # level: ", 3, "time:", 1, " ", 2, "message:", 6, "\n" ],
            newLineWords: []
        },
        {
            match: ".*wats.*",
            columns: [ 2, "    ", 6 ],
            lineSeparator: "\n",
            newLineWords: [],
            disableWrap: true,
            applyWhen: " -  ",
            blankLinesEndingWith: " - "
        },
        {
            match: "docker$",
            columns: [ "   # ", 7, "date:", 1, 2, 3, "message:", 8, "\n" ],
            newLineWords: [ "error:" ]
        },
        {
            match: "sample-mongo-1",
            columns: [ "   # level: ", 2, "time:", 1, "source:", 3, 4, "message:", 5, "\n" ],
            throttleWords: [ "end connection", "connection accepted", "Successfully authenticated", "received client metadata" ],
            newLineWords: [ "command:", "error:", "pipeline:" ]
        },
        {
            match: "sample-mongo-2",
            columns: [ "   # level: ", 2, "time:", 1, "source:", 3, 4, "message:", 5, "\n" ],
            throttleWords: [ "end connection", "connection accepted", "Successfully authenticated", "received client metadata" ],
            newLineWords: [ "command:", "error:", "pipeline:" ]
        },
        {
            match: "keycloak",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 4, "message:", 6, "\n\n" ],
            trimAnsi: true,
            newLineWords: []
        },
        {
            match: "kafka",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 4, 5, "message:", 6, "\n" ],
            newLineWords: [ "(kafka", "(topicName", "(org.apache" ]
        },
        {
            match: "zookeeper",
            columns: [ "   # level: ", 3, "time:", 1, 2, "message:", 4, "\n" ],
            newLineWords: [ "LOOKING", "FOLLOWING", "(org.apache" ]
        },
        {
            match: "gitea",
            columns: [ "   #", "time:", 2, 3, "source:", 1, "message:", 4, "\n\n" ],
            trimAnsi: true
        },
        {
            match: "artifactory",
            columns: [ "   # level: ", 4, 5, "time:", 1, 2, "source:", 3, "message:", 8, "\n" ],
            newLineWords: [ "file:", "item:", "deployed:", "properties:", "repo:", "deleted:" ]
        }
    ];

    let applicationParsers = defaultLogParsers;

    let _statusFilterTimer;


    return {

        initialize: function ( refreshFunction, _launchMenuFunction, _activeProjectFunction ) {
            refreshStatusFunction = refreshFunction;
            launchMenuFunction = _launchMenuFunction;
            activeProjectFunction = _activeProjectFunction;
            jsonForms.setAceDefaults( aceDefaults );


            // add class=sorter-csap-sort-value to th, and then add data-sortvalue="xx" to sortcell
            $.tablesorter.addParser( {
                // set a unique id
                id: 'csap-sort-value',
                is: function ( s, table, cell, $cell ) {
                    // return false so this parser is not auto detected
                    return false;
                },
                format: function ( s, table, cell, cellIndex ) {
                    let $cell = $( cell );
                    let valueToSort = $cell.data( 'sortvalue' );
                    if ( valueToSort === undefined ) {
                        //console.log( ` missing sortvalue: ${valueToSort} in ${ $(table).attr("id")  }`);
                        valueToSort = "";
                    }

                    //                    if ( $cell.closest( `table`).attr(`id`) === `processTable`) {
                    //                        
                    //                        console.log( ` valueToSort: ${valueToSort}` ) ;
                    //                    }
                    // format your data for normalization
                    return valueToSort;
                },
                // set type, either numeric or text
                // text works in all cases - numbers or text - but a little slower
                type: 'text'
            } );

        },

        findInPreferences: function ( jqExpression ) {
            return $( jqExpression , $preferences );
        },

        areHostSettingsAvailable: function () {
            return hostSettings != null;
        },


        graphCompareLabel: function ( isBaseline ) {
            const $currentLabel = $( "#graph-label-current", $preferences );
            const $baselineLabel = $( "#graph-label-baseline", $preferences );

            if ( isBaseline ) {
                let baselineLabel = `Baseline`;

                if ( $baselineLabel.length > 0 ) {
                    baselineLabel = $baselineLabel.val();
                }
                return `<span class=baseline-label >${ baselineLabel }</span>`;
            } else {
                let currentLabel = `Current`;

                if ( $currentLabel.length > 0 ) {
                    currentLabel = $currentLabel.val();
                }
                return `<span class=current-label>${ currentLabel }</span>`;
            }
        },

        graphZeroFilterPercentage: function () {
            const $graphZeroFilterPercent = $( "#graph-zero-filter-percent", $preferences );


            let percent = 10;

            if ( $graphZeroFilterPercent.length > 0 ) {
                percent = $graphZeroFilterPercent.val();
            }
            return percent / 100;
        },

        baseName: function ( path ) {
            return path.split( '/' ).reverse()[ 0 ];
        },

        setGlobalDate: function ( $globalDateInput ) {
            $globalDate = $globalDateInput;

            if ( defaultGraphDate ) {
                $globalDate.val( defaultGraphDate.replaceAll( "-", "/" ) )
            }
        },

        getGlobalDate: function () {
            return $globalDate;
        },


        updateUrlIfDesktop: function ( url ) {

            if ( utils.getActiveEnvironment().includes( "desktop" ) ) {
                url = url.replaceAll( "8021", "8022" ).replaceAll( "desktop-dev-01", "oms-perf-01" ).replaceAll( "csap-desktop", "csap-performance-app" );
                console.warn( `DESKTOP TEST: launching: ${ url }` );
            }

            return url;
        },

        getHostWithTag: function ( hostName ) {

            if ( utils.getHostTag( hostName ) ) {
                return `<span class=host-tag>[${ utils.getHostTag( hostName ) }]</span> ${ hostName } `;
            }

            return hostName;

        },
        getHostTag: function ( hostName ) {

            return getHostTag( hostName );

        },

        getHostHelperUrl: function ( hostName ) {

            return getHostHelperUrl( hostName );

        },

        copyItemToClipboard: function ( $item ) {

            if ( $item.length ) {

                $( ".copy-only", $item ).css( "display", "block" );
                // create a Range object
                let range = document.createRange();
                // set the Node to select the "range"
                range.selectNode( $item[ 0 ] );
                // add the Range to the set of window selections
                window.getSelection().addRange( range );

                // execute 'copy', can't 'cut' in this case
                document.execCommand( 'copy' );

                $( ".copy-only", $item ).css( "display", "" );

                alertify.notify( `copied` );
                window.getSelection().removeAllRanges();

            } else {
                alertify.notify( `unable to select item` );
            }
        },

        isLogAutoFormatDisabled: function () {
            console.log( ` $disableLogFormat widget count: ${ $disableLogFormat.length }` );
            return $disableLogFormat.is( ":checked" );
        },

        stringSplitWithRemainder: function ( stringToBeSplit, separator, limit, stripBlankSpace = true ) {
            // single space everything first
            if ( stripBlankSpace ) {
                stringToBeSplit = stringToBeSplit.trim().replace( /  +/g, ' ' );
            }
            stringToBeSplit = stringToBeSplit.split( separator );

            if ( stringToBeSplit.length > limit ) {
                let stringPieces = stringToBeSplit.splice( 0, limit - 1 );
                stringPieces.push( stringToBeSplit.join( " " ) );

                return stringPieces;
            }

            return stringToBeSplit;
        },

        addTableFilter: function ( $filterInput, $tableContainer, alwaysShowFunction ) {
            return addTableFilter( $filterInput, $tableContainer, alwaysShowFunction );
        },

        showDefaultServicesName: function () {
            return "default";
        },

        bytesFriendlyDisplay: function ( numBytes ) {
            return bytesFriendlyDisplay( numBytes );
        },

        calculateOffsetDays: function ( selectedTime ) {

            let nowDate = new Date( Date.now() );
            let selectedDate = new Date( selectedTime )

            let deltaDays = Math.floor( ( nowDate.getTime() - selectedDate.getTime() ) / 24 / 60 / 60 / 1000 );
            // var days=Math.round((nowDate.getTime() - selectedDate.getTime()
            // )/24/60/60/1000) ;
            // console.log("Days: " + deltaDays + " selectedDate: " + selectedDate +
            // " nowDate: " + nowDate) ;
            return deltaDays;
        },
        numberWithCommas: function ( collectedValue ) {

            function isNumeric(str) {
                return !isNaN(str) && // use type coercion to parse the _entirety_ of the string (`parseFloat` alone does not do this)...
                    !isNaN(parseFloat(str)) // ...and ensure strings of whitespace fail
            }

            if ( typeof collectedValue == "string") {
                if ( ! isNumeric( collectedValue ) ) {
                    return collectedValue ;
                }
            }
            let collectedNum = Number( collectedValue )

            const MILLION=1000000;
            let displayValue = collectedNum;
            let unit = "";
            if ( ( collectedNum + "" ).endsWith( ".0" ) ) {
                displayValue = collectedNum.toFixed( 0 );
            }
            if ( collectedNum > MILLION ) {
                let millions = ( collectedNum / MILLION );
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

        },
        adjustTimeUnitFromMs: function ( collected ) {

            const SECOND_MS = 1000;
            const MINUTE_MS = 60 * SECOND_MS;
            const HOUR_MS = 60 * MINUTE_MS;
            const WEEK_MS = 24 * 7 * HOUR_MS;
            const YEAR_MS = 52 * WEEK_MS;

            let showVal = collected + "<span class=munits>ms</span>";
            if ( collected > 10 * YEAR_MS ) {
                showVal = ( collected / YEAR_MS ).toFixed( 0 ) + "<span class=munits>yrs</span>";
            } else if ( collected > 4 * WEEK_MS ) {
                showVal = ( collected / WEEK_MS ).toFixed( 1 ) + "<span class=munits>wks</span>";
            } else if ( collected > 24 * HOUR_MS ) {
                showVal = ( collected / 24 / HOUR_MS ).toFixed( 1 ) + "<span class=munits>days</span>";
            } else if ( collected > HOUR_MS ) {
                showVal = ( collected / HOUR_MS ).toFixed( 1 ) + "<span class=munits>hrs</span>";
            } else if ( collected > MINUTE_MS ) {
                showVal = ( collected / MINUTE_MS ).toFixed( 1 ) + "<span class=munits>min</span>";
            } else if ( collected > ( SECOND_MS ) ) {
                showVal = ( collected / SECOND_MS ).toFixed( 2 ) + "<span class=munits>s</span>";
            }
            return showVal;
        },

        buildMemoryCell: function ( memoryInBytes ) {

            let displayValue = "";
            if ( memoryInBytes !== 0
                && !isNaN( memoryInBytes ) ) {
                displayValue = bytesFriendlyDisplay( memoryInBytes );
            }
            let $cell = jQuery( '<td/>', {
                class: "numeric",
                //                "data-sortvalue": memoryInBytes,
                text: displayValue
                //                text:  memoryInBytes
            } );

            if ( displayValue !== "" ) {
                $cell.data( "sortvalue", memoryInBytes );
            }

            return $cell;
        },

        toDecimals: function ( original, decimals = 2 ) {
            let result = original;

            let number = Number( original );
            if ( isFloat( number ) ) {
                result = number.toFixed( decimals );
            }

            return result;
        },

        closeAllDialogs: function () {
            jsonForms.closeDialog();
            alertify.closeAll();
        },

        registerForNavChanges: function ( callbackFunction ) {
            console.log( `registerForNavChanges`, callbackFunction );
            navigationChangeFunctions.push( callbackFunction );

        },

        getNavChangeFunctions: function () {
            return navigationChangeFunctions;

        },

        getAceDefaults: function ( mode, readOnly ) {
            let settings = Object.assign( {}, aceDefaults );
            if ( mode ) {
                settings.mode = mode;
            }
            if ( readOnly ) {
                settings.readOnly = true;
            }

            return settings;
        },

        yamlSpaces: function ( yamlText, keyWords, spaceTopLevel ) {
            for ( let keyWord of keyWords ) {

                let re = new RegExp( `^[\s]*${ keyWord }:$`, "gm" );
                //console.log( `re: '${ re }'`) ;
                yamlText = yamlText.replace( re, `\n\n#\n#\n#\n$&` );
            }

            // 2nd level members gets spaced
            if ( spaceTopLevel ) {
                //alert("gotted")
                let serviceMatches = new RegExp( `^([a-z]|[A-Z]|-)*:.*$`, "gm" );
                yamlText = yamlText.replace( serviceMatches, `\n$&` );
            }
            //        
            let objectMatches = new RegExp( `^ ([a-z]|[A-Z]|-| )*:$`, "gm" );
            yamlText = yamlText.replace( objectMatches, `\n$&` );

            //        
            //            let nameValMatches = new RegExp( `^(?!.*http) ([a-z]|[A-Z]|-| )*:([a-z]|[A-Z]|-| |.|[0-9])*$`, "gm" ) ;
            let nameValMatches = new RegExp( `^([a-z]|[A-Z]|-| )*: ([a-z]|[A-Z]|-| |.|[0-9])*$`, "gm" );
            yamlText = yamlText.replace( nameValMatches, `\n$&` );

            //        
            //            let secondLevelMatches = new RegExp( `^  (.*):$`, "gm" ) ;
            //            yamlText = yamlText.replace( secondLevelMatches, `\n  $1:` ) ;
            //            let secondLevelMatches = new RegExp( `^  ([a-z]|[A-Z]|-)*:.*$`, "gm" ) ;
            //            yamlText = yamlText.replace( secondLevelMatches, `\n$&` ) ;

            return yamlText;
        },

        numberPercentDifference( a, b ) {
            a = Number.parseFloat( a );
            b = Number.parseFloat( b );
            return Math.round( 100 * ( a - b ) / ( ( a + b ) / 2 ) );
        },

        markAceYamlErrors: function ( aceSession, e ) {
            let lineNumber = jsonForms.getValue( "mark.line", e );
            if ( !lineNumber ) {
                lineNumber = 0;
            }
            console.debug( ` line: ${ lineNumber }, message: ${ e.message }` );
            aceSession.setAnnotations( [ {
                row: lineNumber,
                column: 0,
                text: e.message, // Or the Json reply from the parser 
                type: "error" // also "warning" and "information"
            } ] );
        },

        generalErrorHandler: function ( jqXHR, textStatus, errorThrown ) {

            // $mainLoading.hide();
            console.log( `Failed command:  ${ textStatus }`, jqXHR, `\n errorThrown: `, errorThrown );

            let messageTitle = `Failed Operation ${ jqXHR.status }: ${ jqXHR.statusText }`;
            let messageContent = "Contact your administrator";
            if ( jqXHR.status === 403 ) {
                messageContent += ` - permissions failure`;
            } else if ( jqXHR.status === 0 ) {
                // only known instance: saveing file to large to be stored
                messageContent += ` - failed to submit - request may be too large, or server may be down for maintenance.`;
            }

            let fullMessage = `<label class=csap>${ messageTitle }</label>` + `<br/><br/>` + messageContent;

            if ( jqXHR && jqXHR.responseText ) {
                fullMessage += `<br/><br/>Details:<br/> <div class=extra-info>` + jqXHR.responseText + `</div>`;
            }
            alertify.csapWarning( fullMessage );

        },

        updateAceDefaults: function ( wrap, theme, fontSize ) {
            aceDefaults.wrap = wrap;
            aceDefaults.theme = theme;
            aceDefaults.fontSize = fontSize;

            jsonForms.setAceDefaults( aceDefaults );
        },

        setRefreshInterval: function ( seconds ) {
            _refreshInterval = seconds * 1000;
        },

        getRefreshInterval: function () {
            return _refreshInterval;
        },

        setLastLocation( previousLocation ) {
            _previousLocation = previousLocation;
        },

        getLastLocation() {
            return _previousLocation;
        },

        jqplotLegendOffset: function () {
            // css: jqplot legend width + 30
            return 180;
        },

        openAgentWindow: function ( host, path, parameterMap ) {
            let targetUrl = agentUrl( host, path );
            let urlWithParams = buildGetUrl( targetUrl, parameterMap ) ;
            console.log( `openAgentWindow: host: ${ host },  targetUrl: ${ targetUrl}, urlWithParams: ${ urlWithParams }`) ;
            window.open( urlWithParams , '_blank' );
        },

        buildGetUrl: function ( url, parameterMap ) {
            return buildGetUrl( url, parameterMap );
        },

        agentUrl: function ( targetHost, command ) {
            return agentUrl( targetHost, command );
        },

        buildAgentLink: function ( targetHost, command = "/app-browser", linkText, parameters ) {

            if ( typeof linkText === 'undefined'
                || !linkText ) {
                linkText = getHostShortName( targetHost )
                if ( getHostTag( linkText ) ) {
                    linkText = `<span class=host-tag>[${ getHostTag( linkText ) }]</span> ${ linkText }`;
                }
            }
            return buildAgentLink( targetHost, command, linkText, parameters );
        },

        getHostShortName: function ( targetHost ) {
            return getHostShortName( targetHost );
        },

        isUnregistered: function ( serviceName ) {
            return UNREGISTERED === serviceName;
        },

        isAgent: function () {
            return AGENT_MODE;
        },
        getPageTitle: function () {
            return pageTitle ;
        },

        getErrorIndicator() {
            return "__ERROR:";
        },

        getWarningIndicator() {
            return "__WARN:";
        },

        setPageParameters: function ( parameters ) {

            _dom.logInfo( `Loading Parameters` );

            if ( parameters ) {
                pageParameters = new URLSearchParams( parameters );
            } else {
                console.debug( `setting page params from: '${ document.location.href }'` )
                pageParameters = ( new URL( document.location ) ).searchParams;
            }

            if ( pageParameters.has( "graphDate" ) ) {
                defaultGraphDate = pageParameters.get( "graphDate" );
                pageParameters.delete( "graphDate" );
            }
            // let lastTwo = CSAP_HOST_NAME.substring( CSAP_HOST_NAME.length - 2 );
            let tags = " ";

            if ( pageParameters.has( "defaultService" ) ) {
                defaultService = pageParameters.get( "defaultService" );
                let serviceParams = splitWithTail( defaultService, " ", 2 );
                if ( serviceParams.length > 1 ) {
                    defaultService = serviceParams[ 0 ];
                    tags = ` ${ serviceParams[ 1 ] } `;
                }

                //document.title = `${ defaultService } ${ HOST_ENVIRONMENT_NAME }`;
                updatePageTitle ( `${ defaultService }${ tags }[${ hostSuffix() }]` ) ;

                pageParameters.delete( "defaultService" );
                $hostServiceNames.empty();
                jQuery( '<option/>', {
                    text: defaultService
                } ).appendTo( $hostServiceNames );
                $( "span", $hostServiceNames.parent() ).text( defaultService );

            }


            updateAgentNav( utils.getSelectedService() );

            $hostServiceNames.change( function () {

                let selectedService = $( this ).val();
                console.log( `serviceName selected: ${ selectedService }` )

                updateAgentNav( selectedService );

                let newPageTitle = `${ selectedService }${ tags }[${ hostSuffix() }]`;

                if ( selectedService == "default" ) {
                    newPageTitle = CSAP_HOST_NAME;
                }

                document.title = newPageTitle;
            } );
        },

        getDefaultService: function () {
            return defaultService;
        },


        getServiceSelector: function () {
            return $hostServiceNames;
        },

        getSelectedService: function () {

            if ( $hostServiceNames.length > 0 ) {
                return $hostServiceNames.val();
            }
            return null;
        },

        isGraphSlicingActive: function ( metricsArray ) {

            if ( metricsArray ) {
                lastMetricArrayLength  = metricsArray.length ;
            }

            let uiChecked = $("#perform-graph-slice").is( ':checked' ) ;
            let graphSliceStart = parseInt ( $( "#graph-slice-start" ).val()  );
            let graphSliceAmount = parseInt ( $( "#graph-slice-amount" ).val() ) ;
            //_dom.logArrow( `graph slicing: uiChecked ${ uiChecked },  lastMetricArrayLength: ${ lastMetricArrayLength }, graphSliceStart ${ graphSliceStart} graphSliceAmount ${ graphSliceAmount }` )

            if ( uiChecked
                && (  lastMetricArrayLength > ( graphSliceStart + graphSliceAmount) ) ) {
                _dom.logArrow( `graph slicing enabled: lastMetricArrayLength ${ lastMetricArrayLength }, graphSliceStart ${ graphSliceStart} graphSliceAmount ${ graphSliceAmount }` )
                return true;
            }
            _dom.logArrow( "SLICING DISABLED") ;
            return false;
        },

        getMonthDayYear: function ( date ) {
            // const date = new Date();

            const year = date.getFullYear();
            // 👇️ getMonth returns integer from 0(January) to 11(December)
            const month = date.getMonth() + 1;
            const day = date.getDate();

            return [ month, day, year ].join( '/' );
        },

        getSelectedServiceIdName: function () {

            if ( $hostServiceNames.length > 0 ) {
                console.log( `$hostServiceNames.length: ${ $hostServiceNames.length }`, $hostServiceNames.text() );
                let $selectedOption = $hostServiceNames.find( ':selected' );
                if ( $selectedOption.data( "service" ) ) {

                    console.log( "using service mapping " );
                    return $selectedOption.data( "service" );

                }
            }
            return null;
        },

        /**
         *
         * @returns { URLSearchParams }
         */
        getPageParameters: function () {
            if ( !pageParameters ) {
                console.log( `\n\n ***** loading page parameters ******\n\n` );
                pageParameters = ( new URL( document.location ) ).searchParams;
            }
            //https://developer.mozilla.org/en-US/docs/Web/API/URLSearchParams
            return pageParameters;
        },

        setStatusReport: function ( statusReport ) {
            _statusReport = statusReport;
        },

        getMasterHost: function () {
            return _statusReport[ "kubernetes-master" ];
        },

        getEnvironmentHostCount: function () {
            return _statusReport[ "hosts-all-projects" ];
        },

        getKubernetesNodes: function () {
            return _statusReport[ "kubernetesNodes" ];
        },

        //        setMasterHost: function ( hostName ) {
        //            masterHost = hostName ;
        //        },

        getCsapUser: function () {
            return CSAP_USER;
        },

        getScmUser: function () {
            let user = CSAP_SCM_USER;

            if ( $useCsapIdForScm.is( ":checked" ) ) {
                user = CSAP_USER;
            }
            return user;
        },

        getAppId: function () {
            return APP_ID;
        },

        getHostName: function () {
            return CSAP_HOST_NAME;
        },

        getHostFqdn: function () {
            let host = CSAP_HOST_NAME;

            try {
                let url = AGENT_URL_PATTERN.replace( /CSAP_HOST/g, CSAP_HOST_NAME );
                let thost = url.substring( url.indexOf( "//" ) + 2 );
                thost = thost.split( ":" )[ 0 ];
                host = thost;
            } catch ( e ) {
                console.log( "Failed to build host", e );
            }

            return host;
        },

        getEnvironment: function () {
            return HOST_ENVIRONMENT_NAME;
        },

        // legacy
        getActiveEnvironment: function () {
            return HOST_ENVIRONMENT_NAME;
            //return  $environmentSelect.val() ;
        },

        getExplorerUrl: function () {
            return EXPLORER_URL;
        },

        getCsapBrowserUrl: function () {
            return APP_BROWSER_URL;
        },

        getOsExplorerUrl: function () {
            return OS_EXPLORER_URL;
        },

        getMetricsUrl: function () {
            return METRICS_URL;
        },

        getAnalyticsUrl: function () {
            return ANALYTICS_URL;
        },

        getTrendUrl: function () {
            return TREND_URL;
        },

        getFileUrl: function () {
            return FILE_URL;
        },

        getOsUrl: function () {
            return OS_URL;
        },

        getParameterByName: function ( name ) {
            return getParameterByName( name );
        },

        adminHost: function () {
            return $( "#admin-host" ).text();
        },

        findContent: function ( selector ) {

            let $resolved = $( selector, $hiddenContent );
            if ( $resolved.length == 0 ) {
                // handle deferred loading cases
                $resolved = $( selector, $activeContent );
            }

            //console.log( `findContent $content size: ${ $content.length }, selector: ${ selector }, size: ${ $resolved.length}` ) ;
            return $resolved;
        },

        findNavigation: function ( selector ) {
            return findNavigation( selector );
        },

        navigationCount: function ( navSelector, count, alertCount, suffix = "" ) {

            let $itemSource = findNavigation( navSelector );
            //console.log( "navigationCount", $itemSource) ;
            let currentVal = $itemSource.text();


            $itemSource.text( count + suffix );
            $itemSource.removeClass( "up down" )


            if ( currentVal === "disabled" ) {
                flash( $itemSource, true );
                return $itemSource;
            }

            if ( currentVal != 0 ) {
                if ( count > currentVal ) {
                    $itemSource.addClass( "up" );
                } else if ( count < currentVal ) {
                    $itemSource.addClass( "down" );
                }
            }
            let active = false;
            if ( count > alertCount ) {
                active = true;
            }
            flash( $itemSource, active );

            return $itemSource;
        },

        launchService: function ( serviceName, servicePath, launcherIndex, launcherMessage, isPerf ) {

            console.log( `serviceName ${ serviceName }, servicePath: ${servicePath}, launcherIndex: ${ launcherIndex } , launcherMessage: ${ launcherMessage} ` )

            $.getJSON( `${ APP_BROWSER_URL }/launch/${ serviceName }`, {
                launcherIndex: launcherIndex
            } )
                .done( function ( launchReport ) {
                    console.log( `launchReport`, launchReport )


                    if ( launchReport.location ) {

                        let targetUrl = launchReport.location;
                        targetUrl = targetUrl.replaceAll( "__comma__", "," );

                        if ( serviceName == "csap-analytics" ) {
                            targetUrl += "&project=" + activeProjectFunction( false ) + "&appId=" + APP_ID;
                        }
                        if ( servicePath ) {
                            if ( targetUrl.endsWith( "/" ) && servicePath.startsWith( "/" ) ) {

                                if ( servicePath.length == 1 ) {
                                    servicePath = "";
                                } else {
                                    servicePath = servicePath.substring( 1 );
                                }
                            }
                            targetUrl += servicePath;
                        }
                        if ( isPerf ) {
                            console.log("overriding launch for perf")
                            let perfUrl = new URL(targetUrl);
                            // targetUrl = agentUrl( perfUrl.hostname, "system" ) ;
                            let parameterMap = {defaultService: serviceName};
                            utils.openAgentWindow( perfUrl.hostname, "/app-browser#agent-tab,system", parameterMap);
                            return;
                        }
                        if ( launcherIndex
                            && launchReport.launchCred ) {

                            let launchContent = `<label class=csap-form><span>Token:</span><input style="color:white" id=lcred-9899 value="${ launchReport.launchCred }"/></label>`;
                            launchContent += `<div class=quote style="color: grey; margin-bottom: 2em"> Select ok to launch the application, and paste token if prompted</div>`;
                            if ( launcherMessage ) {
                                launchContent += `<div class=csap-blue>${ launcherMessage }<div>`;
                            }
                            //alertify.csapInfo( launchContent, null, null, function() {   launch( targetUrl ) } );
                            alertify.confirm(
                                'CSAP Launcher',
                                `${ launchContent }`,
                                function () {
                                    launch( targetUrl )
                                },
                                function () {
                                    alertify.error( 'Canceled launch' )
                                }
                            );

                            function copyToClipboard( element ) {
                                $( "#lcred-9899" ).select();
                                document.execCommand( "copy" );
                                $( "#lcred-9899" ).val( "copied to clipboard" ).prop( "disabled", true ).css( "color", "red" );
                                let durationMs = 120;
                                $( "#lcred-9899" ).fadeOut( durationMs ).fadeIn( durationMs ).fadeOut( durationMs ).fadeIn( durationMs );
                            }

                            setTimeout( copyToClipboard, 500 );
                            //launchFunction() ;
                        } else {
                            if ( launcherMessage ) {
                                let launchContent = `<div class=csap-blue>${ launcherMessage }<div>`;
                                alertify.confirm(
                                    'CSAP Launcher',
                                    `${ launchContent }`,
                                    function () {
                                        launch( targetUrl )
                                    },
                                    function () {
                                        alertify.error( 'Canceled launch' )
                                    }
                                );

                            } else {
                                launch( targetUrl );
                            }

                        }


                    } else {
                        alertify.csapInfo( `Unable to launch service ${ serviceName }:<div class=csap-red> ${ launchReport.reason }</div>` );
                    }
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    console.log( "Error: Retrieving meter definitions ", errorThrown )
                    // handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
                } );
        },

        launchFiles: function ( parameters ) {
            console.log( `launchFiles: `, parameters );
            if ( parameters ) {
                for ( let name in parameters ) {
                    pageParameters.delete( name );
                    pageParameters.append( name, parameters[ name ] );
                }
            }

            launchMenuFunction( "agent-tab,file-browser" );
        },

        launchSystem: function ( parameters ) {
            console.log( `launchSystem: `, parameters );
            if ( parameters ) {
                for ( let name in parameters ) {
                    pageParameters.delete( name );
                    pageParameters.append( name, parameters[ name ] );
                }
            }

            launchMenuFunction( "agent-tab,file-browser" );
        },

        launchHostLogs: function ( parameters ) {
            console.log( `launchHostLogs updating: `, parameters );
            forceLogReInit=true;
            if ( parameters ) {
                for ( let name in parameters ) {
                    pageParameters.delete( name );
                    pageParameters.append( name, parameters[ name ] );
                }
            }

            launchMenuFunction( "agent-tab,logs" );
        },

        isForceLogInit: function() {
            let currentSetting = forceLogReInit ;
            forceLogReInit=false;
            return currentSetting;
        },

        launchScript: function ( parameters ) {

            console.log( `launchScript: `, parameters );
            if ( parameters ) {
                for ( let name in parameters ) {
                    pageParameters.delete( name );
                    pageParameters.append( name, parameters[ name ] );
                }

                pageParameters.append( "scriptRefresh", "true" );
            }

            launchMenuFunction( "agent-tab,script" );

            //findNavigation(".command-runner") ;
            let $menuMatch = menuMatch( "script", "agent-tab" );
            $menuMatch.effect( "pulsate", { times: 2 }, 2000 );


        },

        menuMatch: function ( path, tab ) {
            return menuMatch( path, tab );
        },

        launchMenu: function ( tabCommaMenu ) {
            launchMenuFunction( tabCommaMenu );
        },

        setServiceEditorFunction: function ( theFunction ) {
            editServiceFunction = theFunction;
        },

        launchServiceLogs: function ( logFileName ) {
            logChoice = null;
            if ( !$( "#disable-auto-show-logs" ).is( ":checked" ) ) {
                logChoice = logFileName;
                launchMenuFunction( "services-tab,logs" );
            }
        },

        isLaunchServiceLogs: function () {
            return !$( "#disable-auto-show-logs" ).is( ":checked" );
        },

        setLogChoice: function ( logFileName ) {
            logChoice = logFileName;
        },

        getLogChoice: function () {
            return logChoice;
        },

        getLogParsers: function () {


            // lazy loading of parser definitions. Initial load will return defaults;
            // subsequent load will use application defined - if specified

            $.getJSON( `${ APP_BROWSER_URL }/logParsers`, null ).done( function ( customParsers ) {

                if ( Array.isArray( customParsers )
                    && customParsers.length > 0 ) {
                    applicationParsers = customParsers;
                }

            } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    console.log( "Error: Retrieving meter definitions ", errorThrown )
                    // handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
                } );

            console.log( `applicationParsers: `, applicationParsers );
            return applicationParsers;

        },

        resetLogChoice: function () {
            logChoice = null;
        },

        launchServiceEditor: function ( serviceName ) {
            launchMenuFunction( "projects-tab,environment" );
            editService( serviceName );
        },

        launchServiceResources: function ( serviceName ) {
            launchMenuFunction( "projects-tab,files" );
            browseServiceFiles( serviceName );
        },

        setBrowseServiceFunction( _theFunction ) {
            browseServiceFunction = _theFunction;
        },

        launchServiceHistory: function ( serviceName ) {

            $( "#event-category" ).val( `/csap/ui/service/${ serviceName }*` );
            launchMenuFunction( "performance-tab,events" );

        },

        refreshStatus: function ( isBlocking ) {
            return refreshStatusFunction( isBlocking );
        },

        json: function ( dotPath, theObject ) {
            return jsonForms.getValue( dotPath, theObject );
        },

        loading: function ( message ) {
            if ( $loadingMessage.length == 0 ) {
                $loadingMessage = jQuery( '<article/>', {
                    id: "loading-project-message"
                } );

                jQuery( '<div/>', {
                    class: "loading-message-large",
                    text: "loading application"
                } ).appendTo( $loadingMessage );
                $( "body" ).append( $loadingMessage );
            }

            if ( message ) {
                $( "div", $loadingMessage ).html( message );
            }
            $loadingMessage.show();
        },

        loadingComplete: function ( source ) {

            if ( $loadingMessage.is( ":visible" ) ) {
                console.log( `loadingComplete - hiding message: ${ source } ` );
            }
            $loadingMessage.hide();
        },

        getActiveProject: function ( isAllSupport = true ) {
            return activeProjectFunction( isAllSupport );
        },

        flash: function ( $item, flashOn = true, count ) {
            flash( $item, flashOn, count );
        },

        isObject: function ( theReference ) {
            return isObject( theReference );
        },

        // sample using object attribute: let sortedInstances = ( instanceReport.instances ).sort( ( a, b ) => ( a.host > b.host ) ? 1 : -1 ) ;
        keysSortedCaseIgnored: function ( theObject ) {

            let theArray = Object.keys( theObject );
            theArray.sort( function ( a, b ) {
                return a.toLowerCase().localeCompare( b.toLowerCase() );
            } );
            return theArray;
        },

        launch: function ( theUrl, frameTarget ) {
            launch( theUrl, frameTarget )
        },

        buildHostsParameter: function ( $rows ) {
            return buildHostsParameter( $rows );
        },

        instanceRows: function () {
            return instanceRows();
        },

        selectedInstanceRows: function () {
            return selectedInstanceRows();
        },

        isSelectedKubernetes: function () {
            let $row = instanceRows().first();
            let clusterType = $row.data( "clusterType" );
            let isKubernetes = ( clusterType === "kubernetes" );
            return isKubernetes;
        },

        selectedKubernetesPod: function () {

            let $row = selectedInstanceRows().first();
            if ( $row.length === 0 ) {
                $row = instanceRows().first();
            }
            let name = $row.data( "service" );
            let containerIndex = $row.data( "container-index" );

            let pod = `${ name }-${ containerIndex + 1 }`;
            console.log( `selectedKubernetesPod() ${ pod } ` );
            return pod;
        },

        disableButtons: function ( ...$items ) {
            $items.forEach(
                $item => {
                    $item.prop( 'disabled', true );
                    if ( $item.parent().is( "label" ) ) {
                        $item.parent().css( "opacity", "0.25" );
                    } else {
                        $item.css( "opacity", "0.25" )
                    }
                }
            );

        },

        enableButtons: function ( ...$items ) {

            $items.forEach(
                $item => {
                    $item.prop( 'disabled', false );
                    if ( $item.parent().is( "label" ) ) {
                        $item.parent().css( "opacity", "1.0" );
                    } else {
                        $item.css( "opacity", "1.0" );
                    }
                }
            );
            //            $items.forEach(
            //                    $button => $button.prop( 'disabled', false ).css( "opacity", "1.0" )
            //            ) ;
        },

        buildValidDomId: function ( inputName ) {

            let regexPeriod = new RegExp( "\\.", "g" );
            let regexComma = new RegExp( "\\,", "g" );
            let regexLeftParen = new RegExp( "\\(", "g" );
            let regexRightParen = new RegExp( "\\)", "g" );
            let regexUnderscore = new RegExp( "_", "g" );
            let regexColon = new RegExp( ":", "g" );
            let regexSpace = new RegExp( " ", "g" );

            let updatedName = inputName.replace( regexPeriod, "-" );
            updatedName = updatedName.replace( regexComma, "-" );
            updatedName = updatedName.replace( regexLeftParen, "" );
            updatedName = updatedName.replace( regexRightParen, "" );
            updatedName = updatedName.replace( regexUnderscore, "-" );
            updatedName = updatedName.replace( regexColon, "-" );
            updatedName = updatedName.replace( regexSpace, "" );
            return updatedName;
        },

        getClusterImage: function ( clusterName, firstServiceType ) {
            return getClusterImage( clusterName, firstServiceType );
        },

        splitWithTail: function ( str, delim, count ) {
            return splitWithTail( str, delim, count );
        }


    };

    function updatePageTitle( theTitle ) {

        pageTitle=theTitle
        document.title = theTitle ;

    }

    //
    ////
    //////  Private Methods
    ////
    //

    function updateAgentNav( selectedService ) {
        console.log( `Updating nav for ${ selectedService } ` );
        if ( selectedService == utils.showDefaultServicesName() ) {
            utils.findNavigation( ".hide-when-default" ).css( "display", "none" );
        } else {
            utils.findNavigation( ".hide-when-default" ).css( "display", "flex" );

            if ( utils.isAgent() ) {
                loadServiceReport( selectedService );
            }
        }
    }

    function loadServiceReport( selectedService ) {

        let parameters = {
            name: selectedService,
            blocking: false
        };

        let instancesUrl = APP_BROWSER_URL + "/service/instances";

        console.log( `refreshing status(): ${ instancesUrl }` );

        _net.httpGet( instancesUrl, parameters )
            .then( instanceReport => {

                _dom.csapDebug( `instanceReport`, instanceReport )

                if ( instanceReport.statsConfigured ) {
                    utils.findNavigation( "#agent-statistics-nav" ).css( "display", "flex" );
                } else {
                    utils.findNavigation( "#agent-statistics-nav" ).css( "display", "none" );
                }
                if ( instanceReport.javaCollection ) {
                    utils.findNavigation( "#agent-java-nav" ).css( "display", "flex" );
                } else {
                    utils.findNavigation( "#agent-java-nav" ).css( "display", "none" );
                }
                if ( instanceReport[ "http-collect-url" ] ) {
                    utils.findNavigation( ".agent-app-nav" ).css( "display", "flex" );
                } else {
                    utils.findNavigation( ".agent-app-nav" ).css( "display", "none" );
                }

            } )
            .catch( ( e ) => {
                console.warn( e );
            } );

    }

    //
    //  eg. splitWithTail( "peter.is.testing ) returns [ "peter", "is.testing" ]
    //
    function splitWithTail( str, delim = ".", count = "1" ) {
        let parts = str.split( delim );
        let tail = parts.slice( count ).join( delim );
        let result = parts.slice( 0, count );
        result.push( tail );
        return result;
    }

    function findNavigation( selector ) {

        //console.log(`looking for ${ selector } in ${ $navigation.attr("class") }`, selector) ;
        if ( selector ) {
            return $( selector, $navigation );
        }

        return $navigation;
    }

    function getParameterByName( name ) {
        name = name.replace( /[\[]/, "\\\[" ).replace( /[\]]/, "\\\]" );
        let regexS = "[\\?&]" + name + "=([^&#]*)",
            regex = new RegExp( regexS ),
            results = regex.exec( window.location.href );
        if ( results == null ) {
            return "";
        } else {
            return decodeURIComponent( results[ 1 ].replace( /\+/g, " " ) );
        }
    }

    function browseServiceFiles( serviceName ) {

        setTimeout( function () {

            // lazy initialized
            if ( !browseServiceFunction ) {
                console.log( "waiting for browser" );
                browseServiceFiles( serviceName );
                return;
            }
            browseServiceFunction( serviceName );
        }, 1000 );
    }

    function editService( serviceName ) {
        setTimeout( function () {

            // lazy initialized
            if ( !editServiceFunction ) {
                editService( serviceName );
                return;
            }
            editServiceFunction( serviceName );
        }, 300 );
    }

    function isObject( theReference ) {

        if ( theReference == null )
            return false;
        if ( isArray( theReference ) )
            return false;
        return typeof theReference === "object";
    }

    function isArray( theReference ) {
        return Array.isArray( theReference );
    }

    function instanceRows() {
        return $( "#instance-details table tbody tr" );
    }

    function selectedInstanceRows() {
        return $( "#instance-details table tbody tr.selected" );
    }

    function buildHostsParameter( $rows ) {

        if ( !$rows ) {
            $rows = selectedInstanceRows();
        }
        let hostsParam = "";

        $rows.each( function () {

            let $row = $( this );

            let hostName = $row.data( "host" );
            if ( hostsParam !== "" ) {
                hostsParam += ",";
            }
            hostsParam += hostName;
        } );

        return hostsParam;

    }

    function agentUrl( targetHost, command = "host-dash" ) {

        switch ( command ) {

            case "files":
                //                command = "/file/FileManager" ;
                command = "/app-browser#agent-tab,file-browser";
                break;

            case "host-dash":
                //                command = "/app-browser" ;
                command = "/app-browser#agent-tab,explorer";
                break;

            case "system":
                //                command = "/app-browser" ;
                command = "/app-browser#agent-tab,system";
                break;

            case "scripts":
                //                command = "/app-browser" ;
                command = "/app-browser#agent-tab,script";
                break;

            case "logs":
                command = "/file/FileMonitor";
                break;

            default:
                break;
        }

        let theUrl;

        if ( targetHost === getHostShortName( targetHost )
            && !isIpAddress( targetHost ) ) {

            theUrl = getResolvedUrl( targetHost, command );
            //console.log("simple host") ;
        } else {
            // if multiple parts: host is either ip or fqdn
            theUrl = `http://${ targetHost }${ AGENT_ENDPOINT }${ command }`;
        }

        //console.log( `agentUrl targetHost: '${ targetHost }', pattern: '${AGENT_URL_PATTERN}', \n theUrl: ${theUrl}`)

        return theUrl;
    }

    function getResolvedUrl( targetHost, command ) {
        let privateToPublic = hostSettings[ "private-to-public" ];
        let isPreferPublicDns = $( "#prefer-public-dns", utils.findContent( "#preferences-tab-content" ) ).is( ":checked" );
        // console.log(`${ targetHost } privateToPublic`, privateToPublic) ;

        let theUrl = AGENT_URL_PATTERN.replace( /CSAP_HOST/g, targetHost ) + command;

        if ( privateToPublic
            && isPreferPublicDns ) {

            let publicHost = privateToPublic[ targetHost ];
            // console.log( `publicHost: ${ publicHost } ` );

            if ( publicHost
                && publicHost.includes( "." ) ) {

                let requestHost = window.location.hostname;
                if ( requestHost.includes( "." ) ) {
                    let reqNameAndDomain = splitWithTail( requestHost );

                    // console.log( `requestHost: ${ requestHost } , reqNameAndDomain`, reqNameAndDomain );

                    if ( reqNameAndDomain.length == 2
                        && publicHost.includes( reqNameAndDomain[ 1 ] ) ) {

                        theUrl = AGENT_URL_PATTERN.replace( /CSAP_HOST.*:/g, `${ privateToPublic[ targetHost ] }:` )
                            + command;

                    }
                }

            }


        }

        return theUrl;
    }


    function isIpAddress( ipaddress ) {
        if ( /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/.test( ipaddress ) ) {
            return true;
        }
        return false;
    }


    function getHostShortName( targetHost ) {

        if ( !targetHost ) {
            console.warn( `undefined host - defaulting to active host` );
            targetHost = CSAP_HOST_NAME;
        }

        if ( isIpAddress( targetHost ) ) {
            return targetHost;
        }
        return targetHost.split( "." )[ 0 ];
    }

    function buildAgentLink( targetHost, command, linkText, parameters ) {

        // console.debug( `targetHost: ${ targetHost }, command: ${ command}` )

        let hostUrl = agentUrl( targetHost, command );
        if ( parameters ) {
            hostUrl = buildGetUrl( hostUrl, parameters );
        }

        let commandClass = "csap-link";
        if ( command.includes( "scripts" ) ) {
            commandClass = `${ commandClass } host-command`;
        } else if ( command.includes( "FileManager" ) || command.includes( "files" ) ) {
            commandClass = `${ commandClass } host-files`;
        } else if ( command.includes( "host-dash" ) ) {
            commandClass = `${ commandClass } host-infra`;
        }

        let $hostPortalLink = jQuery( '<a/>', {
            title: `open ${ targetHost } portal in new window`,
            target: "_blank",
            class: commandClass,
            href: hostUrl,
            html: linkText
        } );


        return $hostPortalLink;
    }


    function flash( $item, doFlash, count ) {

        if ( count ) {
            for ( let i = 0; i < count; i++ ) {
                let delay = 200;
                let ms = i * delay;
                setTimeout( function () {
                    flash( $item, true );
                }, ms + 1 );
                setTimeout( function () {
                    flash( $item, false );
                }, Math.round( ms + ( delay / 2 ) ) );
            }
            return;
        }

        if ( !doFlash ) {
            $item.css( "background-color", "" );
            $item.css( "color", "" );

        } else {

            $item.css( "background-color", "#aa0000" );
            $item.css( "color", "#fff" );

        }
    }

    function buildGetUrl( url, parameterMap ) {


        // console.debug( `targetHost: ${ url }, command: ${ parameterMap}` )

        if ( !url.includes( "http" ) ) {
            url = agentUrl( CSAP_HOST_NAME, url );
        }

        const myUrlWithParams = new URL( url );

        for ( let paramName in parameterMap ) {
            let paramValue = parameterMap[ paramName ];
            if ( paramValue !== null ) {
                myUrlWithParams.searchParams.append( paramName, paramValue );
            }
        }

        console.debug( `buildGetUrl: ${ myUrlWithParams.href }, source: ${ url }, `, parameterMap );

        return myUrlWithParams.href;
    }

    function getClusterImage( clusterName, firstServiceType ) {

        console.debug( `firstServiceType: ${ firstServiceType }` );

        if ( clusterName.startsWith( "csap-m" ) ) {
            clusterName = "manager";

        } else if ( clusterName.includes( "csap-event" )
            || clusterName.includes( "postgres" )
            || clusterName.includes( "mongo" ) ) {
            clusterName = "db";

        } else if ( clusterName.includes( "autoplays" )
            || clusterName.includes( "utils" )
            || clusterName.includes( "kube-system" )
            || clusterName.includes( "kubernetes-dashboard" ) ) {
            clusterName = "autoplays";

        } else if ( clusterName.startsWith( "rni-" ) ) {
            clusterName = "rni";

        } else if ( clusterName.includes( "multijvm1" )
            || clusterName.includes( "aod" )
            || clusterName.includes( "oms-core" ) ) {
            clusterName = "wday";

        } else if ( clusterName.includes( "monitor" )
            || clusterName.includes( "metrics" )
            || clusterName.includes( "oms" ) ) {
            clusterName = "monitor";

        } else if ( clusterName.includes( "log" )
            || clusterName.includes( "elastic" ) ) {
            clusterName = "logs";

        } else if ( clusterName.includes( "auth" ) ) {
            clusterName = "auth";

        } else if ( clusterName.includes( "ingest" )
            || clusterName.includes( "kafka" )
            || clusterName.includes( "activemq" )
            || clusterName.includes( "nginx" ) ) {
            clusterName = "messaging";

        } else if ( clusterName.includes( "cron" ) ) {
            clusterName = "cron";


        } else if ( clusterName.startsWith( "kubernetes-" ) ) {
            clusterName = "kubernetes";
        }

        //console.log(`ALL_SERVICES: ${ALL_SERVICES}`) ;

        let nameMatch = {
            "autoplays": '32x32/tools.png',
            "cron": '32x32/appointment-new.png',
            "base-os": '32x32/server.svg',
            "All Services": '32x32/applications-internet.png',
            "all-namespaces": '32x32/applications-internet.png',
            "Containers Discovered": '32x32/discovery.png',
            "rni": "32x32/network-wireless.png",
            "manager": '32x32/network-workgroup.png',
            "kubernetes": 'kubernetes.svg',
            "db": 'database.png',
            "monitor": '32x32/utilities-system-monitor.png',
            "logs": '32x32/logs.png',
            "auth": '32x32/system-users.png',
            "messaging": '32x32/message.png',
            "wday": 'wday.svg',

        }

        let clusterImage = nameMatch[ clusterName ];

        if ( !clusterImage ) {
            //clusterImage = '32x32/application.png' ;
            clusterImage = '32x32/network-workgroup.png';

        }

        return clusterImage;
    }


    function menuMatch( path, tab ) {

        let $menuMatch = null;
        $( "div.tab-menu >span", $( `#${ tab }` ) ).each( function () {
            let $menu = $( this );
            if ( $menu.data( "path" ) == path ) {
                $menuMatch = $menu;
            }
        } );

        if ( $menuMatch == null ) {
            console.error( `failed to locate path ${ path }: ${ $menuMatch }, tab: ${ tab }` );
        } else {
            console.log( ` Path:  ${ path }, tab: ${ tab }, matched: ${ $menuMatch.text() }` );
        }

        return $menuMatch;
    }

    function addTableFilter( $filterInput, $tableContainer, alwaysShowFunction ) {

        $filterInput.parent().attr( "title", "filter output; comma separated items will be or'ed together. Optional: !csap will exclude csap" )

        let $clearButton = jQuery( '<button/>', { class: "csap-icon csap-remove" } )
            .appendTo( $filterInput.parent() )
            .click( function () {
                $filterInput.val( "" );
                $filterInput.trigger( "keyup" )
            } );

        $clearButton.attr( "visibility", "hidden" );


        jQuery.expr[ ':' ].ignoreCaseForHiding = function ( a, i, m ) {
            return jQuery( a ).text().toUpperCase()
                .indexOf( m[ 3 ].toUpperCase() ) >= 0;
        };
        jQuery.expr[ ':' ].ignoreCaseForNotHiding = function ( a, i, m ) {
            let searchField = m[ 3 ];

            let tableCellText = jQuery( a ).children().text();

            let found = tableCellText.toUpperCase()
                .indexOf( searchField.toUpperCase() ) == -1;

            //console.debug( `found: ${ found } searchField: ${ searchField } cell: ${ tableCellText }` ) ;
            return found;
        };

        let applyFunction = function ( $optionalTableRow = null ) {

            let $tableRows = $( 'tbody tr', $tableContainer );

            if ( $optionalTableRow ) {
                $tableRows = $optionalTableRow;
            }

            let includeFilter = $filterInput.val();

            console.debug( ` includeFilter: ${ includeFilter } ` );


            if ( includeFilter.length > 0 ) {
                $filterInput.addClass( "modified" );
                $clearButton.attr( "visibility", "visible" );
                $tableRows.hide();
                let filterEntries = includeFilter.split( "," );

                for ( let filterItem of filterEntries ) {
                    // console.debug( `filterItem: ${ filterItem }` ) ;
                    if ( filterItem.startsWith( "!" ) && filterItem.length > 1 ) {
                        $( `tr:ignoreCaseForNotHiding("${ filterItem.substring( 1 ) }")`, $tableRows.parent() ).show();
                    } else {
                        $( `td:ignoreCaseForHiding("${ filterItem }")`, $tableRows ).parent().show();
                    }
                }

            } else {
                $tableRows.show();
                $filterInput.removeClass( "modified" );
                $clearButton.attr( "visibility", "hidden" );
            }

            if ( alwaysShowFunction ) {
                alwaysShowFunction();
            }

        }

        $filterInput.off().keyup( function () {
            //console.log( "Applying template filter" ) ;
            clearTimeout( _statusFilterTimer );
            _statusFilterTimer = setTimeout( function () {
                applyFunction();
            }, 500 );
        } );

        return applyFunction;
    }

    function isFloat( n ) {
        return Number( n ) === n && n % 1 !== 0;
    }

    function bytesFriendlyDisplay( numBytes ) {

        if ( numBytes === 0 ) {
            return 0;
        }

        if ( !numBytes
            || typeof ( numBytes ) == "undefined"
            || numBytes === undefined ) {
            return "-";
        }
        let resultNum = numBytes;
        let resultString = `${ resultNum }`;
        let resultUnits = `-`;

        if ( Number.isInteger( numBytes ) ) {
            resultUnits = `b`;
            if ( resultNum > 1024 ) {
                resultNum = resultNum / 1024;
                resultUnits = `kb`;
            }
            if ( resultNum > 1024 ) {
                resultNum = resultNum / 1024;
                resultUnits = `mb`;
            }

            if ( resultNum > 1024 ) {
                resultNum = resultNum / 1024;
                resultUnits = `gb`;
            }

            resultString = resultNum.toFixed( 1 );
            if ( resultString.endsWith( "\.0" ) ) {
                resultString = resultNum.toFixed( 0 );
            }
        }

        return `${ resultString } ${ resultUnits }`;
    }

    function launch( url, windowFrameName = "_blank" ) {

        console.log( `launching url: ${ url } to window: ${ getValidWinName( windowFrameName ) }` );

        window.open( encodeURI( url ), getValidWinName( windowFrameName ) );
    }

    function getValidWinName( inputName ) {
        let regex = new RegExp( "-", "g" );
        let validWindowName = inputName.replace( regex, "" );

        regex = new RegExp( " ", "g" );
        validWindowName = validWindowName.replace( regex, "" );

        return validWindowName;
    }

    function getHostTag( hostName, replaceDelims = true ) {

        // console.log( `hostName: ${hostName} `, hostTagReport ) ;

        if ( hostSettings && hostSettings.tags ) {

            let hostTag = hostSettings.tags[ hostName ];
            if ( !hostTag && hostSettings.tags[ "default" ] ) {
                hostTag = hostSettings.tags[ "default" ];
            }

            if ( hostTag ) {
                if ( !replaceDelims ) return hostTag;
                return hostTag.replaceAll( "!", "" );
            }


        }

        return null;


    }


    function getHostHelperUrl( hostName ) {

        // console.log( `hostName: ${hostName} `, hostSettings ) ;

        if ( hostSettings && hostSettings.helperUrl ) {

            let targetUrl = hostSettings.helperUrl.replaceAll( `HOSTNAME`, hostName );
            let $helperLink = jQuery( '<a/>', {
                title: `open ${ targetUrl } support portal in a new window`,
                target: "_blank",
                class: "csap-link-icon csap-help",
                href: targetUrl,
                html: "&nbsp"
            } );
            //$helperLink.css("margin-right", "1em") ;
            return $helperLink;
        }

        return null;


    }

} 