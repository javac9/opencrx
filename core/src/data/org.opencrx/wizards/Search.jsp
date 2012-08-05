<%@  page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8" %><%
/*
 * ====================================================================
 * Project:     openCRX/Core, http://www.opencrx.org/
 * Name:        $Id: Search.jsp,v 1.4 2007/12/16 02:23:54 wfro Exp $
 * Description: Search.jsp
 * Revision:    $Revision: 1.4 $
 * Owner:       CRIXP AG, Switzerland, http://www.crixp.com
 * Date:        $Date: 2007/12/16 02:23:54 $
 * ====================================================================
 *
 * This software is published under the BSD license
 * as listed below.
 *
 * Copyright (c) 2007, CRIXP Corp., Switzerland
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * * Neither the name of CRIXP Corp. nor the names of the contributors
 * to openCRX may be used to endorse or promote products derived
 * from this software without specific prior written permission
 *
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * ------------------
 *
 * This product includes software developed by the Apache Software
 * Foundation (http://www.apache.org/).
 *
 * This product includes software developed by contributors to
 * openMDX (http://www.openmdx.org/)
 */
%><%@ page session="true" import="
java.util.*,
java.io.*,
java.text.*,
org.openmdx.compatibility.base.dataprovider.cci.Orders,
org.openmdx.compatibility.base.dataprovider.cci.SystemAttributes,
org.openmdx.compatibility.base.naming.Path,
org.openmdx.compatibility.base.query.FilterOperators,
org.openmdx.base.accessor.jmi.cci.*,
org.openmdx.base.exception.*,
org.openmdx.portal.servlet.*,
org.openmdx.portal.servlet.attribute.*,
org.openmdx.portal.servlet.view.*,
org.openmdx.portal.servlet.texts.*,
org.openmdx.portal.servlet.control.*,
org.openmdx.portal.servlet.reports.*,
org.openmdx.portal.servlet.wizards.*,
org.openmdx.compatibility.base.naming.*,
org.openmdx.compatibility.base.dataprovider.cci.*,
org.openmdx.application.log.*
" %><%
  request.setCharacterEncoding("UTF-8");
  ApplicationContext app = (ApplicationContext)session.getValue("ObjectInspectorServlet.ApplicationContext");
  ShowObjectView view = (ShowObjectView)session.getValue("ObjectInspectorServlet.View");
  Texts_1_0 texts = app.getTexts();
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html dir="<%= texts.getDir() %>">
<head>
  <title><%= app.getApplicationName() + " - Search" %></title>
  <meta name="label" content="Search">
  <meta name="toolTip" content="Search">
  <meta name="targetType" content="_blank">
  <meta name="order" content="9999">
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
  <link href="../../_style/colors.css" rel="stylesheet" type="text/css">
  <link href="../../_style/n2default.css" rel="stylesheet" type="text/css">
  <link rel='shortcut icon' href='../../images/favicon.ico' />
</head>
<body>
  <table><tr><td>
<%  
	  try {
		String searchExpression = request.getParameter("searchExpression");
		Action nextAction = view.getObjectReference().getSelectObjectAction();
		RefPackage_1_1 dataPkg = app.getDataPackage();
		javax.jdo.PersistenceManager pm = dataPkg.refPersistenceManager();
		org.opencrx.kernel.home1.jmi1.UserHome userHome = 
			(org.opencrx.kernel.home1.jmi1.UserHome)dataPkg.refObject(app.getUserHomeIdentity().toXri());
		org.opencrx.kernel.home1.jmi1.SearchResult searchResult = null;
		try {
			pm.currentTransaction().begin();
			searchResult = userHome.searchBasic(searchExpression);
			pm.currentTransaction().commit();
		}
		catch(Exception e) {
			System.out.println(e.getCause());
			try {
				pm.currentTransaction().rollback();
			} catch(Exception e0) {}
		}
		if((searchResult != null) && (searchResult.getObjectFinder() != null)) {
			org.opencrx.kernel.home1.jmi1.ObjectFinder objectFinder = 
				(org.opencrx.kernel.home1.jmi1.ObjectFinder)dataPkg.refObject(searchResult.getObjectFinder().refMofId());
			nextAction = new ObjectReference(objectFinder, app).getSelectObjectAction();
		}
		response.sendRedirect(
			request.getContextPath() + "/" + view.getEncodedHRef(nextAction)
		);
	  }
	  catch (Exception e) {
		out.println("<p><b>!! Failed !!<br><br>The following exception(s) occured:</b><br><br><pre>");
		PrintWriter pw = new PrintWriter(out);
		ServiceException e0 = new ServiceException(e);
		pw.println(e0.getMessage());
		pw.println(e0.getCause());
		out.println("</pre>");
		AppLog.warning("Error calling wizard", "Search.jsp");
		AppLog.warning(e0.getMessage(), e0.getCause(), 1);
	  }
%>
  </td></tr></table>
</body>
</html>
