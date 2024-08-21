package org.csap.agent;

import org.csap.helpers.CSAP;

public class CustomProperty {

    String name;
    String value;
    String file;
    String key;

    public String getName( ) {

        return CsapConstants.CSAP_VARIABLE_PREFIX + name;

    }

    public String getRawName( ) {

        return name;

    }

    public void setName( String name ) {

        this.name = name;

    }

    public String getValue( ) {

        return value;

    }

    public void setValue( String key ) {

        this.value = key;

    }

    public String getFile( ) {

        return file;

    }

    public void setFile( String file ) {

        this.file = file;

    }

    public String getKey( ) {

        return key;

    }

    public void setKey( String key ) {

        this.key = key;

    }

    @Override
    public String toString( ) {

        return CSAP.buildDescription( "\t  " + getName( ),
                "\t value", getValue( ),
                "\t key", getKey( ),
                "\t file", getFile( ) );

    }

}
