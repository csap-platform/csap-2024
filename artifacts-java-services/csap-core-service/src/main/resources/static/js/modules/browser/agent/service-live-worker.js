// import _dom from "../../utils/dom-utils.js";
//import _net from "../../utils/net-utils.js";

// import utils from "../utils.js"


self.serviceLive = Service_Live_Worker();




function Service_Live_Worker() {

    console.log( "\n\n\n Module loaded \n\n\n" );

    onmessage = workRequest;

    function workRequest( workerRequest ) {

        const liveRequest = workerRequest.data[ 0 ];
        // console.log( `Received request: `, liveRequest );

        switch ( liveRequest.command ) {
            case `getMicroMeters`:

                getMicroMeters( liveRequest.microUrl, liveRequest.params, liveRequest.meterView );
                break;

            default:
                console.warn( `Unexpected command ${ liveRequest.command }` );
                break;
        }

    }

    function httpGet( urlString = '', parameters = {}, isText = false ) {

        let url = urlString;

        console.debug( `Hitting: ${ url } ` );

        if ( parameters
            && Object.keys( parameters ).length > 0
            && Object.getPrototypeOf( parameters ) === Object.prototype ) {
            // Strip out null items 
            for ( let paramName in parameters ) {
                console.debug( `paramName: '${ paramName }', value: '${ parameters[ paramName ] }' ` );
                if ( parameters[ paramName ] == null ) {
                    console.debug( `deleting paramName: '${ paramName }', value: '${ parameters[ paramName ] }' ` );
                    delete parameters[ paramName ];
                }
            }
            url += '?' + ( new URLSearchParams( parameters ) ).toString();
            // url = new URL( urlString, globalThis.settings.BASE_URL );
            // url.search = new URLSearchParams( data ).toString();
        }

        // Default options are marked with *

        const options = {
            method: 'GET', // *GET, POST, PUT, DELETE, etc.
            mode: 'cors', // no-cors, *cors, same-origin
            cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
            credentials: 'same-origin', // include, *same-origin, omit
            headers: {
                // 'Content-Type': 'application/json'
                // 'Content-Type': 'application/x-www-form-urlencoded',
            },
            redirect: 'follow', // manual, *follow, error
            referrerPolicy: 'no-referrer', // no-referrer, *no-referrer-when-downgrade, origin, origin-when-cross-origin, same-origin, strict-origin, strict-origin-when-cross-origin, unsafe-url

        }


        let jsonPromise = fetch( url, options )
            .then( response => {
                console.debug( `response` );

                // _dom.csapDebug( ` response: `, response );

                if ( !response.ok ) {
                    throw new Error( `response status: ${ response.status } url: ${ url }` );
                }

                if ( isText ) {
                    return response.text();
                }
                return response.json();
            } );


        return jsonPromise;

    }


    function getMicroMeters( url, parameters, meterView ) {


        let viewLoadedPromise = httpGet( url, parameters );

        viewLoadedPromise
            .then( function ( report ) {
                processMicroMeters( report, meterView )
            } )
            .catch( ( e ) => {
                console.warn( "Failed processing report2", e );

            } );;


        return viewLoadedPromise;

    }

    // my_prom_app_id_func1_getattribute_count{tenant="tenant-1",}
    function buildShortName( fullName, suffix, tenant ) {

        let maxWordSize = 25;

        let shortName = fullName;
        if ( fullName.endsWith( suffix ) ) {
            shortName = fullName.substr( 0, fullName.length - suffix.length );
        }
        let SPLITCHAR = " ";
        shortName = shortName.replaceAll( "my_prom_app_id_", "" ).replaceAll( "_", SPLITCHAR );

        let wordsInName = shortName.split( SPLITCHAR );

        let shorterWordsName = "";
        for ( let nameWord of wordsInName ) {

            if ( shorterWordsName != "" ) {
                shorterWordsName += SPLITCHAR;
            }
            if ( nameWord.length > maxWordSize ) {
                shorterWordsName += nameWord.substr( 0, maxWordSize ) + "-";
            } else {
                shorterWordsName += nameWord;
            }
        }

        if ( tenant ) {
            shorterWordsName = `${ shorterWordsName }${ SPLITCHAR }${ tenant }`;
        }

        return shorterWordsName;
    }

    function buildNameValueCountReport( collectionReport ) {

        let meterCountReport = {};

        for ( let line of collectionReport.response.split( "\n" ) ) {

            if ( line.startsWith( "#" ) || line.trim() == "" ) {
                continue;
            }

            //debug
            //if ( !line.contains( "my_prom_app_id_txn_messages_txncommitted" ) ) continue;


            try {
                let fullNameCommaValue = line.split( " " );

                if ( fullNameCommaValue.length == 2 ) {

                    let fullNameWithBrace = fullNameCommaValue[ 0 ];

                    let nameAndTags = fullNameWithBrace.split( "{" );
                    let meterName = nameAndTags[ 0 ];
                    let preName = meterName;

                    if ( meterName.endsWith( "count" ) ) {

                    }
                    meterName = buildShortName( meterName, "" );
                    // let nameWords = meterName.split( "_" );
                    // let numNameWords = nameWords.length;

                    // // console.log("nameWords", nameWords)

                    // if ( numNameWords > 3 ) {
                    //     meterName = `${ nameWords[ numNameWords - 3 ] }-${ nameWords[ numNameWords - 2 ] }`;
                    // }

                    let tags = new Object();
                    if ( nameAndTags.length = 2 ) {
                        let tagsArray = nameAndTags[ 1 ].replaceAll( "}", "]" ).split( "," );
                        for ( let tagDef of tagsArray ) {
                            if ( tagDef.length > 3 ) {
                                let tagParts = tagDef.split( "=" );
                                if ( tagParts.length == 2 ) {
                                    tags[ tagParts[ 0 ] ] = tagParts[ 1 ];
                                }
                            }
                        }
                    }

                    // .replaceAll( "{", "[" )
                    // .replaceAll( "}", "]" );

                    // my_prom_app_id_txn_messages_txncommitted_sendmillis95percentile{tenant="35k_cba_plat_20",quantile="0.95",} 0.0
                    // my_prom_app_id_txn_messages_txncommitted_sendmillismedian{tenant="35k_cba_plat_20",quantile="0.5",} 0.0
                    // my_prom_app_id_txn_messages_txncommitted_sendmillis_count{tenant="35k_cba_plat_20",} 2710.0
                    // my_prom_app_id_txn_messages_txncommitted_sendmillisrate{tenant="35k_cba_plat_20",} 2.8163855302173503E-128

                    //if ()

                    let metricReport = {
                        details: {
                            description: fullNameCommaValue[ 0 ],
                            tags: tags
                        },
                        count: Number( fullNameCommaValue[ 1 ] )
                    }

                    let uniqueName = meterName;
                    let count = 0;
                    while ( meterCountReport[ uniqueName ] ) {
                        uniqueName = `${ meterName }-${ ++count }`;
                    }

                    meterCountReport[ uniqueName ] = metricReport;
                }
            } catch ( suberr ) {
                console.debug( `Failed to process meter name values  `, suberr );
            }
        }

        return meterCountReport;
    }

    function buildNameValueTypeReport( collectionReport ) {

        // treat as name value pairs

        let metersByType = new Object();
        for ( let line of collectionReport.response.split( "\n" ) ) {

            if ( line.startsWith( "#" ) || line.trim() == "" ) {
                continue;
            }


            //debug
            // if ( !line.contains("my_prom_app_id_txn_messages_txncommitted") ) continue ;

            try {
                let fullNameCommaValue = line.split( " " );

                if ( fullNameCommaValue.length == 2 ) {
                    let fullNameWithBrace = fullNameCommaValue[ 0 ];
                    let valueFound = fullNameCommaValue[ 1 ];

                    let nameAndTags = fullNameWithBrace.split( "{" );
                    let meterName = nameAndTags[ 0 ];



                    let tags = new Object();

                    let tenant;
                    if ( Array.isArray( nameAndTags )
                        && nameAndTags.length === 2 ) {

                        let tagsArray = nameAndTags[ 1 ].replaceAll( "}", "" ).split( "," );
                        for ( let tagDef of tagsArray ) {
                            if ( tagDef.length > 3 ) {
                                let tagParts = tagDef.split( "=" );
                                if ( tagParts.length == 2
                                    && tagParts[ 0 ] == "tenant" ) {
                                    tenant = tagParts[ 1 ].replaceAll( `"`, `` );
                                    tags[ tagParts[ 0 ] ] = tenant;
                                }
                            }
                        }
                    }

                    function addDetails( meterDef ) {
                        meterDef[ "details" ] = {
                            description: fullNameWithBrace,
                            tags: tags
                        }
                    }


                    let currentValue = 0;
                    if ( meterName.endsWith( "_count" ) ) {
                        let shortName = buildShortName( meterName, "_count", tenant );
                        let meterReport = metersByType[ shortName ];
                        if ( !meterReport ) {
                            metersByType[ shortName ] = new Object();
                            meterReport = metersByType[ shortName ]
                            addDetails( metersByType[ shortName ] );
                        } else {

                            if ( meterReport.count ) {
                                currentValue = meterReport.count;
                                //console.log(`Multiple matches for ${ shortName }: ${ currentValue }`) ;
                            }
                        }
                        meterReport.count = Number( valueFound ) + currentValue;
                        if ( meterName.includes( "total" ) ) {
                            meterReport[ "total-ms" ] = Number( valueFound ) + currentValue;
                        }
                    } else if ( meterName.endsWith( "median" ) ) {
                        let shortName = buildShortName( meterName, "median", tenant );
                        let meterReport = metersByType[ shortName ];

                        if ( !meterReport ) {
                            metersByType[ shortName ] = new Object();
                            meterReport = metersByType[ shortName ];
                            addDetails( meterReport );
                        } else {

                            if ( meterReport[ "mean-ms" ] ) {
                                currentValue = meterReport[ "mean-ms" ];
                                //console.log(`Multiple matches for ${ shortName }: ${ currentValue }`) ;
                            }
                        }
                        meterReport[ "mean-ms" ] = Number( valueFound ) + currentValue;

                    } else if ( meterName.endsWith( "95percentile" ) ) {
                        let shortName = buildShortName( meterName, "95percentile", tenant );
                        if ( !metersByType.hasOwnProperty( shortName ) ) {
                            metersByType[ shortName ] = new Object();
                            addDetails( metersByType[ shortName ] );
                        }
                        metersByType[ shortName ][ "bucket-0.95-ms" ] = Number( valueFound );

                    } else if ( meterName.endsWith( "rate" ) ) {
                        let shortName = buildShortName( meterName, "rate", tenant );
                        if ( !metersByType.hasOwnProperty( shortName ) ) {
                            metersByType[ shortName ] = new Object();
                            addDetails( metersByType[ shortName ] );
                        }
                        metersByType[ shortName ][ "bucket-max-ms" ] = Number( valueFound );

                    } else {
                        let shortName = buildShortName( meterName, "", tenant );
                        // bare value
                        metersByType[ shortName ] = {
                            count: Number( valueFound )
                        }
                        addDetails( metersByType[ shortName ] );
                    }

                }

            } catch ( suberr ) {
                console.debug( `Failed to process meter name values  `, suberr );
            }
        }

        // console.log( `metersByType`, metersByType );

        return metersByType;
    }

    function processMicroMeters( collectionReport, meterView ) {
        //console.log( "processMicroMeters() ", collectionReport ) ;

        let workerResponse = {
            isPrometheus: false
        };
        let microMeterReport = {};



        try {
            microMeterReport = JSON.parse( collectionReport.response );
        } catch ( err ) {

            console.debug( `parsing using prometheus text format`) ;

            // if ( window.csapdebug ) {
            //     _dom.csapDebug( err ) ;
            // }

            // update view settings
            workerResponse.isPrometheus = true;

            if ( meterView == "apiDetails" ) {
                microMeterReport = buildNameValueCountReport( collectionReport );
            } else {
                microMeterReport = buildNameValueTypeReport( collectionReport );
            }

            // use csap-agent collection interval as the source
            microMeterReport[ "process.uptime" ] = collectionReport[ "collect-seconds" ];
        }

        workerResponse.meterReport = microMeterReport ;

        // console.log( `workerResponse`, workerResponse );

        postMessage(workerResponse);

    }

}
