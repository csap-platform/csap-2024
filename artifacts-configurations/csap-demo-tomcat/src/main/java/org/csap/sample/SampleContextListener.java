package org.csap.sample;

import java.util.Date;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Application Lifecycle Listener implementation class SampleContextListener
 *
 */
@WebListener
public class SampleContextListener implements ServletContextListener {

    /**
     * Default constructor. 
     */
    public SampleContextListener() {
        // TODO Auto-generated constructor stub
    }

	/**
     * @see ServletContextListener#contextInitialized(ServletContextEvent)
     */
    public void contextInitialized(ServletContextEvent sce) {
        // TODO Auto-generated method stub

		System.out.println("\n\n" 
		+  new Date() 
		+ "\t =================== CSAP DEMO PROJECT ==================="
		+ "\n\t\t  Simple sample 3\n\n");
    }

	/**
     * @see ServletContextListener#contextDestroyed(ServletContextEvent)
     */
    public void contextDestroyed(ServletContextEvent sce) {
        // TODO Auto-generated method stub
 
		System.out.println( "\n\n" + new Date() + "\tDemo only - ServleContext destroyed\n\n");
    }
	
}
