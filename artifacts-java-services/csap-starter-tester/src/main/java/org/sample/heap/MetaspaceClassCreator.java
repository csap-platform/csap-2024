package org.sample.heap;

import java.util.concurrent.TimeUnit;

public class MetaspaceClassCreator {
    private int number;
    private String className;
    private StringBuilder classNameBuilder = new StringBuilder( );
    private int maxPerClassLoader = 100;  // one class loader will load max up to 100 classes
    private MetaspaceLeakerClassLoader loader = new MetaspaceLeakerClassLoader( );

    public MetaspaceClassCreator( ) {
        this( MetaspaceLeakerClassLoader.DEFAULT_CLASSNAME );
    }

    public MetaspaceClassCreator( String className ) {
        this.className = className;
    }

    // Generate new class name
    String getNewClassName( ) {

        //
        // Meta allocation failures happen here. catch and hard exit
        //
        try {
            classNameBuilder.delete( 0, classNameBuilder.length( ) );
            classNameBuilder.append( "Class" );
            classNameBuilder.append( number );
            int n = loader.getNameLength( ) - classNameBuilder.length( );
            for ( int i = 0 ; i < n ; ++i ) {
                classNameBuilder.append( '#' );
            }

        } catch ( Exception e ) {

            System.out.println( "\n***\n***\n  NULL StringBuilder  -- assuming memory allocation failed and exiting \n***\n***\n" );

            try {
                TimeUnit.MILLISECONDS.sleep( 500 );
            } catch ( Exception e2 ) {
                System.out.println( "Failed Sleeping - will be a fast exit" );
            }

            System.exit( 999 );
        }

        return classNameBuilder.toString( );
    }

    // Load a new class with TemplateClassLoader

    // Load a new class with TemplateClassLoader
    public synchronized  Class createClass( ) {
        try {
            // Create a new TemplateClassLoader if the number of classes loaded
            // by the current classloader is more then maxPerClassLoader
            if ( number++ > maxPerClassLoader || loader == null ) {
                loader = new MetaspaceLeakerClassLoader( className );
                number = 0;
            }
            return loader.loadClass( getNewClassName( ) );
        } catch ( Exception e ) {
            System.out.println( "\n***\n***\n  createClass  -- assuming memory allocation failed and exiting \n***\n***\n" );
        }
        System.exit( 998 );
        return null ;
    }
}
