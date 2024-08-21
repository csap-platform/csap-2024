
// export function 


export const getParameterByName = function ( name ) {
	name = name.replace( /[\[]/, "\\\[" ).replace( /[\]]/, "\\\]" );
	var regexS = "[\\?&]" + name + "=([^&#]*)", regex = new RegExp( regexS ), results = regex
		.exec( window.location.href );
	if ( results == null ) {
		return "";
	} else {
		return decodeURIComponent( results[ 1 ].replace( /\+/g, " " ) );
	}
}

export const precise_round = function ( value, decPlaces ) {
	var val = value * Math.pow( 10, decPlaces );
	var fraction = ( Math.round( ( val - parseInt( val ) ) * 10 ) / 10 );
	if ( fraction == -0.5 )
		fraction = -0.6;
	val = Math.round( parseInt( val ) + fraction ) / Math.pow( 10, decPlaces );
	return val;
}
