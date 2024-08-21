package org.csap.sample;


import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@WebServlet(name = "securedServlet", urlPatterns = { "/securedServlet" })
public class SecuredServlet extends HttpServlet {


	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		
		
		StringBuffer resultsBuff = new StringBuffer("\n Hello from " + this.getClass().getName() );
		
		System.out.println( new Date() + "\tDemo only - standard out should never be used, configure logging framework");
		
		
		request.setAttribute(SimpleJdbcServlet.RESULTS, resultsBuff) ;
		// response.getWriter().println(resultsBuff);
		request.getRequestDispatcher("/index.jsp").forward(request, response) ;
	}
}
