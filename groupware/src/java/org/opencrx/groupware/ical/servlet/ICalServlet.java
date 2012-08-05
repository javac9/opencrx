/*
 * ====================================================================
 * Project:     openCRX/CalDAV, http://www.opencrx.org/
 * Name:        $Id: ICalServlet.java,v 1.38 2008/02/19 13:39:34 wfro Exp $
 * Description: ICalServlet
 * Revision:    $Revision: 1.38 $
 * Owner:       CRIXP AG, Switzerland, http://www.crixp.com
 * Date:        $Date: 2008/02/19 13:39:34 $
 * ====================================================================
 *
 * This software is published under the BSD license
 * as listed below.
 * 
 * Copyright (c) 2004-2007, CRIXP Corp., Switzerland
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
package org.opencrx.groupware.ical.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opencrx.groupware.generic.ActivitiesHelper;
import org.opencrx.groupware.generic.Util;
import org.opencrx.kernel.activity1.cci2.ActivityQuery;
import org.opencrx.kernel.activity1.jmi1.Activity;
import org.opencrx.kernel.backend.ICalendar;
import org.openmdx.application.log.AppLog;
import org.openmdx.base.text.conversion.XMLEncoder;
import org.openmdx.portal.servlet.Action;
import org.openmdx.portal.servlet.WebKeys;

public class ICalServlet extends FreeBusyServlet {

    //-----------------------------------------------------------------------
    protected Activity findActivity(
        ActivitiesHelper activitiesHelper,
        String calUid
    ) {
        ActivityQuery query = activitiesHelper.getActivityPackage().createActivityQuery();
        query.thereExistsExternalLink().equalTo(ICalendar.ICAL_SCHEMA + calUid);
        List activities = activitiesHelper.getActivitySegment().getActivity(query);
        if(activities.isEmpty()) {
            query = activitiesHelper.getActivityPackage().createActivityQuery();
            query.thereExistsExternalLink().equalTo(ICalendar.ICAL_SCHEMA + calUid.replace('.', '+'));
            activities = activitiesHelper.getActivitySegment().getActivity(query);
            if(activities.isEmpty()) {
                return null;
            }
            else {
                return (Activity)activities.iterator().next();
            }
        }
        else {
            return (Activity)activities.iterator().next();
        }
    }
        
    //-----------------------------------------------------------------------
    protected String getSelectedActivityUrl(        
        HttpServletRequest req,
        Activity activity,
        boolean htmlEncoded
    ) {
        Action selectActivityAction = 
            new Action(
                Action.EVENT_SELECT_OBJECT, 
                new Action.Parameter[]{
                    new Action.Parameter(Action.PARAMETER_OBJECTXRI, activity.refMofId())
                },
                "",
                true
            );        
        return htmlEncoded 
            ? req.getContextPath().replace("-ical-", "-core-") +  "/" + WebKeys.SERVLET_NAME + "?event=" + Action.EVENT_SELECT_OBJECT + "&amp;parameter=" + selectActivityAction.getParameterEncoded()
            : req.getContextPath().replace("-ical-", "-core-") +  "/" + WebKeys.SERVLET_NAME + "?event=" + Action.EVENT_SELECT_OBJECT + "&parameter=" + selectActivityAction.getParameter();
    }
    
    //-----------------------------------------------------------------------
    @Override
    protected void doGet(
        HttpServletRequest req, 
        HttpServletResponse resp
    ) throws ServletException, IOException {
        PersistenceManager pm = this.getPersistenceManager(req);
        String requestedActivityGroup = req.getParameter("id");
        // Locale
        String loc = req.getParameter("user.locale");
        Locale locale = loc == null
            ? Locale.getDefault()
            : new Locale(loc.substring(0, 2), loc.substring(3, 5));       
        // Time zone
        String tz = req.getParameter("user.tz");
        tz = tz == null ? TimeZone.getDefault().getID() : tz;
        TimeZone timeZone = TimeZone.getTimeZone(tz);
        int tzRawOffsetHours = (timeZone.getRawOffset() / 3600000);
        // Reformat to GMT
        tz = "GMT" + (tzRawOffsetHours >= 0 ? "+" : "") + tzRawOffsetHours;
        timeZone = TimeZone.getTimeZone(tz);
        // Date formatter
        SimpleDateFormat dateFormatUser = (SimpleDateFormat)SimpleDateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, 
            DateFormat.LONG, 
            locale
        );
        dateFormatUser.applyPattern("MMM dd yyyy HH:mm:ss 'GMT'Z");
        dateFormatUser.setTimeZone(timeZone);        
        SimpleDateFormat dateFormatEnUs = (SimpleDateFormat)SimpleDateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, 
            DateFormat.LONG, 
            new Locale("en", "US")
        );
        dateFormatEnUs.applyLocalizedPattern("MMM dd, yyyy hh:mm:ss a 'GMT'Z");
        dateFormatEnUs.setTimeZone(timeZone);
        ActivitiesHelper activitiesHelper = this.getActivitiesHelper(pm, requestedActivityGroup);
        org.opencrx.kernel.admin1.jmi1.ComponentConfiguration componentConfiguration = 
            this.getComponentConfiguration(
                 pm, 
                 activitiesHelper.getActivitySegment().refGetPath().get(2)
            );
        String maxActivitiesValue = componentConfiguration == null
            ? null
            : Util.getComponentConfigProperty("maxActivities", componentConfiguration);
        int maxActivities = Integer.valueOf(
            maxActivitiesValue == null 
                ? "500" 
                : maxActivitiesValue
        ).intValue();
        // Return all activities in ICS format
        if(RESOURCE_NAME_ACTIVITIES_ICS.equals(req.getParameter("resource"))) {
            resp.setCharacterEncoding("UTF-8");
            resp.setStatus(HttpServletResponse.SC_OK);
            ActivityQuery activityQuery = activitiesHelper.getActivityPackage().createActivityQuery();
            activityQuery.forAllDisabled().isFalse();
            activityQuery.ical().isNonNull();
            PrintWriter p = resp.getWriter();
            p.write("BEGIN:VCALENDAR\n");
            p.write("VERSION:2.0\n");
            p.write("PRODID:" + ICalendar.PROD_ID + "\n");
            p.write("CALSCALE:GREGORIAN\n");
            p.write("METHOD:PUBLISH\n");
            int n = 0;
            for(Activity activity: activitiesHelper.getFilteredActivities(activityQuery)) {
                String ical = activity.getIcal();
                if(ical.indexOf("BEGIN:VEVENT") >= 0) {
                    int start = ical.indexOf("BEGIN:VEVENT");
                    int end = ical.indexOf("END:VEVENT");
                    String vevent = ical.substring(start, end).replace("BEGIN:VEVENTBEGIN:VCALENDAR", "BEGIN:VEVENT");
                    p.write(vevent);
                    if(vevent.indexOf("URL:") < 0) {
                        p.write("URL:" + req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort() + this.getSelectedActivityUrl(req, activity, false) + "\n");
                    }
                    p.write("END:VEVENT\n");
                }
                else if(ical.indexOf("BEGIN:VTODO") >= 0) {
                    int start = ical.indexOf("BEGIN:VTODO");
                    int end = ical.indexOf("END:VTODO");
                    String vtodo = ical.substring(start, end).replace("BEGIN:VTODOBEGIN:VCALENDAR", "BEGIN:VTODO");
                    p.write(vtodo);
                    if(vtodo.indexOf("URL:") < 0) {
                        p.write("URL:" + req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort() + this.getSelectedActivityUrl(req, activity, false) + "\n");
                    }
                    p.write("END:VTODO\n");                        
                }
                n++;
                if(n % 50 == 0) pm.evictAll();                
                if(n > maxActivities) break;
            }
            p.write("END:VCALENDAR\n");
            p.flush();
        }
        // Return all activities in XML format
        else if(RESOURCE_NAME_ACTIVITIES_XML.equals(req.getParameter("resource"))) {
            resp.setCharacterEncoding("UTF-8");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/xml");
            ActivityQuery activityQuery = activitiesHelper.getActivityPackage().createActivityQuery();
            activityQuery.forAllDisabled().isFalse();
            activityQuery.scheduledStart().isNonNull();
            PrintWriter p = resp.getWriter();
            p.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            p.write("<data>\n");
            int n = 0;
            for(Activity activity: activitiesHelper.getFilteredActivities(activityQuery)) {
                p.write("  <event start=\"" + dateFormatEnUs.format(activity.getScheduledStart()) + "\" end=\"" + dateFormatEnUs.format(activity.getScheduledEnd() == null ? activity.getScheduledStart() : activity.getScheduledEnd()) + "\" link=\"" + this.getSelectedActivityUrl(req, activity, true) + "\" title=\"" + XMLEncoder.encode((activity.getActivityNumber() == null ? "" : activity.getActivityNumber().trim() + ": " ) + activity.getName()) + "\">\n");
                String description = (activity.getDescription() == null) || (activity.getDescription().trim().length() == 0)
                    ? activity.getName() 
                    : activity.getDescription();
                p.write(XMLEncoder.encode(
                    description == null ? "" : description
                ));
                p.write("  </event>\n");
                n++;
                if(n % 50 == 0) pm.evictAll();
                if(n > maxActivities) break;
            }
            p.write("</data>\n");
            p.flush();
        }
        else if(RESOURCE_NAME_ACTIVITIES_HTML.equals(req.getParameter("resource"))) {        
            int height = req.getParameter("height") == null
                ? 500
                : Integer.valueOf(req.getParameter("height"));
            resp.setCharacterEncoding("UTF-8");
            resp.setStatus(HttpServletResponse.SC_OK);
            PrintWriter p = resp.getWriter();
            p.write("<!--[if IE]><!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"><![endif]-->\n");
            p.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n");
            p.write("<html>\n");
            p.write("<head>\n");
            p.write("       <style>\n");
            p.write("           .timeline-band {\n");
            p.write("               font-family: Trebuchet MS, Helvetica, Arial, sans serif;\n");
            p.write("               font-size: 9pt;\n");
            p.write("               border: 1px solid #aaa;\n");
            p.write("           }\n");
            p.write("       </style>\n");
            p.write("       <script type=\"text/javascript\" src=\"javascript/timeline/api/timeline-api.js\"></script>\n");
            p.write("       <script language=\"javascript\" type=\"text/javascript\">\n");               
            p.write("           var tl;\n");
            p.write("           function pageinit() {\n");
            p.write("             var eventSource = new Timeline.DefaultEventSource();\n");          
            p.write("             var bandInfos = [\n");
            p.write("               Timeline.createHotZoneBandInfo({\n");
            p.write("                   zones: [\n");
            p.write("                       {   start:    \"Jan 01 1960 00:00:00 GMT-0600\",\n");
            p.write("                           end:      \"Dec 31 2050 00:00:00 GMT-0600\",\n");
            p.write("                           magnify:  30,\n");
            p.write("                           unit:     Timeline.DateTime.DAY\n");
            p.write("                       }\n");
            p.write("                   ],\n");
            p.write("                   timeZone: " + tzRawOffsetHours + ",\n");
            p.write("                   eventSource: eventSource,\n");
            p.write("                   date: \"" + dateFormatEnUs.format(new Date()) + "\",\n");               
            p.write("                   width: \"70%\",\n");
            p.write("                   intervalUnit: Timeline.DateTime.MONTH,\n"); 
            p.write("                   intervalPixels: 100\n");
            p.write("               }),\n");
            p.write("               Timeline.createHotZoneBandInfo({\n");
            p.write("                   zones: [\n");
            p.write("                       {   start:    \"Jan 01 1960 00:00:00 GMT-0600\",\n");
            p.write("                           end:      \"Dec 31 2050 00:00:00 GMT-0600\",\n");
            p.write("                           magnify:  10,\n");
            p.write("                           unit:     Timeline.DateTime.MONTH\n");
            p.write("                       }\n");
            p.write("                   ],\n");
            p.write("                   timeZone: " + tzRawOffsetHours + ",\n");
            p.write("                   showEventText: false,\n");
            p.write("                   trackHeight: 0.5,\n");
            p.write("                   trackGap: 0.2,\n");        
            p.write("                   eventSource: eventSource,\n");
            p.write("                   date: \"" + dateFormatEnUs.format(new Date()) + "\",\n");               
            p.write("                   width: \"30%\",\n"); 
            p.write("                   intervalUnit: Timeline.DateTime.YEAR,\n"); 
            p.write("                   intervalPixels: 200\n");
            p.write("               })\n");
            p.write("             ];\n");
            p.write("           bandInfos[1].syncWith = 0;\n");
            p.write("           bandInfos[1].highlight = true;\n");                    
            p.write("           bandInfos[1].eventPainter.setLayout(bandInfos[0].eventPainter.getLayout());\n");           
            p.write("\n");
            p.write("           var theme = Timeline.ClassicTheme.create();\n");
            p.write("           theme.event.label.width = 250; // px\n");
            p.write("           theme.event.bubble.width = 250;\n");
            p.write("           theme.event.bubble.height = 200;\n");
            p.write("\n");
            p.write("           tl = Timeline.create(document.getElementById(\"my-timeline\"), bandInfos);\n");
            String dataUrl = req.getContextPath() + req.getServletPath() + "?" + req.getQueryString().replace("html", "xml");
            p.write("           Timeline.loadXML(\"" + dataUrl + "\", function(xml, url) { eventSource.loadXML(xml, url); });\n");            
            p.write("       }\n");
            p.write("\n");
            p.write("           var resizeTimerID = null;\n");
            p.write("           function pageresize() {\n");
            p.write("               if (resizeTimerID == null) {\n");
            p.write("                   resizeTimerID = window.setTimeout(function() {\n");
            p.write("                       resizeTimerID = null;\n");
            p.write("                       tl.layout();\n");
            p.write("                   }, 500);\n");
            p.write("               }\n");
            p.write("           }\n");
            p.write("       </script>\n");       
            p.write("   </head>\n");
            String actionUrl = req.getContextPath() + req.getServletPath();
            p.write("   <body onload=\"javascript:pageinit();\" onresize=\"javascript:pageresize();\">\n");
            p.write("     <form name=\"Timeline\" id=\"Timeline\" action=\"" + actionUrl + "\" width=\"100%\" style=\"margin-bottom:5px;\">\n");
            p.write("       <table>\n");
            p.write("         <tr>\n");
            p.write("           <td width=\"95%\">\n");
            p.write("             <input type=\"hidden\" name=\"id\" value=\"" + req.getParameter("id") + "\">\n");
            p.write("             <input type=\"hidden\" name=\"resource\" value=\"" + req.getParameter("resource") + "\">\n");
            p.write("             <input type=\"hidden\" name=\"user.tz\" id=\"user.tz\" value=\"" + tz + "\">\n");
            p.write("             <input type=\"hidden\" name=\"user.locale\" id=\"user.locale\" value=\"" + (locale.getLanguage() + "_" + locale.getCountry()) + "\">\n");
            p.write("             <input type=\"hidden\" name=\"height\" id=\"height\" value=\"" + (Math.max(500, (height + 200) % 1300)) + "\">\n");
            p.write("             <input type=\"image\" src=\"images/magnify.gif\" alt=\"\" border=\"0\" align=\"absbottom\" />");
            p.write("           </td>\n");
            p.write("           <td>" + dateFormatUser.format(new Date()).replace(" ", "&nbsp;") + "</td>\n");
            p.write("         </tr>\n");
            p.write("       </table>\n");
            p.write("     </form>\n");
            p.write("     <div id=\"my-timeline\" style=\"height: " + height + "px; border: 1px solid #aaa\" />\n");
            p.write("   </body>\n");
            p.write("</html>\n");
            p.flush();
        }            
        else {
            super.doGet(req, resp);
        }
    }
    
    //-----------------------------------------------------------------------
    @Override
    protected void doPut(
        HttpServletRequest req, 
        HttpServletResponse resp
    ) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        PersistenceManager pm = this.getPersistenceManager(req);
        String requestedActivityGroup = req.getParameter("id");
        ActivitiesHelper activitiesHelper = this.getActivitiesHelper(pm, requestedActivityGroup);
        if(RESOURCE_NAME_ACTIVITIES_ICS.equals(req.getParameter("resource"))) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setCharacterEncoding("UTF-8");
            BufferedReader reader = new BufferedReader(req.getReader());
            String l = null;
            while((l = reader.readLine()) != null) {
                if(l.startsWith("BEGIN:VEVENT") || l.startsWith("BEGIN:VTODO")) {
                    StringBuilder calendar = new StringBuilder();
                    calendar.append("BEGIN:VCALENDAR\n");
                    calendar.append("VERSION:2.0\n");
                    calendar.append("PRODID:-" + ICalendar.PROD_ID + "\n");
                    calendar.append(
                        l.startsWith("BEGIN:VEVENT")
                        ? new StringBuilder("BEGIN:VEVENT\n")
                        : new StringBuilder("BEGIN:VTODO\n")
                    );
                    String calUid = null;
                    String lastModified = null;
                    while((l = reader.readLine()) != null) {
                        calendar.append(l).append("\n");
                        if(l.startsWith("UID:")) {
                            calUid = l.substring(4);
                        }
                        else if(l.startsWith("LAST-MODIFIED:")) {
                            lastModified = l.substring(14);
                        }
                        else if(l.startsWith("END:VEVENT") || l.startsWith("END:TODO")) {
                            break;
                        }
                    }
                    calendar.append("END:VCALENDAR\n");
                    AppLog.trace("VCALENDAR", calendar);
                    if((calUid != null) && (lastModified != null)) {
                        AppLog.detail("Lookup activity", calUid);
                        Activity activity = this.findActivity(
                            activitiesHelper, 
                            calUid
                        );
                        if(
                            (activity != null) &&
                            ((activity.getModifiedAt() == null) || 
                            // only compare date, hours and minutes (a sample date is 20070922T005655Z)
                            (lastModified.substring(0, 13).compareTo(ActivitiesHelper.formatDate(activity.getModifiedAt()).substring(0, 13)) >= 0))
                        ) {
                            try {
                                pm.currentTransaction().begin();
                                activity.importItem(
                                    calendar.toString().getBytes("UTF-8"), 
                                    "text/calendar", 
                                    "import.ics", 
                                    (short)0
                                );
                                pm.currentTransaction().commit();
                            }
                            catch(Exception e) {
                                try {
                                    pm.currentTransaction().rollback();
                                } catch(Exception e0) {}                                    
                            }
                        }
                        else {
                            AppLog.detail(
                                "Skipping ", 
                                new String[]{
                                    "UID: " + calUid, 
                                    "LAST-MODIFIED: " + lastModified, 
                                    "Activity.number: " + (activity == null ? null : activity.refMofId()),
                                    "Activity.modifiedAt:" + (activity == null ? null : activity.getModifiedAt())
                                }
                            );
                        }
                    }
                    else {
                        AppLog.detail("Skipping", calendar); 
                    }
                }                    
            }
        }            
        else {
            super.doPut(req, resp);
        }
    }

    //-----------------------------------------------------------------------
    // Members
    //-----------------------------------------------------------------------
    private static final long serialVersionUID = 4746783518992145105L;
    
    protected final static String RESOURCE_NAME_ACTIVITIES_ICS = "activities.ics";
    protected final static String RESOURCE_NAME_ACTIVITIES_HTML = "activities.html";
    protected final static String RESOURCE_NAME_ACTIVITIES_XML = "activities.xml";
    
    protected static final int MAX_ACTIVITIES = 500;
    
    protected PersistenceManagerFactory persistenceManagerFactory = null;
    
}
