package org.csap.agent;

import java.util.List ;
import java.util.stream.Collectors ;

import org.apache.commons.dbcp2.BasicDataSource ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.jdbc.core.JdbcTemplate ;
import org.springframework.jdbc.core.RowMapper ;

public class DbPerformanceCollector {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	String url ;
	String driver ;
	String username ;
	String password ;
	String query ;
	
	int maxConnections ;
	
	JdbcTemplate _myTemplate ;
	
	
	
	public StringBuilder collectPerformance() {
		
		StringBuilder reportBuilder = new StringBuilder() ;
		
		try {
			
			RowMapper<String> rowMapper = //
					( rs , rowNum ) -> {

						//ObjectNode databaseStats = jsonMapper.createObjectNode( ) ;
						
						var perfName = rs.getString( "variable_name" ) ;
						String perfRow = "# skipped " + perfName ;

						try {

							perfRow =  perfName + " "+rs.getLong( "value" )  ;

						} catch ( Exception e ) {
								
							logger.debug( "Failed item: {}", CSAP.buildCsapStack( e ) );

						}
						
						reportBuilder.append( "\n" + perfRow ) ;


						return "" ;

					} ;

			List<String> dbPerformanceRows = getJdbcTemplate().query( 
					getQuery( ),
					rowMapper ) ;
			
			//logger.info( "PerfData: {}",  dbPerformanceRows.stream( ).collect( Collectors.joining( "\n" ) )  );
			
			
		} catch (Exception e ) {
			logger.info( "Failed to collect db data: {}", CSAP.buildCsapStack( e ));
		}
		
		return reportBuilder ;
	}
	
	
	JdbcTemplate getJdbcTemplate() {
		
		if ( _myTemplate == null ) {
			_myTemplate = new JdbcTemplate( mysqlDatasource ( ) ) ;
		}
		
		return _myTemplate ;
		
		
	}
	
	

	private BasicDataSource mysqlDatasource ( ) {
		

		logger.info( CsapApplication.arrowMessage( "Creating datasource" ) ) ;

		logger.info( toString() ) ;

		BasicDataSource dataSource = new BasicDataSource( ) ;
		dataSource.setDriverClassName(  getDriver( ) ) ;
		dataSource.setUrl( getUrl( ) ) ;
		dataSource.setUsername(  getUsername( )) ;

		dataSource.setPassword( getPassword( ) ) ;

		// helloDataSource.setMaxWait(500);
		dataSource.setMaxWaitMillis( 500 ) ;
		dataSource.setMaxTotal(  getMaxConnections( ) ) ;
		return dataSource ;
	}
	
	
	
	
	public String getUrl ( ) {
	
		return url ;
	
	}
	public void setUrl ( String url ) {
	
		this.url = url ;
	
	}
	public String getDriver ( ) {
	
		return driver ;
	
	}
	public void setDriver ( String driver ) {
	
		this.driver = driver ;
	
	}
	public String getUsername ( ) {
	
		return username ;
	
	}
	public void setUsername ( String username ) {
	
		this.username = username ;
	
	}
	public String getPassword ( ) {
	
		return password ;
	
	}
	public void setPassword ( String password ) {
	
		this.password = password ;
	
	}
	public String getQuery ( ) {
	
		return query ;
	
	}
	public void setQuery ( String query ) {
	
		this.query = query ;
	
	}
	public int getMaxConnections ( ) {
	
		return maxConnections ;
	
	}
	public void setMaxConnections ( int maxConnections ) {
	
		this.maxConnections = maxConnections ;
	
	}
	
	
	
	
	
	@Override
	public String toString ( ) {

		var report = CSAP.buildDescription( this.getClass().getSimpleName()+ " Settings",
				"Note", "Typically dbUser and dbPass is set via install-csap-user.sh",
				"url", url,
				"driver", driver,
				"username", username,
				"password", password,
				"maxConnections", maxConnections,
				"query", query) ;

		return report ;

	}

	
}
