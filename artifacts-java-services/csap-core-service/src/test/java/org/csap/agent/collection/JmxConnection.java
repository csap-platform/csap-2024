package org.csap.agent.collection;

import javax.management.MBeanServerConnection ;
import javax.management.ObjectName ;
import javax.management.openmbean.CompositeData ;

import org.csap.agent.CsapThinNoProfile ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.Disabled ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.jmx.support.MBeanServerConnectionFactoryBean ;

@Disabled

@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class JmxConnection {

	static {

		// includes logging initialization - so must occur very early
		CsapApplication.initialize( CSAP.padNoLine( "Loading" ) + CsapThinNoProfile.class.getName( ) ) ;

	}

	public Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	/**
	 * @param args
	 */

	@Test
	public void jmx_api_connect_with_spaces ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var opHost = "localhost" ;
		// opHost = "csseredapp-dev-03";
		var opPort = "12345" ;
		// opPort = "8356";

		// String serviceUrl = "service:jmx:rmi://" + opHost
		// + "/jndi/rmi://" + opHost + ":" + opPort
		// + "/jmxrmi";
		var serviceUrl = "service:jmx:rmi:///jndi/rmi://" + opHost + ":" + opPort
				+ "/jmxrmi" ;

		try {

			MBeanServerConnectionFactoryBean jmxFactory = null ;
			// String mbeanName = "org.jfrog.artifactory:instance=Artifactory,
			// type=Repositories,prop=example-repo-local" ;
			// String attributeName = "ArtifactsTotalSize" ;
			
//			var mbeanName = "org.jfrog.artifactory:instance=Artifactory, type=Storage,prop=Binary Storage" ;
//			var attributeName = "Size" ;

			var mbeanName = "java.lang:type=Memory" ;
			var attributeName = "HeapMemoryUsage" ;

//			var mbeanName = "java.lang:type=Threading" ;
//			var attributeName = "ThreadCount" ;
			
			jmxFactory = new MBeanServerConnectionFactoryBean( ) ;
			jmxFactory.setServiceUrl( serviceUrl ) ;

			logger.info( "attempting conenction: {}", serviceUrl );
			jmxFactory.afterPropertiesSet( ) ;

			long start = System.currentTimeMillis( ) ;
			
			logger.info( "attempting conenction: {}", serviceUrl );
			MBeanServerConnection mbeanConn = jmxFactory.getObject( ) ;

//			Long size = (Long) mbeanConn.getAttribute( new ObjectName( mbeanName ), attributeName ) ;

			CompositeData resultData = (CompositeData) mbeanConn
					.getAttribute( new ObjectName( mbeanName ),
							attributeName ) ;
			//
			 int heapUsed = (int) Long
			 .parseLong( resultData.get( "used" )
			 .toString() ) / 1024 / 1024 ;
			//
			int heapMax = (int) Long.parseLong( resultData.get( "max" )
					.toString( ) ) / 1024 / 1024 ;
			//
			 System.out.println( "heapUsed" + heapUsed ) ;
//			System.out.println( "size: " + size ) ;
			System.out.println( opHost + " === Time Taken (ms): " + ( System.currentTimeMillis( ) - start ) ) ;

			// }
		} catch ( Exception e ) {

			System.out.println( "Failed to collect" + e ) ;

		}

	}

	@Test
	public void jmx_api_connect ( ) {

		String opHost = "csap-dev03.csap.org" ;
		String opPort = "8266" ; // 8016
									// =
									// CsAgent,
									// 8266
									// =
									// docker
									// test
									// service

		// String serviceUrl = "service:jmx:rmi://" + opHost
		// + "/jndi/rmi://" + opHost + ":" + opPort
		// + "/jmxrmi";
		String longUrl = "service:jmx:rmi://" + opHost + ":" + opPort + "/jndi/rmi://" + opHost + ":" + opPort
				+ "/jmxrmi" ;
		String shortUrl = "service:jmx:rmi:///jndi/rmi://" + opHost + ":" + opPort + "/jmxrmi" ;

		try {

			MBeanServerConnectionFactoryBean jmxFactory = null ;
			String mbeanName = "java.lang:type=Memory" ;
			String attributeName = "HeapMemoryUsage" ;
			jmxFactory = new MBeanServerConnectionFactoryBean( ) ;
			jmxFactory.setServiceUrl( shortUrl ) ;

			System.out.println( "Connecting to: " + shortUrl ) ;
			jmxFactory.afterPropertiesSet( ) ;

			long start = System.currentTimeMillis( ) ;
			MBeanServerConnection mbeanConn = jmxFactory.getObject( ) ;

			CompositeData resultData = (CompositeData) mbeanConn
					.getAttribute( new ObjectName( mbeanName ),
							attributeName ) ;

			int heapUsed = (int) Long
					.parseLong( resultData.get( "used" )
							.toString( ) ) / 1024 / 1024 ;

			int heapMax = (int) Long.parseLong( resultData.get( "max" )
					.toString( ) ) / 1024 / 1024 ;

			System.out.println( "heapUsed" + heapUsed ) ;
			System.out.println( "heapMax" + heapMax ) ;
			System.out.println( opHost + " === Time Taken (ms): " + ( System.currentTimeMillis( ) - start ) ) ;

			// Tomcat only
			// String connName = "http-nio-8021" ;
			// // connName = "http*" ;
			// mbeanName = "Catalina:type=ThreadPool,name=\""
			// + connName + "\"";
			// attributeName = "connectionCount";
			// Long conns = (Long) mbeanConn.getAttribute(
			// new ObjectName(mbeanName),
			// attributeName);
			//
			// System.out.println("conns" + conns) ;
			//
			//
			// // connName = "http*" ;
			// mbeanName = "Catalina:type=ThreadPool,name=\"*\"";
			// attributeName = "connectionCount";
			// Set<ObjectInstance> matchingBeans = mbeanConn.queryMBeans(
			// new ObjectName(mbeanName),
			// null);
			//
			// conns = 0l;
			// for (ObjectInstance objectInstance : matchingBeans) {
			// System.out.println("objectInstance: " +
			// objectInstance.getObjectName()) ;
			// conns += (Long) mbeanConn.getAttribute(
			// objectInstance.getObjectName(),
			// attributeName);
			// }
			// System.out.println("conns" + conns) ;
			// mbeanName = "Catalina:type=ThreadPool,name=\"p*\"";
			// Set<ObjectName> queryNames(ObjectName name, QueryExp query) ;
			// for (ObjectInstance objectInstance : matchingBeans) {
			// System.out.println("objectInstance: " +
			// objectInstance.getObjectName()) ;
			// }
		} catch ( Exception e ) {

			System.out.println( "Failed to collect" + e ) ;

		}

	}

}
