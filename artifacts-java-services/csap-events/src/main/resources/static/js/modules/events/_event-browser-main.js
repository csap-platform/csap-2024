
console.log( `loading imports` );

import "../libs/csap-modules.js";

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";

import searchModule from "./search.js"
import adminModule from "./admin.js"
import table from "./event-table.js"



_dom.onReady( function () {

	// _utils.prefixColumnEntryWithNumbers( $( "table" ) );

	let appScope = new events_main();

	_dialogs.loading( "start up" );

	// subModule.showValues() ;



	appScope.initialize();

} );

function events_main() {


	_dom.logHead( "Main module" );

	this.initialize = function () {

		searchModule.initialize();
		adminModule.initialize();
		table.initialize();


		setTimeout( () => {
			_dialogs.loadingComplete();
		}, 500 );
	}


}

if ( typeof String.prototype.startsWith != 'function' ) {
	// see below for better implementation!
	String.prototype.startsWith = function ( str ) {
		return this.indexOf( str ) == 0;
	};
}
