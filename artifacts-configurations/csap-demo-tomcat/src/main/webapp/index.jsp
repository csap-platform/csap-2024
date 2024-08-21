<!DOCTYPE html>
<%@page import="org.csap.sample.SimpleJdbcServlet"%>
<html>
<meta http-equiv="Cache-Control" content="no-cache" />
<head>
<title>Servlet 3 Sample</title>
<link rel="stylesheet" href="styles/nextGen.css" type="text/css"
	media="screen">
<script>
	
</script>

<style>
</style>
</head>
<body>


	<header>Servlet 3 Sample page d</header>


	<article>
		<div class="title">Simple Servlet</div>
		<form action="showHelloServlet">
			Demo request param <input name="message" value="test" /> <input
				type="submit" value="Hit Hello Servlet">
		</form>
	</article>

	<article>
		<div class="title">Secured Servlet</div>
		<form action="securedServlet">
			Demo with Basic Auth. User for testing is: sampleUser , samplePass. Note CSAP agent must be used to auto copy the access file, or manually copy
			<br>param <input name="message" value="test" /> <input
				type="submit" value="Hit Secured Servlet">
		</form>
	</article>
	
	<article>
		<div class="title">DB test</div>
		<form action="showJdbcServlet">
			Connection: comma separated user,pass,driver,connection,query 
			<br/>
			<textarea name="connection" style="width: 80%; height: 2em">csap_test,csap_test,oracle.jdbc.driver.OracleDriver,jdbc:oracle:thin:@csapdb-dev01:1521:dev01,SELECT 1 FROM DUAL
			</textarea>
			<br/>
			<input type="submit" value="Test Connection">
		</form>
	</article>
	<%
		if (request.getAttribute(SimpleJdbcServlet.RESULTS) != null) {
	%>
	<article>
		<div class="title">Results</div>
		<pre>
			<%=request.getAttribute(SimpleJdbcServlet.RESULTS)%>
		</pre>
	</article>
	<%
		}
	%>

</body>