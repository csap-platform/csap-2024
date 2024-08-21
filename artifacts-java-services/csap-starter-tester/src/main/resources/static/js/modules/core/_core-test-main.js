import "../libs/csap-modules.js";
import "../utils/table-test-page.js";

import { _dialogs, _dom, _net } from "../utils/all-utils.js";

_dom.onReady( function () {

	let appScope = new DemoManager( globalThis.settings );

	_dialogs.loading( "start up" );

	appScope.initialize();

} );


function DemoManager() {

	// note the public method
	this.initialize = function () {


		_dom.logSection( `initializing main ` );

		//
		// Heap Tester
		//

		let $heapStrings = $( "#heap-strings-general" ) ;
		let $heapStringsLong = $( "#heap-strings-long" ) ;
		let $heapStringsShort = $( "#heap-strings-short" ) ;
		let $heapObjectsLong = $( "#heap-objects-long" ) ;
		let $heapObjectsShort = $( "#heap-objects-short" ) ;
		let $heapAllocate = $( "#heap-allocate" ) ;
		let $heapClear = $( "#heap-clear" ) ;
		let $heapRefresh = $( "#heap-refresh" ) ;
		function updateHeapAllocCount( report, bgColor = "grey" ) {

			$("#heap-current-allocations").removeClass( "csap-white" );
			$("#heap-current-allocations").addClass( "csap-blue");
			$heapStrings.val( report.generalStringSize ) ;

			$heapStringsShort.val( report.shorterStringSize ) ;
			$heapStringsLong.val( report.longerStringSize ) ;

			$heapObjectsShort.val( report.shortLivedObjects ) ;
			$heapObjectsLong.val( report.longLivedObjects ) ;

			$( "#heap-classes-long" ).val( report.longLivedClasses ) ;
			$( "#heap-classes-short" ).val( report.shortLivedClasses ) ;

			setTimeout( function () {
				$("#heap-current-allocations").removeClass( "csap-blue" );
				$("#heap-current-allocations").addClass( "csap-white");
			}, 500 )
		}

		$heapAllocate.click( function () {
			let parameters={
				mb: $( "#heap-mb" ).val(),
				kb: $( "#heap-kb" ).val(),
				longAndShort: $( "#heap-long-short" ).is(':checked'),
				objects: $( "#heap-use-objects" ).is(':checked'),
				classesToLeak: $("#heap-classes").val()
			} ;
			console.log( "heap allocation", parameters ) ;

			_dialogs.loading( "allocating heap" );
			_net.httpGet(  "/api/heap/allocate", parameters )
				.then( updateHeapAllocCount )
				// .then( report => {
				//     console.log( `report: `, report )
				//     $( "#meta-test-current" ).val( report.currentSize )
				// } )
				.catch( ( errorThrown ) => {
					console.warn( errorThrown );
					handleConnectionError( "Getting Items in DB", errorThrown );
				} )
				.finally( () => {
					console.log("completed") ;
					_dialogs.loadingComplete() ;
				});

		} );

		$heapClear.click( function () {
			let parameters={} ;
			_dialogs.loading( "clearing metadata classes" );
			_net.httpGet(  "/api/heap/free", parameters )
				.then( updateHeapAllocCount )
				.catch( ( errorThrown ) => {
					console.warn( errorThrown );
					handleConnectionError( "Getting Items in DB", errorThrown );
				} )
				.finally( () => {
					console.log("completed") ;

					_dialogs.loadingComplete() ;
				});
		} );

		$heapRefresh.click( function () {
			let parameters={} ;
			_dialogs.loading( "updating counts" );
			_net.httpGet(  "/api/heap/refresh", parameters )
				.then( updateHeapAllocCount )
				.catch( ( errorThrown ) => {
					console.warn( errorThrown );
					handleConnectionError( "Getting Items in DB", errorThrown );
				} )
				.finally( () => {
					console.log("completed") ;

					_dialogs.loadingComplete() ;
				});
		} );

		//
		// Java native memory testing
		//

		function showNativeMemoryResults( report, bgColor = "grey" ) {

			console.log( `report: `, report )

			alertify.csapInfo(JSON.stringify( report, null, "\t", )  );
		}

		$( "#native-memory-run" ).click( function () {
			let parameters={
				threads: $( "#native-memory-threads" ).val(),
				iterations: $( "#native-memory-iterations" ).val()
			} ;

			_dialogs.loading( "allocating native memory by scanning resources, and checking if resource.isReadable" );
			_net.httpGet(  "/api/native-memory/run", parameters )
				.then( showNativeMemoryResults )
				// .then( report => {
				//     console.log( `report: `, report )
				//     $( "#meta-test-current" ).val( report.currentSize )
				// } )
				.catch( ( errorThrown ) => {
					console.warn( errorThrown );
					handleConnectionError( "Getting Items in DB", errorThrown );
				} )
				.finally( () => {
					console.log("completed") ;
					_dialogs.loadingComplete() ;
				});

		} );

		//
		//  Meta tester
		//

		function updateMetaClassCount( report, bgColor = "grey" ) {

			$( "#meta-test-current" ).val( report.currentSize ) ;
			$( "#meta-test-current" ).css( "background-color", bgColor );

			setTimeout( function () {
				$( "#meta-test-current" ).css( "background-color", "white" );
			}, 500 )
		}

		$( "#meta-test-create" ).click( function () {
			let parameters={
				numberOfClasses: $( "#meta-test-create-count" ).val()
			} ;

			_dialogs.loading( "allocating metadata classes" );
			_net.httpGet(  "/api/metaSpace/add", parameters )
				.then( updateMetaClassCount )
				// .then( report => {
				//     console.log( `report: `, report )
				//     $( "#meta-test-current" ).val( report.currentSize )
				// } )
				.catch( ( errorThrown ) => {
					console.warn( errorThrown );
					handleConnectionError( "Getting Items in DB", errorThrown );
				} )
				.finally( () => {
					console.log("completed") ;
					_dialogs.loadingComplete() ;
				});

		} );

		$( "#meta-test-clear" ).click( function () {
			let parameters={} ;
			_dialogs.loading( "clearing metadata classes" );
			_net.httpGet(  "/api/metaSpace/clear", parameters )
				.then( updateMetaClassCount )
				.catch( ( errorThrown ) => {
					console.warn( errorThrown );
					handleConnectionError( "Getting Items in DB", errorThrown );
				} )
				.finally( () => {
					console.log("completed") ;

					_dialogs.loadingComplete() ;
				});
		} );

		$( "#meta-test-gc" ).click( function () {

			let parameters={} ;
			_dialogs.loading( "performing garbage collection" );
			_net.httpGet(  "/api/garbageCollection", parameters )
				.then( updateMetaClassCount )
				.catch( ( errorThrown ) => {
					console.warn( errorThrown );
					handleConnectionError( "Getting Items in DB", errorThrown );
				} )
				.finally( () => {
					console.log("completed") ;
					_dialogs.loadingComplete() ;
				});
		} );

		//
		//  Heap Load configuration
		//

		function updateHeapObjectSettings(  report, bgColor = "grey" ) {


			console.log( `report: `, report )
			$( "#heap-test-enabled" ).val( report.isHeapTestEnabled )
			$( "#heap-test-create" ).val( report.objectsToAllocate )
			$( "#heap-test-meta-classes" ).val( report.classesToAllocate )
			$( "#heap-test-clear" ).val( report.resetInterval )

			$( "#heap-test-td" ).css( "background-color", bgColor );

			setTimeout( function () {
				$( "#heap-test-td" ).css( "background-color", "white" );
			}, 500 )
		}

		$( '#heap-test-refresh' ).click( function ( e ) {
			$.getJSON( "/api/heap/test/refresh", {
				dummyParam: "dummy"

			} ).done( updateHeapObjectSettings )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Getting Items in DB", errorThrown );
				} );
		} );

		setTimeout( function () {
			$( '#heap-test-refresh' ).trigger( "click" );
		}, 500 )

		$( '#heap-test-update' ).click( function ( e ) {
			$.getJSON( "/api/heap/test/update", {
				objectsToAllocate: $( "#heap-test-create" ).val(),
				resetInterval: $( "#heap-test-clear" ).val(),
				classesToAllocate: $( "#heap-test-meta-classes" ).val()

			} ).done( function ( report ) {
				updateHeapObjectSettings(report, `#a2edba` );
				alertify.csapInfo( report.summary );
			} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Getting Items in DB", errorThrown );
				} );
		} );

		$( '#dbConnectionTest' ).click( function ( e ) {


			var message = "Testing Db Connection ";
			try {
				_dialogs.notify( message );

				// delay to display notification
				setTimeout( testDbConnection, 500 );
			} catch ( e ) {
				console.log( e )
			}
		} );

		$( '.showData' ).click( function ( e ) {


			var message = "Getting items from DB ";
			_dialogs.notify( message );

			setTimeout( getData, 500 );
		} );

		$( '.longTime' ).click( function ( e ) {

			let $theForm = $( this ).closest( "form" );

			e.preventDefault();
			// alertify.alert( "Note: this request might take a while. Once completed - the results will be displayed" );
			alertify.confirm(
				'CSAP Launcher',
				`Note: this request might take a while. Once completed - the results will be displayed`,
				function () {
					$theForm.submit()
				},
				function () {
					alertify.notify( 'Cancelled' )
				}
			);

		} );

		if ( $( "#inlineResults" ).text() != "" ) {
			alertify.csapInfo( '<pre style="font-size: 0.8em">'
				+ $( "#inlineResults" ).text() + "</pre>" );
		}

		setTimeout( _dialogs.loadingComplete, 500 );

	};

	function testDbConnection() {

		$( 'body' ).css( 'cursor', 'wait' );
		$.post( $( '#dbConnectionForm' ).attr( "action" ), $( '#dbConnectionForm' )
				.serialize(), function ( data ) {
				// alertify.alert(data) ;
				_dialogs.dismissAll();
				_dialogs.csapInfo( '<pre style="font-size: 0.8em">' + data
					+ "</pre>" )
				$( 'body' ).css( 'cursor', 'default' );
			}, 'text' // I expect a JSON response
		);
	}

	function getData() {


		_dialogs.loading( "Getting data" );

		$.getJSON( window.baseUrl + "api/showTestDataJson", {
			dummyParam: "dummy"

		} ).done( getDataSuccess )

			.fail( function ( jqXHR, textStatus, errorThrown ) {

				handleConnectionError( "Getting Items in DB", errorThrown );
			} );
	}

	function getDataSuccess( dataJson ) {

		// _dialogs.notify( "Number of items in DB:" + dataJson.count );

		$( ".alertify-logs" ).css( "width", "800px" );

		var table = $( "#ajaxResults table" ).clone();

		for ( var i = 0; i < dataJson.data.length; i++ ) {

			var trContent = '<td style="padding: 2px;text-align: left">'
				+ dataJson.data[ i ].id
				+ '</td><td style="padding: 2px;text-align: left">'
				+ dataJson.data[ i ].description + '</td>';
			var tr = $( '<tr />', {
				'class': "peter",
				html: trContent
			} );
			table.append( tr );
		}

		var message = "Number of records displayed: " + dataJson.data.length
			+ ", of total in db: " + dataJson.count + "<br><br>";
		if ( dataJson.count == 0 ) {
			var trContent = '<td style="padding: 2px;text-align: left">-</td><td style="padding: 2px;text-align: left">No Data Found</td>';
			var tr = $( '<tr />', {
				'class': "peter",
				html: trContent
			} );
			table.append( tr );
		}
		_dialogs.dismissAll();
		_dialogs.csapInfo( message + table.clone().wrap( '<p>' ).parent().html() );

		setTimeout( _dialogs.loadingComplete, 500 );
	}

	function handleConnectionError( command, errorThrown ) {
		var message = "<pre>Failed connecting to server";
		message += "\n\n Server Message:" + errorThrown;
		message += "\n\n Click OK to reload page, or cancel to ignore.</pre>";

		alertify.csapWarning( message );
		$( 'body' ).css( 'cursor', 'default' );
	}

}
