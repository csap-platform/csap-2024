
console.log(`hi`)


const queryString = window.location.search;
console.log(queryString);

const urlParams = new URLSearchParams(queryString);

let screencastParam=urlParams.get('screencast') ;

if ( ! screencastParam ) {
    console.log("switching to default") ;
    screencastParam = "ssh"
}

console.log( `updating video source: ${ "screencast"}` ) ;
$("video source").attr("src", `screencasts/${ screencastParam }.mp4`) ;


