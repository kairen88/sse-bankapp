<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ page session="true" %>

<!DOCTYPE html>
<html lang="en">
  <%@include file="pageHeader.jsp"%>
  <body>
	<%@include file="header.jsp"%>
	
	<main id="content" class="mainContent sutd-template" role="main">
	<div class="container">
		<%@include file="errorMessage.jsp"%>
		
		<div id="createBatchTransaction">
			<form id="batchTransactionForm" action="batchTransaction" method="post" enctype = "multipart/form-data">
				<input type = "file" name = "file" size = "50" id="file"/>
				</br>
				<button id="createBatchTransBtn" type="submit" class="btn btn-default">Upload File</button>
				<input type="hidden" name="actionType" value="batchTransactionAction">
				<input type="hidden" name="formValidationId" value="<%= request.getAttribute("formValidationId") %>">
			</form>
		</div>
		
	</div>
	</main>
  </body>
</html>
