// import "../../../../webjars/alertifyjs/1.13.1/alertify.js";
// import "../../../../webjars/jquery/3.6.4/jquery.min.js";


import _dom from "./dom-utils.js";


export default function Dialogs() {

}


let $loadingMessage = $( "#loading-project-message" );

_dom.onReady( function () {

    // _dom.logHead( "ready") ;
    //let $loadingMessage = $( "#loading-project-message" ) ;
    configureCsapAlertify();

    configureCsapToolsMenu();

} );




Dialogs.loading = function ( message ) {
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
}

Dialogs.loadingComplete = function ( source ) {

    if ( $loadingMessage.is( ":visible" ) ) {
        console.log( `loadingComplete - hiding message: ${ source } ` );
    }
    $loadingMessage.hide();
}


function configureCsapToolsMenu() {

    let $toolsMenu = $( "header .csapOptions select" );

    $toolsMenu.on( "change", function () {
        let item = $( "header .csapOptions select" ).val();

        if ( item != "default" ) {
            console.log( "launching: " + item );
            if ( item.indexOf( "logout" ) == -1 ) {
                openWindowSafely( item, "_blank" );
            } else {
                document.location.href = item;
            }
            $( "header .csapOptions select" ).val( "default" )
        }

        $toolsMenu.val( "default" );

    } );
}



Dialogs.dismissAll = function () {
    alertify.dismissAll();
}



Dialogs.notify = function ( message ) {
    alertify.notify( message );
}

Dialogs.csapHtml = function ( message ) {
    alertify.csapHtml( message );
}


Dialogs.csapInfo = function ( message ) {
    alertify.csapInfo( message );
}

Dialogs.csapWarning = function ( message ) {
    alertify.csapWarning( message );
}


Dialogs.isFunction = function ( functionToCheck ) {
    return functionToCheck && {}.toString.call( functionToCheck ) === '[object Function]';
    //    return typeof functionToCheck === "function" ;
};

Dialogs.dialog_factory_builder = function ( configuration ) {

    let factory = function () {
        return Dialogs.build_alertify_factory( configuration );
    };


    return factory;
};


//                  configuration.content,
//                configuration.onresize,
//                configuration.onclose,
//                configuration.onok,
//                configuration.onshow,
//                configuration.width,
//                configuration.height,
//                configuration.buttons
Dialogs.build_alertify_factory = function (
    configuration ) {

    let _windowResizeTimer = null;


    // let isResizeEnabled = true ;

    let alertifyResize = function ( alertifyDialog ) {

        if ( !alertifyDialog.elements ) {
            console.log( `build_alertify_factory.alertifyResize(): dialog not visible, skipping resize` );
            return;
        }

        let targetWidth = Math.round( $( window ).outerWidth( true ) ) - 20;
        let targetHeight = Math.round( $( window ).outerHeight( true ) ) - 20;

        if ( Dialogs.isFunction( configuration.getWidth ) ) {
            let specWidth = Math.round( configuration.getWidth() );
            //alertifyDialog.elements.dialog.style.marginLeft = (targetWidth - specWidth) + "px";
            targetWidth = specWidth;
        }
        if ( Dialogs.isFunction( configuration.getHeight ) ) {
            let specHeight = Math.round( configuration.getHeight() );
            //alertifyDialog.elements.dialog.style.marginTop = (targetHeight - specHeight) + "px";
            targetHeight = specHeight;
        }

        console.log( `alertify_frameless_factory() targetWidth: ${ targetWidth },  targetHeight: ${ targetHeight } ` );

        alertifyDialog.elements.dialog.style.maxWidth = 'none';
        alertifyDialog.elements.dialog.style.width = targetWidth + "px";
        alertifyDialog.elements.dialog.style.maxHeight = 'none';
        alertifyDialog.elements.dialog.style.height = targetHeight + "px";

        if ( Dialogs.isFunction( configuration.onresize ) ) {
            configuration.onresize( targetWidth, targetHeight );
        } else {
            console.log( "resize function not found" );
        }

    };

    let alertifyWindowResizeScheduler = function ( alertifyDialog ) {

        clearTimeout( _windowResizeTimer );

        // put in background to wait for alertify instantiation
        _windowResizeTimer = setTimeout( function () {
            alertifyResize( alertifyDialog );
        }, 500 );
    };

    let maximizable = false;
    if ( configuration.maximizable ) {
        maximizable = configuration.maximizable;
    }

    let factory = {
        build: function () {
            // Move dom content from template
            this.setContent( configuration.content );

            this.setting( {
                onok: function ( closeEvent ) {

                    console.log( "dialogFactory(): dialog event:  ", JSON.stringify( closeEvent ) );

                    if ( Dialogs.isFunction( configuration.onok ) ) {
                        configuration.onok( closeEvent );
                    }

                },
            } );

        },

        setup: function () {
            return {

                buttons: configuration.buttons,

                options: {
                    // title: "Kubernetes YAML Deployment",
                    resizable: true,
                    autoReset: false,
                    movable: true,
                    maximizable: maximizable,
                    frameless: true,
                    title: false,
                    overflow: false,
                    transition: "fade", // fade, zoom, pulse

                    onmaximized: function () {
                        console.log( "alertify maxed" );

                        if ( Dialogs.isFunction( configuration.onmax ) ) {
                            configuration.onmax( this.elements.dialog.offsetWidth, this.elements.dialog.offsetHeight );
                        }


                        if ( Dialogs.isFunction( configuration.onresize ) ) {
                            configuration.onresize( this.elements.dialog.offsetWidth, this.elements.dialog.offsetHeight );
                        }

                    },
                    onrestored: function () {
                        console.log( "alertify restored" );

                        if ( Dialogs.isFunction( configuration.onrestore ) ) {
                            configuration.onrestore( this.elements.dialog.offsetWidth, this.elements.dialog.offsetHeight );
                        }


                        if ( Dialogs.isFunction( configuration.onresize ) ) {
                            configuration.onresize( this.elements.dialog.offsetWidth, this.elements.dialog.offsetHeight );
                        }

                    },

                    onresized: function () {
                        console.log( "alertify resized" );

                        if ( Dialogs.isFunction( configuration.onresize ) ) {
                            configuration.onresize( this.elements.dialog.offsetWidth, this.elements.dialog.offsetHeight );
                        }
                        // isResizeEnabled = false ;
                    },

                    onclose: function () {
                        console.log( "alertify onclose" );

                        if ( Dialogs.isFunction( configuration.onclose ) ) {
                            configuration.onclose( this );
                        }

                    },

                    onshow: function () {

                        let currentDialog = this;

                        alertifyResize( currentDialog );

                        $( window ).resize( function () {
                            alertifyWindowResizeScheduler( currentDialog )
                        } );



                        if ( Dialogs.isFunction( configuration.onshow ) ) {
                            configuration.onshow();
                        }

                    },
                }

            };
        }

    }
    return factory;
}

Dialogs.showConfirmDialog = function(
        title, message,
        okFunction, cancelFunction,
        okLabel="Proceed", cancelLabel="Cancel",
        theStyle = `csap-info`,
        resizeable = false,
        optionalHeightAdd = 0) {

    let $messageInHtml = jQuery( '<div/>', {} );

    let uid= `confirm-${ (new Date()).getTime() }` ;

    jQuery( '<div/>', {
        id: uid,
        class: `${ theStyle }`,
        html: message
    } )
        .css( "margin-top", "2em" )
        .css( "font-weight", "bold" )
        .appendTo( $messageInHtml );

    let agentConfirm = alertify.confirm(
        $messageInHtml.html(),
        okFunction,
        cancelFunction
    );
    agentConfirm.setting( {
        title: title ,
        resizable: resizeable,
        autoReset: false,
        onclose: function() {
            console.log( `Closing ${ title }` ) ;
            // reset button labels back to defaults
            agentConfirm.setting( {
                'labels': {
                    ok: "ok",
                    cancel: "cancel"
                }
            } );
            //
            // setTimeout( function() {
            //     alertify.confirm("verify reset labels")
            // }, 1000)
        },
        'labels': {
            ok: okLabel,
            cancel: cancelLabel
        }
    } );

    if ( resizeable ) {
        Dialogs.resizeDialog( agentConfirm, $( `#${ uid }`).parent(), optionalHeightAdd ) ;
    }


    // console.log(`Scheduling resize: ${ uid }`) ;
    // function waitForElementToExist(selector) {
    //     return new Promise(resolve => {
    //         if (document.querySelector(selector)) {
    //             return resolve(document.querySelector(selector));
    //         }
    //
    //         const observer = new MutationObserver(() => {
    //             if (document.querySelector(selector)) {
    //                 resolve(document.querySelector(selector));
    //                 observer.disconnect();
    //             }
    //         });
    //
    //         observer.observe(document.body, {
    //             subtree: true,
    //             childList: true,
    //         });
    //     });
    // }
    // waitForElementToExist( `#${ uid }` ).then(element => {
    //     console.log('The element exists', element);
    //     Dialogs.resizeDialog( agentConfirm, $( `#${ uid }`).parent(), optionalHeightAdd ) ;
    // });
}

Dialogs.showJsonError = function ( summary, json ) {

    var partToShow = json ;
    if ( json.responseJSON ) {
        partToShow = json.responseJSON ;
    }

    let detailText =  JSON.stringify( partToShow, null, "\t" ) ;

    let message = "Failed to send request to server.";
    message += `<label class='csap-form'><span>Summary:</span><div class="noteAlt">${ summary }</div></label><br><br>`
    message += `<label class='csap-form'><span>Details:</span><div class="noteAlt">${ detailText }</div></label><br><br>`

    Dialogs.buildErrorDialog( message ) ;

}

Dialogs.showTextError = function ( summary , detailText ) {

    let message = "Failed to send request to server.";
    message += `<label class='csap-form'><span>Summary:</span><div class="noteAlt">${ summary }</div></label><br><br>`
    message += `<label class='csap-form'><span>Details:</span><div class="noteAlt">${ detailText }</div></label><br><br>`

    Dialogs.buildErrorDialog( message ) ;

}

Dialogs.buildErrorDialog = function ( messageHtml ) {

    console.warn(`buildErrorDialog: ${ messageHtml } `) ;
    $( 'body' ).css( 'cursor', 'default' );

    // if ( errorThrown == "abort" ) {
    //     console.log( "Request was aborted: " + command );
    //     return;
    // }


    let $messagContainer = jQuery( '<div/>', {} );
    jQuery( '<div/>', {
        class: `warning`,
        html: messageHtml
    } )
        .css( "margin-top", "2em" )
        .css( "font-weight", "bold" )
        .appendTo( $messagContainer );

    if(!alertify.connAlertLegGlob){
        //define a new dialog
        alertify.dialog('connAlertLegGlob',function factory(){
            return{
                main:function(message){
                    this.message = message;
                },
                setup:function(){
                    return {
                        options: {
                            title: "Connection Error: Reload browser?",
                            resizable: false
                        },
                        buttons:[
                            { action: "reload", text: "Reload Browser", className: alertify.defaults.theme.ok},
                            { action: "ignore", invokeOnClose: true, text: "Ignore Error And Continue" }
                        ],
                        focus: { element:0 }
                    };
                },
                prepare:function(){
                    this.setContent(this.message);
                },
                callback:function(closeEvent) {
                    console.log( `closed: `, closeEvent ) ;
                    if ( closeEvent.button.action == "reload" ) {
                        document.location.reload( true );
                    } else {
                        console.log( `Ignoring` ) ;
                    }
                }
            }});
    }
    //launch it.
    let theAlertifyDialog = alertify.connAlertLegGlob( $messagContainer.html() );


};

Dialogs.resizeDialog = function ( theAlertifyDialog, $displaySection, optionalHeight = 0 ) {

    if ( theAlertifyDialog == null ) {
        console.log( "resizeDialog() theAlertifyDialog is null- skipping" );
        return;
    }
    let targetWidth = $( window ).outerWidth( true ) - 100;
    let targetHeight = $( window ).outerHeight( true ) - 100;

    let customHeight = targetHeight / 2;

    if ( $displaySection ) {
        customHeight = Math.round( $displaySection[ 0 ].scrollHeight ) + 120;
    }

    customHeight += optionalHeight ;
    //let customHeight = Math.round( $warningsList.height()  ) ;

    console.log( "customHeight: ", customHeight )

    if ( customHeight > targetHeight )
        customHeight = targetHeight;

    theAlertifyDialog.resizeTo( targetWidth, customHeight )
}

/**
 * 
 *  create alertify templates
 * 
 * @see https://github.com/csap-platform/csap-core/wiki/Release-Notes
 * 
 */
function configureCsapAlertify() {

    _dom.logHead( `Configuring themes, csapInfo, and csapWarning` );

    // http://alertifyjs.com/
    alertify.defaults.glossary.title = "CSAP"
    alertify.defaults.theme.ok = "ui positive mini button";
    alertify.defaults.theme.cancel = "ui black mini button";
    alertify.defaults.notifier.position = "top-left";
    alertify.defaults.closableByDimmer = false;
    // alertify.defaults.theme.ok = "pushButton";
    // alertify.defaults.theme.cancel = "btn btn-danger";

    alertify.csapWarning = function ( message, head ) {
        let $warning = jQuery( '<div/>', {} );

        if ( head ) {
            let $errorHeader = jQuery( '<div/>', { text: head } ).appendTo( $warning );
            $errorHeader
                .css( "font-size", "14pt" )
                .css( "font-weight", "bold" )
                .css( "text-align", "left" )
                .css( "margin-left", "7px" );
        }
        $warning.append( jQuery( '<div/>', {
            class: "warning",
            html: message
        } ) );


        $warning.append( jQuery( '<button/>', {
            class: "csap-button close-csap",
            html: "Close"
        } ) );


        return alertify.error( $warning.html(), 0 );
    }


    alertify.csapHtml = function ( message, onCloseFunction=null, title="Markdown Preview" ) {
        let $info = jQuery( '<div/>', {} );


        let $messagePanel = jQuery( '<div/>', {
            id: "csap-preview-panel",
            class: "csap-markdown line-numbers",
            html: message
        } );

        $messagePanel.css("max-height", Math.round( $( window ).outerHeight( true ) ) - 200) ;
        
        setTimeout( function() {
            let $alertifyParent = $("#csap-preview-panel", "div.alertify-notifier").parent() ;
            $alertifyParent
                .css("text-align", "left")
                .css("background-color",  "#6e89bb")
                .css("width", Math.round( $( window ).outerWidth( true ) ) - 200) ;
        }, 200);

        let canDismiss = false;

        let $header = jQuery( '<div/>', {
            class: "flex-container"
        } ).css("position", "relative")
            .css("top", "-6px")
            .css("color", "white")
            .css( "font-weight", "bold") ;
        $info.append(  $header );

        let $closeButton = jQuery( '<button/>', {
            class: "csap-icon csap-remove csap-preview-dismiss-button",
            text: title,
        } ) ;
        $header.append( $closeButton );
        $header.append( jQuery( '<label/>', {
            class: ""
        } ) ) ;

        $info.append( $messagePanel );

        if ( onCloseFunction == null ) {
            onCloseFunction = function () {
                console.log( 'dismissed' );
            } 
        }

        let $theAlert = alertify.notify( $info.html(), "info", 0, onCloseFunction);

        $theAlert.ondismiss = function () {
            return canDismiss;
        };

        setTimeout( () => {
            $( ".csap-preview-dismiss-button" ).click( function () {
                canDismiss = true;
                $theAlert.dismiss();
            } );
        }, 500 );
        // let $theAlert = alertify.alert( $info.html());
        // $theAlert.set('frameless', true);
        return $theAlert;
    }

    alertify.csapInfo = function ( message, wrapText, head, onCloseFunction=null ) {
        let $info = jQuery( '<div/>', {} );

        if ( head ) {
            let $errorHeader = jQuery( '<div/>', { text: head } ).appendTo( $info );
            $errorHeader
                .css( "font-size", "14pt" )
                .css( "font-weight", "bold" )
                .css( "text-align", "left" )
                .css( "margin-left", "7px" );
        }

        let $messagePanel = jQuery( '<div/>', {
            class: "code",
            html: message
        } );

        if ( wrapText ) {
            $messagePanel.css( "white-space", "pre-wrap" );
            $messagePanel.css( "color", "blue" );
        }
        $info.append( $messagePanel );

        let canDismiss = false;

        $info.append( jQuery( '<button/>', {
            class: "csap-button csap-code-dismiss-button",
            html: "Close"
        } ) );

        if ( onCloseFunction == null ) {
            onCloseFunction = function () {
                console.log( 'dismissed' );
            } 
        }

        let $theAlert = alertify.notify( $info.html(), "info", 0, onCloseFunction);

        $theAlert.ondismiss = function () {
            return canDismiss;
        };

        setTimeout( () => {
            $( ".csap-code-dismiss-button" ).click( function () {
                canDismiss = true;
                $theAlert.dismiss();
            } );
        }, 500 );
        // let $theAlert = alertify.alert( $info.html());
        // $theAlert.set('frameless', true);
        return $theAlert;
    }

}

Dialogs.openWindowSafely = function ( url, windowFrameName ) {

    // console.log("window frame name: " + getValidWinName( windowFrameName)
    // + "
    // url: " + encodeURI(url)
    // + " encodeURIComponent:" + encodeURIComponent(url)) ;

    window.open( encodeURI( url ), Dialogs.getValidWinName( windowFrameName ) );

}

Dialogs.getValidWinName = function ( inputName ) {
    let regex = new RegExp( "-", "g" );
    let validWindowName = inputName.replace( regex, "" );

    regex = new RegExp( " ", "g" );
    validWindowName = validWindowName.replace( regex, "" );

    return validWindowName;
}