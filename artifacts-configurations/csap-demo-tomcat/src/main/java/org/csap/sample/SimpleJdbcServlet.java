package org.csap.sample;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet ( name = "JdbServlet" , urlPatterns = { "/showJdbcServlet" } )
public class SimpleJdbcServlet extends HttpServlet {

	public final static String RESULTS = "results";
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet (	HttpServletRequest request,
							HttpServletResponse response )
			throws ServletException, IOException {

		String[] connection=request.getParameter( "connection" ).split( "," ) ;
		String user = connection[0];
		String pass = connection[1];
		String testDriver =  connection[2];
		String testLabConnection = connection[3];
		String testQuery = connection[4];
		// testQuery = "select count(*) from job_schedule" ; csap test schema

		StringBuilder resultsBuff = new StringBuilder( "\n\nTesting connection: " );
		Connection jdbcConnection = null;
		ResultSet rs = null;
		try {

			System.out.println(
				new Date() + "\t Testing jdbc using  Driver: " + testDriver
				+ "\n\t connection: " + testLabConnection
				+ "\n\t testQuery: " + testQuery );

			Class.forName( testDriver );
			jdbcConnection = DriverManager.getConnection(
				testLabConnection,
				user, pass );

			rs = jdbcConnection.createStatement().executeQuery( testQuery );
			while (rs.next()) {
				resultsBuff.append( rs.getString( 1 ) );
			}
			resultsBuff.insert( 0, "* Connection Success: " + testLabConnection + "\n" ) ;

		} catch (Exception e) {
			resultsBuff.append( getFilteredStackTrace( e, "." ) ) ;
			e.printStackTrace();
		} finally {
			try {
				rs.close();
				jdbcConnection.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		request.setAttribute( RESULTS, resultsBuff );
		// response.getWriter().println(resultsBuff);
		request.getRequestDispatcher( "/index.jsp" ).forward( request, response );

	}
	
	public static String getFilteredStackTrace ( Throwable possibleNestedThrowable, String pattern ) {
		// add the class name and any message passed to constructor
		final StringBuffer result = new StringBuffer();
	
		Throwable currentThrowable = possibleNestedThrowable;
	
		int nestedCount = 1;
		while (currentThrowable != null) {
	
			if ( nestedCount == 1 ) {
				result.append( "\n========== Exception, Filter:  " + pattern );
			} else {
				result.append( "\n========== Nested Count: " );
				result.append( nestedCount );
				result.append( " ===============================" );
			}
			result.append( "\n\n Exception: " + currentThrowable
				.getClass()
				.getName() );
			result.append( "\n Message: " + currentThrowable.getMessage() );
			result.append( "\n\n StackTrace: \n" );
	
			// add each element of the stack trace
			List<StackTraceElement> traceElements = Arrays.asList( currentThrowable.getStackTrace() );
	
			Iterator<StackTraceElement> traceIt = traceElements.iterator();
			while (traceIt.hasNext()) {
				StackTraceElement element = traceIt.next();
				String stackDesc = element.toString();
				if ( pattern == null || stackDesc.contains( pattern ) ) {
					result.append( stackDesc );
					result.append( "\n" );
				}
			}
			result.append( "\n========================================================" );
			currentThrowable = currentThrowable.getCause();
			nestedCount++;
		}
		return result.toString();
	}
}
