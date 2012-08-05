﻿<%@  page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8" %><%
/*
 * ====================================================================
 * Project:     openCRX/Core, http://www.opencrx.org/
 * Name:        $Id: CreateAgendaWizard.jsp,v 1.11 2009/03/25 10:14:50 wfro Exp $
 * Description: CreateAgendaWizard
 * Revision:    $Revision: 1.11 $
 * Owner:       CRIXP AG, Switzerland, http://www.crixp.com
 * Date:        $Date: 2009/03/25 10:14:50 $
 * ====================================================================
 *
 * This software is published under the BSD license
 * as listed below.
 *
 * Copyright (c) 2005-2009, CRIXP Corp., Switzerland
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
org.opencrx.kernel.backend.Activities,
org.openmdx.kernel.id.cci.*,
org.openmdx.kernel.id.*,
org.openmdx.base.accessor.jmi.cci.*,
org.openmdx.portal.servlet.*,
org.openmdx.portal.servlet.attribute.*,
org.openmdx.portal.servlet.view.*,
org.openmdx.portal.servlet.texts.*,
org.openmdx.portal.servlet.control.*,
org.openmdx.portal.servlet.reports.*,
org.openmdx.portal.servlet.wizards.*,
org.openmdx.base.naming.*,
org.openmdx.application.log.*
" %><%
	request.setCharacterEncoding("UTF-8");
	String servletPath = "." + request.getServletPath();
	String servletPathPrefix = servletPath.substring(0, servletPath.lastIndexOf("/") + 1);
	ApplicationContext app = (ApplicationContext)session.getValue(WebKeys.APPLICATION_KEY);
	ViewsCache viewsCache = (ViewsCache)session.getValue(WebKeys.VIEW_CACHE_KEY_SHOW);
	String requestId =  request.getParameter(Action.PARAMETER_REQUEST_ID);
	String objectXri = request.getParameter(Action.PARAMETER_OBJECTXRI);
	if(objectXri == null || app == null || viewsCache.getViews().isEmpty()) {
		response.sendRedirect(
			request.getContextPath() + "/" + WebKeys.SERVLET_NAME
		);
		return;
	}
	javax.jdo.PersistenceManager pm = app.getPmData();
	RefObject_1_0 obj = (RefObject_1_0)pm.getObjectById(new Path(objectXri));
	Texts_1_0 texts = app.getTexts();
	Codes codes = app.getCodes();
	UUIDGenerator uuids = UUIDs.getGenerator();
	String formName = "CreateActivityTrackerForm";
	String wizardName = "CreateAgendaWizard.jsp";

	// Get Parameters
	String command = request.getParameter("Command");
	if(command == null) command = "";				
	boolean actionCreate = "OK".equals(command);
	boolean actionCancel = "Cancel".equals(command);

	if(actionCancel) {
	  session.setAttribute(wizardName, null);
		Action nextAction = new ObjectReference(obj, app).getSelectObjectAction();
		response.sendRedirect(
			request.getContextPath() + "/" + nextAction.getEncodedHRef()
		);
		return;
	}
%>
<!--
	<meta name="UNUSEDlabel" content="Create Agenda">
	<meta name="UNUSEDtoolTip" content="Create Agenda">
	<meta name="targetType" content="_inplace">
	<meta name="forClass" content="org:opencrx:kernel:activity1:Segment">
	<meta name="order" content="org:opencrx:kernel:activity1:Segment:createAgenda">
-->	
<%
	String providerName = obj.refGetPath().get(2);
	String segmentName = obj.refGetPath().get(4);
	org.opencrx.kernel.activity1.jmi1.Segment activitySegment =
	    (org.opencrx.kernel.activity1.jmi1.Segment)pm.getObjectById(
	        new Path("xri:@openmdx:org.opencrx.kernel.activity1/provider/" + providerName + "/segment/" + segmentName)
		);
	org.openmdx.ui1.jmi1.FormDefinition formDefinition = app.getUiFormDefinition(formName);
	org.openmdx.portal.servlet.control.FormControl form = new org.openmdx.portal.servlet.control.FormControl(
		formDefinition.refGetPath().getBase(),
		app.getCurrentLocaleAsString(),
		app.getCurrentLocaleAsIndex(),
		app.getUiContext(),
		formDefinition
	);
	Map formValues = new HashMap();
	form.updateObject(
		request.getParameterMap(),
		formValues,
		app,
		pm
	);
	if(actionCreate) {
	    String name = (String)formValues.get("org:opencrx:kernel:activity1:ActivityGroup:name");
	    String description = (String)formValues.get("org:opencrx:kernel:activity1:ActivityGroup:description");
	    if(
	        (name != null) &&
	        (name.length() > 0)
	    ) {
			org.opencrx.security.realm1.jmi1.PrincipalGroup usersGroup =
				(org.opencrx.security.realm1.jmi1.PrincipalGroup)org.opencrx.kernel.backend.SecureObject.findPrincipal(
					"Users",
					org.opencrx.kernel.backend.SecureObject.getRealm(
						pm,
						providerName,
						segmentName
					),
					pm
				);
	        org.opencrx.kernel.activity1.jmi1.ActivityTracker activityTracker = Activities.initActivityTracker(
	            name,
	            Arrays.asList(usersGroup),
	            pm,
	            providerName,
	            segmentName
	        );
	    	// ActivityCreator Meeting
	    	org.opencrx.kernel.activity1.jmi1.ActivityCreator defaultCreator = Activities.initActivityCreator(
	    	    name + " - " + Activities.ACTIVITY_CREATOR_NAME_MEETINGS,
		    	Activities.initActivityType(
		    	    Activities.ACTIVITY_TYPE_NAME_MEETINGS,
		    	    Activities.ACTIVITY_CLASS_MEETING,
		    	    Activities.findActivityProcess(
		    	        Activities.ACTIVITY_PROCESS_NAME_BUG_AND_FEATURE_TRACKING,
		    	        activitySegment,
		    	        pm
		    	    ),
		    	    pm,
		    	    providerName,
		    	    segmentName
		    	),
	    	    (List)Arrays.asList(new Object[]{activityTracker}),
	    	    Arrays.asList(usersGroup),
	    	    pm,
	    	    providerName,
	    	    segmentName
	    	);
	    	// ActivityCreator SalesVisit
	    	Activities.initActivityCreator(
	    	    name + " - " + Activities.ACTIVITY_CREATOR_NAME_SALES_VISITS,
		    	Activities.initActivityType(
		    	    Activities.ACTIVITY_TYPE_NAME_SALES_VISITS,
		    	    Activities.ACTIVITY_CLASS_SALES_VISIT,
		    	    Activities.findActivityProcess(
		    	        Activities.ACTIVITY_PROCESS_NAME_BUG_AND_FEATURE_TRACKING,
		    	        activitySegment,
		    	        pm
		    	    ),
		    	    pm,
		    	    providerName,
		    	    segmentName
		    	),
	    	    (List)Arrays.asList(new Object[]{activityTracker}),
	    	    Arrays.asList(usersGroup),
	    	    pm,
	    	    providerName,
	    	    segmentName
	    	);
	    	// ActivityCreator Absence
	    	Activities.initActivityCreator(
	    	    name + " - " + Activities.ACTIVITY_CREATOR_NAME_ABSENCES,
		    	Activities.initActivityType(
		    	    Activities.ACTIVITY_TYPE_NAME_ABSENCES,
		    	    Activities.ACTIVITY_CLASS_ABSENCE,
		    	    Activities.findActivityProcess(
		    	        Activities.ACTIVITY_PROCESS_NAME_BUG_AND_FEATURE_TRACKING,
		    	        activitySegment,
		    	        pm
		    	    ),
		    	    pm,
		    	    providerName,
		    	    segmentName
		    	),
	    	    (List)Arrays.asList(new Object[]{activityTracker}),
	    	    Arrays.asList(usersGroup),
	    	    pm,
	    	    providerName,
	    	    segmentName
	    	);
	    	// Update tracker
	        pm.currentTransaction().begin();
	    	activityTracker.setDescription(description);
	    	activityTracker.setDefaultCreator(defaultCreator);
	    	pm.currentTransaction().commit();
	    	// Forward to tracker
		    Action nextAction = new ObjectReference(
		    	activityTracker,
		    	app
		   	).getSelectObjectAction();
			response.sendRedirect(
				request.getContextPath() + "/" + nextAction.getEncodedHRef()
			);
			return;
	    }
	}
	TransientObjectView view = new TransientObjectView(
		formValues,
		app,
		obj
	);
	HtmlPage p = HtmlPageFactory.openPage(
		view,
		request,
		out
	);
%>
<br />
<form id="<%= formName %>" name="<%= formName %>" accept-charset="UTF-8" method="POST" action="<%= servletPath %>">
	<input type="hidden" name="<%= Action.PARAMETER_REQUEST_ID %>" value="<%= requestId %>" />
	<input type="hidden" name="<%= Action.PARAMETER_OBJECTXRI %>" value="<%= objectXri %>" />
	<input type="hidden" id="Command" name="Command" value="" />			
	<table cellspacing="8" class="tableLayout">
		<tr>
			<td class="cellObject">
				<div class="panel" id="panel<%= formName %>" style="display: block">
<%
					form.paint(
						p,
						null, // frame
						true // forEditing
					);
					p.flush();
%>
				</div>
				<input type="submit" class="abutton" name="OK" id="OK.Button" tabindex="9000" value="Create" onclick="javascript:$('Command').value=this.name;" />
				<input  type="submit" class="abutton" name="Cancel" tabindex="9010" value="<%= app.getTexts().getCancelTitle() %>" onclick="javascript:$('Command').value=this.name;" />
			</td>
		</tr>
	</table>
</form>
<script language="javascript" type="text/javascript">
	Event.observe('<%= formName %>', 'submit', function(event) {
		$('<%= formName %>').request({
			onFailure: function() { },
			onSuccess: function(t) {
				$('UserDialog').update(t.responseText);
			}
		});
		Event.stop(event);
	});		
</script>
<%
p.close(false);
%>