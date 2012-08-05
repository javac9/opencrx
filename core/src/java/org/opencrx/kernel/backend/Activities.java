/*
 * ====================================================================
 * Project:     opencrx, http://www.opencrx.org/
 * Name:        $Id: Activities.java,v 1.21 2008/02/12 19:49:06 wfro Exp $
 * Description: Activities
 * Revision:    $Revision: 1.21 $
 * Owner:       CRIXP AG, Switzerland, http://www.crixp.com
 * Date:        $Date: 2008/02/12 19:49:06 $
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

package org.opencrx.kernel.backend;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.HeaderTokenizer;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.xml.datatype.XMLGregorianCalendar;

import org.opencrx.kernel.account1.jmi1.EmailAddress;
import org.opencrx.kernel.activity1.cci2.EmailQuery;
import org.opencrx.kernel.activity1.jmi1.AbstractEmailRecipient;
import org.opencrx.kernel.activity1.jmi1.Activity;
import org.opencrx.kernel.activity1.jmi1.Activity1Package;
import org.opencrx.kernel.activity1.jmi1.ActivityCreator;
import org.opencrx.kernel.activity1.jmi1.ActivityFollowUp;
import org.opencrx.kernel.activity1.jmi1.ActivityGroup;
import org.opencrx.kernel.activity1.jmi1.ActivityWorkRecord;
import org.opencrx.kernel.activity1.jmi1.Email;
import org.opencrx.kernel.activity1.jmi1.EmailRecipient;
import org.opencrx.kernel.activity1.jmi1.Resource;
import org.opencrx.kernel.generic.OpenCrxException;
import org.opencrx.kernel.generic.jmi1.Media;
import org.opencrx.kernel.generic.jmi1.Note;
import org.opencrx.kernel.workflow.servlet.Utils;
import org.opencrx.security.realm1.jmi1.PrincipalGroup;
import org.openmdx.application.log.AppLog;
import org.openmdx.base.exception.ServiceException;
import org.openmdx.base.text.format.DateFormat;
import org.openmdx.compatibility.base.dataprovider.cci.AttributeSelectors;
import org.openmdx.compatibility.base.dataprovider.cci.DataproviderObject;
import org.openmdx.compatibility.base.dataprovider.cci.DataproviderObject_1_0;
import org.openmdx.compatibility.base.dataprovider.cci.Directions;
import org.openmdx.compatibility.base.dataprovider.cci.SystemAttributes;
import org.openmdx.compatibility.base.dataprovider.layer.persistence.jdbc.Database_1_Attributes;
import org.openmdx.compatibility.base.naming.Path;
import org.openmdx.compatibility.base.query.FilterOperators;
import org.openmdx.compatibility.base.query.FilterProperty;
import org.openmdx.compatibility.base.query.Quantors;
import org.openmdx.kernel.exception.BasicException;
import org.openmdx.kernel.id.UUIDs;
import org.w3c.cci2.BinaryLargeObjects;

public class Activities {

    //-----------------------------------------------------------------------
    public Activities(
        Backend backend
    ) {
        this.backend = backend;
        this.icalendar = new ICalendar(
            this.backend
        );        
    }

    //-------------------------------------------------------------------------
    public void refreshItems(
        Path activityTrackerIdentity
    ) throws ServiceException {
        this.backend.flushObjectModifications(this.backend.getServiceHeader());
        this.backend.getActivities().refreshTracker(
            this.backend.retrieveObject(
                activityTrackerIdentity
            )
        );
    }
    
    //-------------------------------------------------------------------------
    private int calculateOpenActivityTimeDistribution(
      Path reference,
      String objectClass,
      int[] timeDistribution,
      String distributionOnAttribute,
      boolean lookAhead
    ) {
      try {
        List activities = this.backend.getDelegatingRequests().addFindRequest(
            reference,
            new FilterProperty[]{
                new FilterProperty(
                    Quantors.THERE_EXISTS,
                    SystemAttributes.OBJECT_CLASS,
                    FilterOperators.IS_IN,
                    new String[]{objectClass}
                ),
                new FilterProperty(
                    Quantors.THERE_EXISTS,
                    "percentComplete",
                    FilterOperators.IS_LESS,
                    new Number[]{new Short((short)100)}
                )
            },
            AttributeSelectors.SPECIFIED_AND_TYPICAL_ATTRIBUTES,
            0,
            Activities.BATCHING_MODE_SIZE,
            Directions.ASCENDING
        );
        
        int count = 0;
        for(
           Iterator i = activities.iterator(); 
           i.hasNext(); 
        ) {
          DataproviderObject_1_0 activity = (DataproviderObject_1_0)i.next();
          Date dt = new Date();
          try {
            dt = org.openmdx.base.text.format.DateFormat.getInstance().parse(
              (String)activity.values(distributionOnAttribute).get(0)
            );
          } catch(Exception e) {}

          long delayInDays = (lookAhead ? 1 : -1) * (dt.getTime() - System.currentTimeMillis()) / 86400000;
          if(delayInDays < 0) timeDistribution[0]++;
          else if(delayInDays < 1) timeDistribution[1]++;
          else if(delayInDays < 2) timeDistribution[2]++;
          else if(delayInDays < 3) timeDistribution[3]++;
          else if(delayInDays < 4) timeDistribution[4]++;
          else if(delayInDays < 5) timeDistribution[5]++;
          else if(delayInDays < 6) timeDistribution[6]++;
          else if(delayInDays < 7) timeDistribution[7]++;
          else if(delayInDays < 8) timeDistribution[8]++;
          else if(delayInDays < 15) timeDistribution[9]++;
          else if(delayInDays < 31) timeDistribution[10]++;
          else if(delayInDays < 91) timeDistribution[11]++;
          else if(delayInDays < 181) timeDistribution[12]++;
          else if(delayInDays < 361) timeDistribution[13]++;
          else timeDistribution[14]++;
          
          count++;
        }
        return count;
      }
      catch(ServiceException e) {
        return 0;
      }
    }

    //-------------------------------------------------------------------------
    DataproviderObject[] calculateUserHomeCharts(
      Path userHome,
      Path chartReference
    ) throws ServiceException {

      java.text.DateFormat dateFormat = 
          java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, new Locale("en_US")); 
      java.text.DateFormat timeFormat = 
          java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, new Locale("en_US"));
      String createdAt = dateFormat.format(new Date()) + " " + timeFormat.format(new Date());
      // try to get full name of contact
      String fullName = "";
      try {
          fullName = (String)this.backend.retrieveObject(
              (Path)this.backend.retrieveObject(userHome).values("contact").get(0)
          ).values("fullName").get(0);
      } catch(Exception e) {}
      
      DataproviderObject[] charts = new DataproviderObject[2];
      String chartTitle = null;
      
      /**
       * Assigned Activities Overview
       */
      chartTitle = (fullName.length() == 0 ? "" : fullName + ": ") + "Assigned Open Activities Overview (" + createdAt + ")";
      
      charts[0] = new DataproviderObject(
        chartReference.getChild("2")
      );
      charts[0].values(SystemAttributes.OBJECT_CLASS).add("org:opencrx:kernel:home1:Chart");
      charts[0].values("description").add(chartTitle);
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      PrintWriter pw = new PrintWriter(os);

      pw.println("BEGIN:VND.OPENDMDX-CHART");
      pw.println("VERSION:1.0");
      pw.println("COUNT:1");

      pw.println("CHART[0].TYPE:HORIZBAR");
      pw.println("CHART[0].LABEL:" + chartTitle);
      pw.println("CHART[0].SCALEXTITLE:#Activities");
      pw.println("CHART[0].SCALEYTITLE:Activity type");
      pw.println("CHART[0].COUNT:" + ACTIVITY_TYPES.length);
      
      int[] counts = new int[ACTIVITY_TYPES.length];
      int[] timeDistribution = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
      Path assignedActivityReference = userHome.getChild("assignedActivity");
      for(
          int i = 0; 
          i < ACTIVITY_TYPES.length; 
          i++
      ) {
          String activityTypeName = (String)this.backend.getCodes().getLongText(
              CODEVALUENAME_ACTIVITY_TYPE,
              (short)0, 
              true
          ).get(new Short((short)i));          
          pw.println("CHART[0].LABEL[" + i + "]:" + activityTypeName);
          counts[i] = 
              this.calculateOpenActivityTimeDistribution(
                  assignedActivityReference, 
                  ACTIVITY_TYPES[i], 
                  timeDistribution, 
                  "scheduledStart", 
                  true
              );
      }         
      int maxValue = 0;
      for(int i = 0; i < counts.length; i++) {
        pw.println("CHART[0].VAL[" + i + "]:" + counts[i]);
        pw.println("CHART[0].BORDER[" + i + "]:#000066");
        pw.println("CHART[0].FILL[" + i + "]:#F6D66D");
        maxValue = Math.max(maxValue, counts[i]);
      }
      pw.println("CHART[0].MINVALUE:0");
      pw.println("CHART[0].MAXVALUE:" + maxValue);      
      pw.println("END:VND.OPENDMDX-CHART");      
      try {
        pw.flush();
        os.close();
      } catch(Exception e) {}
      charts[0].values("chart").add(
        os.toByteArray()
      );
      charts[0].values("chartMimeType").add("application/vnd.openmdx-chart");
      charts[0].values("chartName").add(
        this.backend.toFilename(chartTitle + ".txt")
      );

      /**
       * Assigned Activities Age Distribution
       */
      chartTitle = (fullName.length() == 0 ? "" : fullName + ": ") + "Assigned Open Activities Age Distribution (" + createdAt + ")";
      
      charts[1] = new DataproviderObject(
        chartReference.getChild("3")
      );
      charts[1].values(SystemAttributes.OBJECT_CLASS).add("org:opencrx:kernel:home1:Chart");
      charts[1].values("description").add(chartTitle);
      os = new ByteArrayOutputStream();
      pw = new PrintWriter(os);

      pw.println("BEGIN:VND.OPENDMDX-CHART");
      pw.println("VERSION:1.0");
      pw.println("COUNT:1");

      pw.println("CHART[0].TYPE:VERTBAR");
      pw.println("CHART[0].LABEL:" + chartTitle);
      pw.println("CHART[0].SCALEXTITLE:#Days");
      pw.println("CHART[0].SCALEYTITLE:#Activities");
      pw.println("CHART[0].COUNT:15");
      
      pw.println("CHART[0].LABEL[0]:past due");
      pw.println("CHART[0].LABEL[1]:today");
      pw.println("CHART[0].LABEL[2]:1");
      pw.println("CHART[0].LABEL[3]:2");
      pw.println("CHART[0].LABEL[4]:3");
      pw.println("CHART[0].LABEL[5]:4");
      pw.println("CHART[0].LABEL[6]:5");
      pw.println("CHART[0].LABEL[7]:6");
      pw.println("CHART[0].LABEL[8]:7");
      pw.println("CHART[0].LABEL[9]:..14");
      pw.println("CHART[0].LABEL[10]:..30");
      pw.println("CHART[0].LABEL[11]:..90");
      pw.println("CHART[0].LABEL[12]:..180");
      pw.println("CHART[0].LABEL[13]:..360");
      pw.println("CHART[0].LABEL[14]:>360 days");
      
      maxValue = 0;
      for(int i = 0; i < 15; i++) {
        pw.println("CHART[0].VAL[" + i + "]:" + timeDistribution[i]);
        pw.println("CHART[0].BORDER[" + i + "]:#000066");
        pw.println("CHART[0].FILL[" + i + "]:#F6D66D");
        maxValue = Math.max(maxValue, timeDistribution[i]);
      }

      pw.println("CHART[0].MINVALUE:0");
      pw.println("CHART[0].MAXVALUE:" + maxValue);
      
      pw.println("END:VND.OPENDMDX-CHART");
      
      try {
        pw.flush();
        os.close();
      } catch(Exception e) {}
      charts[1].values("chart").add(
        os.toByteArray()
      );
      charts[1].values("chartMimeType").add("application/vnd.openmdx-chart");
      charts[1].values("chartName").add(
        this.backend.toFilename(chartTitle + ".txt")
      );

      return charts;
    }
    
    //-------------------------------------------------------------------------
    public DataproviderObject refreshTracker(
      DataproviderObject_1_0 activityTracker
    ) throws ServiceException {
        DataproviderObject refreshedTracker = this.backend.retrieveObjectForModification(
            activityTracker.path()
        );        
        List activites = this.backend.getDelegatingRequests().addFindRequest(
            activityTracker.path().getChild("filteredActivity"),
            null,
            AttributeSelectors.NO_ATTRIBUTES,
            0, 
            BATCHING_MODE_SIZE, 
            Directions.ASCENDING
        );
        int estimateEffortHours = 0;
        int estimateEffortMinutes = 0;
        // Iterate all activities and sum up all main effort estimates. Don't care
        // if isMain=true if there is exactly one estimate for the activity
        for(
            Iterator i = activites.iterator(); 
            i.hasNext(); 
        ) {
            DataproviderObject_1_0 activity = (DataproviderObject_1_0)i.next();
            List effortEstimates = this.backend.getDelegatingRequests().addFindRequest(
                new Path((String)activity.values(SystemAttributes.OBJECT_IDENTITY).get(0)).getChild("effortEstimate"),
                null,
                AttributeSelectors.SPECIFIED_AND_TYPICAL_ATTRIBUTES,
                0, 
                Integer.MAX_VALUE, 
                Directions.ASCENDING
            );
            if(effortEstimates.size() == 1) {
                DataproviderObject_1_0 effortEstimate = (DataproviderObject_1_0)effortEstimates.iterator().next();
                if(effortEstimate.values("estimateEffortHours").size() > 0) {
                    estimateEffortHours += ((Number)effortEstimate.values("estimateEffortHours").get(0)).intValue();
                }
                if(effortEstimate.values("estimateEffortMinutes").size() > 0) {
                    estimateEffortMinutes += ((Number)effortEstimate.values("estimateEffortMinutes").get(0)).intValue();
                }
            }
            // Lookup main estimate
            else {
                for(
                    Iterator j = effortEstimates.iterator();
                    j.hasNext();
                ) {
                    DataproviderObject_1_0 effortEstimate = (DataproviderObject_1_0)j.next();
                    if(
                        (effortEstimate.values("isMain").size() > 0) &&
                        ((Boolean)effortEstimate.values("isMain").get(0)).booleanValue()
                    ) {                    
                        if(effortEstimate.values("estimateEffortHours").size() > 0) {
                            estimateEffortHours += ((Number)effortEstimate.values("estimateEffortHours").get(0)).intValue();
                        }
                        if(effortEstimate.values("estimateEffortMinutes").size() > 0) {
                            estimateEffortMinutes += ((Number)effortEstimate.values("estimateEffortMinutes").get(0)).intValue();
                        }
                        // At most one main estimate is allowed. If the user entered more 
                        // than one include the first only
                        break;
                    }
                }
            }
        }
        // Update tracker
        refreshedTracker.clearValues("sumEstimateEffortHours").add(
            new Integer(estimateEffortHours + estimateEffortMinutes / 60)
        );
        refreshedTracker.clearValues("sumEstimateEffortMinutes").add(
            new Integer(estimateEffortMinutes % 60)
        );
        return refreshedTracker;
    }
    
    //-------------------------------------------------------------------------
    /**
     * Creates a new activity and links the tracker with this new activity.
     */
    public Activity newActivity(
        ActivityCreator activityCreator,
        String name,
        String description,
        String detailedDescription,
        Date suppliedScheduledStart,
        Date suppliedScheduledEnd,
        Date suppliedDueBy,
        Number suppliedPriority,
        Path reportingContactIdentity
    ) throws ServiceException {
        if(activityCreator.getActivityType() != null) {
            DataproviderObject_1_0 activityType = this.backend.retrieveObjectFromDelegation(
                activityCreator.getActivityType().refGetPath()
            );
            DataproviderObject_1_0 activityProcess = this.backend.retrieveObjectFromDelegation(
                (Path)activityType.values("controlledBy").get(0)
            );
            Date scheduledStart = null;
            scheduledStart = suppliedScheduledStart != null
                ? suppliedScheduledStart
                : (activityCreator.getBaseDate() != null) && (activityCreator.getScheduledStart() != null)
                    ? new Date(System.currentTimeMillis() + activityCreator.getScheduledStart().getTime())
                    : new Date();
            Date scheduledEnd = null;
            scheduledEnd = suppliedScheduledEnd != null
                ? suppliedScheduledEnd
                : (activityCreator.getBaseDate() != null) && (activityCreator.getScheduledEnd() != null) 
                    ? new Date(System.currentTimeMillis() + activityCreator.getScheduledEnd().getTime() - activityCreator.getBaseDate().getTime())
                    : new Date(scheduledStart.getTime() + 3600000L);
            Date dueBy = null;
            dueBy = suppliedDueBy != null
                ? suppliedDueBy
                : (activityCreator.getBaseDate() != null) && (activityCreator.getDueBy() != null)
                    ? new Date(System.currentTimeMillis() + activityCreator.getDueBy().getTime() - activityCreator.getBaseDate().getTime())
                    : null;
            short priority = suppliedPriority != null
                ? suppliedPriority.shortValue()
                : activityCreator.getPriority() != 0
                    ? activityCreator.getPriority()
                    : (short)2; // NORMAL
            Path newActivityIdentity = new Path("xri:@openmdx:org.opencrx.kernel.activity1/provider");
            newActivityIdentity = 
                newActivityIdentity.getDescendant(
                    new String[]{
                        activityCreator.refGetPath().get(2), 
                        "segment", 
                        activityCreator.refGetPath().get(4), 
                        "activity", 
                        this.backend.getUidAsString()
                    }
                );
            DataproviderObject newActivity = new DataproviderObject(newActivityIdentity);
            String activityClass = !activityType.values("activityClassName").isEmpty()
                ? (String)activityType.values("activityClassName").get(0)                
                : ACTIVITY_TYPES[((Number)activityType.values("activityClass").get(0)).intValue()];
            newActivity.values(SystemAttributes.OBJECT_CLASS).add(activityClass);
            if(name != null) {
                newActivity.values("name").add(name);
            }
            if(description != null) {
                newActivity.values("description").add(description);
            }
            if(detailedDescription != null) {
                newActivity.values("detailedDescription").add(
                    detailedDescription
                );
            }
            if(scheduledStart != null) {
                newActivity.values("scheduledStart").add(
                    DateFormat.getInstance().format(scheduledStart)
                );
            }
            if(scheduledEnd != null) {
                newActivity.values("scheduledEnd").add(
                    DateFormat.getInstance().format(scheduledEnd)
                );
            }
            if(reportingContactIdentity != null) {
                newActivity.values("reportingContact").add(reportingContactIdentity);                    
            }
            else {
                DataproviderObject_1_0 userHome = this.backend.getUserHomes().getUserHome(
                    newActivity.path()
                );
                newActivity.values("reportingContact").addAll(
                    userHome.values("contact")
                );
            }
            newActivity.values("priority").add(
                new Short(priority)
            );
            newActivity.values("dueBy").add(
                dueBy != null 
                    ? DateFormat.getInstance().format(dueBy)
                    : MAX_DATE
            );
            newActivity.values("activityState").add(
                new Short((short)0)
            );
            newActivity.values("percentComplete").add(
                new Short((short)0)
            );
            newActivity.values("activityType").add(
                activityType.path()
            );
            newActivity.values("processState").addAll(
                activityProcess.values("startState")
            );
            // Set code values to 0 (non-optional attributes)
            if(this.backend.getModel().isSubtypeOf(activityClass, "org:opencrx:kernel:activity1:Incident")) {
                newActivity.values("caseOrigin").add(new Short((short)0));
                newActivity.values("caseType").add(new Short((short)0));
                newActivity.values("customerSatisfaction").add(new Short((short)0));
                newActivity.values("severity").add(new Short((short)0));
                newActivity.values("reproducibility").add(new Short((short)0));
            }
            try {                
                // Create activity
                this.backend.createObject(
                    newActivity
                );
                this.reapplyActivityCreator(
                    newActivity.path(),
                    (ActivityCreator)this.backend.getDelegatingPkg().refObject(activityCreator.refGetPath().toXri())
                );
                this.updateIcal(
                    newActivity.path()
                );
                return (Activity)this.backend.getDelegatingPkg().refObject(newActivityIdentity.toXri());
            }
            catch(ServiceException e) {
                AppLog.warning("Creation of new activity failed", e.getMessage());
                AppLog.warning(e.getMessage(), e.getCause(), 1);
            }
        }
        return null;
    }
    
    //-------------------------------------------------------------------------
    public void voteForActivity(
        Path activityIdentity,
        String name,
        String description
    ) throws ServiceException {
        this.voteForActivity(
            this.backend.retrieveObject(
                activityIdentity
            ),
            name, 
            description
        );
    }
            
    //-------------------------------------------------------------------------
    public Path voteForActivity(
        DataproviderObject_1_0 activity,
        String name,
        String description
    ) throws ServiceException {
        DataproviderObject vote = new DataproviderObject(
            activity.path().getDescendant(new String[]{"vote", this.backend.getUidAsString()})
        );
        vote.values(SystemAttributes.OBJECT_CLASS).add("org:opencrx:kernel:activity1:ActivityVote");
        if(name != null) {
            vote.values("name").add(name);
        }
        if(description != null) {
            vote.values("description").add(description);
        }
        this.backend.getBase().assignToMe(
            vote,
            null,
            true,
            null
        );
        this.backend.createObject(vote);
        return vote.path();
    }
    
    //-------------------------------------------------------------------------
    public ActivityFollowUp doFollowUp(
        Path activityIdentity,
        String followUpTitle,
        String followUpText,
        Path processTransitionIdentity,
        Path assignTo
    ) throws ServiceException {
        DataproviderObject_1_0 activity = this.backend.retrieveObjectForModification(
            activityIdentity
        );
        Path processStateIdentity = (Path)activity.values("processState").get(0);
        if(processTransitionIdentity != null) {
            DataproviderObject_1_0 processTransition = this.backend.retrieveObjectFromDelegation(
                processTransitionIdentity
            );
            if(processTransition.values("nextState").size() == 0) {
                throw new ServiceException(
                    OpenCrxException.DOMAIN,
                    OpenCrxException.ACTIVITY_UNDEFINED_NEXT_STATE, 
                    new BasicException.Parameter[]{
                        new BasicException.Parameter("param0", processTransition.path())
                    },
                    "Undefined next state. Transition not possible."
                );
            }
            // Check that previous state of transition matches the current activity's state
            if(
                ((processTransition.values("prevState").size() == 0) && (processStateIdentity == null)) ||
                ((processTransition.values("prevState").size() > 0) && processTransition.values("prevState").get(0).equals(processStateIdentity))
            ) {
                
            }
            else {
                throw new ServiceException(
                    OpenCrxException.DOMAIN,
                    OpenCrxException.ACTIVITY_TRANSITION_NOT_VALID_FOR_STATE, 
                    new BasicException.Parameter[]{
                        new BasicException.Parameter("param0", processTransitionIdentity),
                        new BasicException.Parameter("param1", processStateIdentity)
                    },
                    "Transition is not valid for current state"
                );
            } 
            // Apply transition to activity
            activity.clearValues("lastTransition").add(
                processTransitionIdentity         
            );    
            activity.clearValues("percentComplete").addAll(
                processTransition.values("newPercentComplete")
            );
            activity.clearValues("activityState").addAll(
                processTransition.values("newActivityState")
            );
            
            /**
             * Execute actions. If at least the execution of one action fails
             * the transition is considered as failed. In this case the activity
             * is set to set errState if defined.
             */
            Path nextState = (Path)processTransition.values("nextState").get(0);
            Path errState = (Path)processTransition.values("errState").get(0);
            List actions = this.backend.getDelegatingRequests().addFindRequest(
                processTransitionIdentity.getChild("action"),
                null,
                AttributeSelectors.ALL_ATTRIBUTES,
                0,
                Integer.MAX_VALUE,
                Directions.ASCENDING
            );
            boolean failed = false;
            for(
                Iterator i = actions.iterator(); 
                i.hasNext(); 
            ) {
                DataproviderObject_1_0 action = (DataproviderObject_1_0)i.next();
                String actionClass = (String)action.values(SystemAttributes.OBJECT_CLASS).get(0);
                // SetActualEndAction
                if(this.backend.getModel().isSubtypeOf(actionClass, "org:opencrx:kernel:activity1:SetActualEndAction")) {
                    if(!action.values("resetToNull").isEmpty() && ((Boolean)action.values("resetToNull").get(0)).booleanValue()) {
                        activity.clearValues("actualEnd");
                    }
                    else {
                        activity.clearValues("actualEnd").add(
                            DateFormat.getInstance().format(new Date())
                        );
                    }
                }
                // SetActualStartAction
                else if(this.backend.getModel().isSubtypeOf(actionClass, "org:opencrx:kernel:activity1:SetActualStartAction")) {
                    if(!action.values("resetToNull").isEmpty() && ((Boolean)action.values("resetToNull").get(0)).booleanValue()) {
                        activity.clearValues("actualStart");
                    }
                    else {
                        activity.clearValues("actualStart").add(
                            DateFormat.getInstance().format(new Date())
                        );
                    }
                }
                // SetAssignedToAction
                else if(this.backend.getModel().isSubtypeOf(actionClass, "org:opencrx:kernel:activity1:SetAssignedToAction")) {
                    List resources = this.backend.getDelegatingRequests().addFindRequest(
                        activityIdentity.getPrefix(activityIdentity.size() - 2).getChild("resource"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );
                    boolean assigned = false;
                    // Try to find resource which matches specified contact
                    Path contactIdentity = null;
                    if(
                        !action.values("contactFeatureName").isEmpty() &&
                        !activity.values((String)action.values("contactFeatureName").get(0)).isEmpty()
                    ) {
                        contactIdentity = (Path)activity.values((String)action.values("contactFeatureName").get(0)).get(0);
                    }
                    else {
                        DataproviderObject_1_0 userHome = this.backend.getUserHomes().getUserHome(action.path());
                        contactIdentity = (Path)userHome.values("contact").get(0);
                    }
                    if(contactIdentity != null) {
                        try {
                            for(
                                Iterator j = resources.iterator(); 
                                j.hasNext(); 
                            ) {
                                DataproviderObject_1_0 resource = (DataproviderObject_1_0)j.next();
                                if(
                                    !resource.values("contact").isEmpty() &&
                                    contactIdentity.equals(resource.values("contact").get(0))
                                ) {
                                    this.assignTo(
                                        activityIdentity,
                                        resource.path()
                                    );
                                    assigned = true;
                                    break;
                                }
                            }
                        }
                        catch(Exception e) {
                            AppLog.warning("Execution of action failed --> transition failed.", action);
                            new ServiceException(e).log();
                            failed = true;
                        }                            
                    }
                    // Try to find resource with matching resource order
                    if(!assigned && !action.values("resourceOrder").isEmpty()) {
                        try {
                            for(
                                Iterator j = resources.iterator(); 
                                j.hasNext(); 
                            ) {
                                DataproviderObject_1_0 assignedResource = (DataproviderObject_1_0)j.next();
                                if(!assignedResource.values("resourceOrder").isEmpty()) {
                                    short actionResourceOrder = ((Number)action.values("resourceOrder").get(0)).shortValue();
                                    short assignmentResourceOrder = ((Number)assignedResource.values("resourceOrder").get(0)).shortValue();
                                    if(
                                        (actionResourceOrder == assignmentResourceOrder) &&
                                        !assignedResource.values("resource").isEmpty()
                                    ) {
                                        this.assignTo(
                                            activityIdentity,
                                            (Path)assignedResource.values("resource").get(0)
                                        );
                                        assigned = true;
                                        break;
                                    }
                                }
                            }
                        }
                        catch(Exception e) {
                            AppLog.warning("Execution of action failed --> transition failed.", action);
                            new ServiceException(e).log();
                            failed = true;
                        }                            
                    }
                    // Try to find resource with matching resource role
                    if(!assigned && !action.values("resourceRole").isEmpty()) {
                        try {
                            for(
                                Iterator j = resources.iterator(); 
                                j.hasNext(); 
                            ) {
                                DataproviderObject_1_0 assignedResource = (DataproviderObject_1_0)j.next();
                                if(!assignedResource.values("resourceRole").isEmpty()) {
                                    short actionResourceRole = ((Number)action.values("resourceRole").get(0)).shortValue();
                                    short assignmentResourceRole = ((Number)assignedResource.values("resourceRole").get(0)).shortValue();
                                    if(
                                        (actionResourceRole == assignmentResourceRole) &&
                                        !assignedResource.values("resource").isEmpty()
                                    ) {
                                        this.assignTo(
                                            activityIdentity,
                                            (Path)assignedResource.values("resource").get(0)
                                        );
                                        assigned = true;
                                        break;
                                    }
                                }
                            }
                        }
                        catch(Exception e) {
                            AppLog.warning("Execution of action failed --> transition failed.", action);
                            new ServiceException(e).log();
                            failed = true;
                        }                            
                    }
                }
                // WfAction
                else if(this.backend.getModel().isSubtypeOf(actionClass, "org:opencrx:kernel:activity1:WfAction")) {
                    DataproviderObject_1_0 userHome = this.backend.getUserHomes().getUserHome(action.path());
                    if(!action.values("wfProcess").isEmpty()) {
                        try {
                            DataproviderObject_1_0 wfProcessInstance = 
                                this.backend.getWorkflows().executeWorkflow(
                                    userHome,
                                    (Path)action.values("wfProcess").get(0),
                                    activityIdentity,
                                    null,
                                    null,
                                    null,
                                    null                            
                                );
                            AppLog.info("Execution of workflow successful.", action);
                            Boolean wfExecutionFailed = (Boolean)wfProcessInstance.values("failed").get(0);
                            if((wfExecutionFailed != null) && wfExecutionFailed.booleanValue()) {
                                failed = true;
                            }
                        }
                        catch(Exception e) {
                            AppLog.warning("Execution of action failed --> transition failed.", action);
                            new ServiceException(e).log();
                            failed = true;
                        }                            
                    }
                }
                // ActivityCreationAction
                else if(this.backend.getModel().isSubtypeOf(actionClass, "org:opencrx:kernel:activity1:ActivityCreationAction")) {
                    if(!action.values("activityCreator").isEmpty()) {
                        try {
                            Path newActivityIdentity = this.newActivity(
                                (ActivityCreator)this.backend.getDelegatingPkg().refObject(
                                    ((Path)action.values("activityCreator").get(0)).toXri()
                                ),
                                (String)action.values("activityName").get(0),
                                (String)action.values("activityDescription").get(0),
                                null,
                                null,
                                null,
                                null,
                                null,
                                (Path)activity.values("reportingContact").get(0)
                            ).refGetPath();
                            // Link new activity with original
                            DataproviderObject activityLinkTo = new DataproviderObject(
                                newActivityIdentity.getDescendant(new String[]{"activityLinkTo", this.backend.getUidAsString()})
                            );
                            activityLinkTo.values(SystemAttributes.OBJECT_CLASS).add("org:opencrx:kernel:activity1:ActivityLinkTo");
                            activityLinkTo.values("name").addAll(
                                activity.values("name")
                            );
                            activityLinkTo.values("activityLinkType").add(
                                new Short(ACTIVITY_LINK_TYPE_IS_DERIVED_FROM)
                            );
                            activityLinkTo.values("linkTo").add(activityIdentity);
                            this.backend.createObject(
                                activityLinkTo
                            );
                        }
                        catch(Exception e) {
                            AppLog.warning("Execution of action failed --> transition failed.", action);
                            new ServiceException(e).log();
                            failed = true;
                        }                            
                    }
                }
                // LinkedActivityFollowUpAction
                else if(this.backend.getModel().isSubtypeOf(actionClass, "org:opencrx:kernel:activity1:LinkedActivityFollowUpAction")) {
                    List activityLinks = this.backend.getDelegatingRequests().addFindRequest(
                        activityIdentity.getChild("activityLinkTo"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );                    
                    for(
                        Iterator j = activityLinks.iterator(); 
                        j.hasNext(); 
                    ) {
                        DataproviderObject_1_0 activityLink = (DataproviderObject_1_0)i.next();
                        Number activityLinkType = (Number)activityLink.values("activityLinkType").get(0);
                        Number actionActivityLinkType = (Number)action.values("activityLinkType").get(0);  
                        if(
                            !activityLink.values("linkTo").isEmpty() &&
                            !action.values("transition").isEmpty() &&
                            (actionActivityLinkType != null) && 
                            (activityLinkType != null) &&
                            (actionActivityLinkType.shortValue() == activityLinkType.shortValue())
                        ) {
                            try {
                                this.doFollowUp(
                                    (Path)activityLink.values("linkTo").get(0),
                                    (String)action.values("transitionTitle").get(0),
                                    (String)action.values("transitionText").get(0),
                                    (Path)action.values("transition").get(0),
                                    null
                                );
                            }
                            catch(Exception e) {
                                AppLog.warning("Execution of action failed --> transition failed.", action);
                                new ServiceException(e).log();
                                failed = true;
                            }
                        }
                    }
                }                
            }
            activity.clearValues("processState").add(
                failed && errState != null
                    ? errState
                    : nextState
            );
        }
        
        // Create transition
        DataproviderObject transition = new DataproviderObject(
            activity.path().getDescendant(new String[]{"followUp", this.backend.getUidAsString()})
        );
        transition.values(SystemAttributes.OBJECT_CLASS).add("org:opencrx:kernel:activity1:ActivityFollowUp");
        transition.values("transition").add(processTransitionIdentity);
        transition.values("title").add(followUpTitle);
        transition.values("text").add(followUpText);
        if(assignTo == null) {
            this.backend.getBase().assignToMe(
                transition,
                null,
                true,
                null
            );
        }
        else {
            transition.values("assignedTo").add(assignTo);
        }
        this.backend.createObject(transition);  
        return transition == null
            ? null
            : (ActivityFollowUp)this.backend.getDelegatingPkg().refObject(transition.path().toXri());
    }
        
    //-------------------------------------------------------------------------
    public void updateWorkRecord(
        DataproviderObject workRecord,
        DataproviderObject_1_0 oldValues
    ) throws ServiceException {
        if(this.backend.isActivityWorkRecord(workRecord)) {
            DateFormat dateFormat = DateFormat.getInstance();
            String startedAt = workRecord.getValues("startedAt") != null
                ? (String)workRecord.values("startedAt").get(0)
                : (oldValues == null ? dateFormat.format(new Date()) : (String)oldValues.values("startedAt").get(0));
            String endedAt = workRecord.getValues("endedAt") != null
                ? (String)workRecord.values("endedAt").get(0)
                : (oldValues == null ? dateFormat.format(new Date()) : (String)oldValues.values("endedAt").get(0));
            Number durationHours = workRecord.getValues("durationHours") != null
                ? (Number)workRecord.values("durationHours").get(0)
                : (oldValues == null ? new Integer(0) : (Number)oldValues.values("durationHours").get(0));
            Number durationMinutes = workRecord.getValues("durationMinutes") != null
                ? (Number)workRecord.values("durationMinutes").get(0)
                : (oldValues == null ? new Integer(0) : (Number)oldValues.values("durationMinutes").get(0));
            Number pauseDurationHours = workRecord.getValues("pauseDurationHours") != null
                ? (Number)workRecord.values("pauseDurationHours").get(0)
                : (oldValues == null ? new Integer(0) : (Number)oldValues.values("pauseDurationHours").get(0));
            Number pauseDurationMinutes = workRecord.getValues("pauseDurationMinutes") != null
                ? (Number)workRecord.values("pauseDurationMinutes").get(0)
                : (oldValues == null ? new Integer(0) : (Number)oldValues.values("pauseDurationMinutes").get(0));
            durationHours = durationHours == null ? new Integer(0) : durationHours;
            durationMinutes = durationMinutes == null ? new Integer(0) : durationMinutes;
            pauseDurationHours = pauseDurationHours == null ? new Integer(0) : pauseDurationHours;
            pauseDurationMinutes = pauseDurationMinutes == null ? new Integer(0) : pauseDurationMinutes;
            // depotSelector
            short depotSelector = workRecord.getValues("depotSelector") != null
                ? ((Number)workRecord.values("depotSelector").get(0)).shortValue()
                : ((oldValues == null) || (oldValues.getValues("depotSelector") == null) 
                    ? Depots.DEPOT_USAGE_WORK_EFFORT 
                    : ((Number)oldValues.values("depotSelector").get(0)).shortValue());
            if(depotSelector == 0) {
                depotSelector = Depots.DEPOT_USAGE_WORK_EFFORT;
            }
            // Calculate duration
            int durationCalculationMode = workRecord.getValues("durationCalculationMode") != null
                ? ((Number)workRecord.getValues("durationCalculationMode").get(0)).intValue()
                : (oldValues == null ? DURATION_CALCULATION_MODE_CALC_DURATION : ((Number)oldValues.values("durationCalculationMode").get(0)).intValue());
            if(durationCalculationMode == DURATION_CALCULATION_MODE_CALC_DURATION) {
                long duration = 0;
                try {
                    duration = 
                        dateFormat.parse(endedAt).getTime() - dateFormat.parse(startedAt).getTime() -
                        pauseDurationHours.longValue() * 3600000 - pauseDurationMinutes.longValue() * 60000;
                    durationHours = new Long(duration / 3600000);
                    durationMinutes = new Long((duration % 3600000) / 60000);                
                    workRecord.clearValues("durationHours").add(durationHours);
                    workRecord.clearValues("durationMinutes").add(durationMinutes);
                } catch(ParseException e) {}
            }
            // Calculate pause
            else {
                long pauseDuration = 0;
                try {
                    pauseDuration = 
                        dateFormat.parse(endedAt).getTime() - dateFormat.parse(startedAt).getTime() -
                        durationHours.longValue() * 3600000 - durationMinutes.longValue() * 60000;
                    pauseDurationHours = new Long(pauseDuration / 3600000);
                    pauseDurationMinutes = new Long((pauseDuration % 3600000) / 60000);                
                    workRecord.clearValues("pauseDurationHours").add(pauseDurationHours);
                    workRecord.clearValues("pauseDurationMinutes").add(pauseDurationMinutes);
                } catch(ParseException e) {}                
            }            
            // Billable amount
            DataproviderObject_1_0 resourceAssignment = this.backend.retrieveObjectFromDelegation(
                workRecord.path().getPrefix(workRecord.path().size() - 2)
            );
            // rateType
            Number rateType = workRecord.getValues("rateType") != null
                ? (Number)workRecord.values("rateType").get(0)
                : ((oldValues == null) || (oldValues.getValues("rateType") == null) 
                      ? new Short(RATE_TYPE_NA)
                      : (Number)oldValues.values("rateType").get(0));
            workRecord.clearValues("rateType").add(rateType);
            // rate
            DataproviderObject_1_0 resource = null;
            if(resourceAssignment.values("resource").size() > 0) {
                resource = this.backend.retrieveObjectFromDelegation(
                    (Path)resourceAssignment.values("resource").get(0)
                );
            }
            BigDecimal rate = null;
            if((rateType == null) || (rateType.shortValue() == RATE_TYPE_NA)) {
                rate = workRecord.getValues("rate") != null
                    ? (BigDecimal)workRecord.values("rate").get(0)
                    : (oldValues == null ? new BigDecimal(0) : (BigDecimal)oldValues.values("rate").get(0));
            }
            else if(rateType.shortValue() == RATE_TYPE_STANDARD) { 
                rate = (BigDecimal)resource.values("standardRate").get(0);
            }
            else {
                rate = (BigDecimal)resource.values("overtimeRate").get(0);
            }
            if(rate == null) {
                rate = new BigDecimal(0);
            }
            boolean updateWorkCompoundBooking =
                (oldValues == null) ||
                (((Number)workRecord.values("durationHours").get(0)).intValue () != ((Number)oldValues.values("durationHours").get(0)).intValue()) ||
                (((Number)workRecord.values("durationMinutes").get(0)).intValue() != ((Number)oldValues.values("durationMinutes").get(0)).intValue());
            boolean updateBillableAmount =
                updateWorkCompoundBooking ||
                oldValues.values("rateType").isEmpty() ||
                (rateType.intValue () != ((Number)oldValues.values("rateType").get(0)).intValue()) ||
                (oldValues.values("rate").size() == 0) ||
                (rate.doubleValue() != ((Number)oldValues.values("rate").get(0)).doubleValue());
            // Update billable amount if rate or rate type has been changed
            if(updateBillableAmount) {
                workRecord.clearValues("rate").add(rate);
                // billableAmount
                workRecord.clearValues("billableAmount").add(
                    rate.multiply(
                        new BigDecimal(durationHours.intValue())
                    ).add(
                        rate.multiply(new BigDecimal(durationMinutes.intValue()).divide(new BigDecimal(60), 2, BigDecimal.ROUND_DOWN))
                    )
                );
            }
            // Update work booking if duration has been changed                
            if(updateWorkCompoundBooking) {
                if(!workRecord.values("workCb").isEmpty()) {
                    try {
                        this.backend.getDepots().removeCompoundBooking(
                            (Path)workRecord.getValues("workCb").get(0)
                        );
                    } catch(Exception e) {}
                }
                workRecord.clearValues("workCb");
                // Depot credit
                // Get assigned depot of resource with usage DEPOT_USAGE_WORK_EFFORT
                DataproviderObject_1_0 depotCredit = null;
                Path resourceIdentity = (Path)resourceAssignment.values("resource").get(0);
                List depotReferences = null;
                if(resourceIdentity == null) {
                    depotReferences = Collections.EMPTY_LIST;
                }
                else {
                    depotReferences = this.backend.getDelegatingRequests().addFindRequest(
                        resourceIdentity.getChild("depotReference"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );
                }
                // Depot selector
                for(
                    Iterator j = depotReferences.iterator();
                    j.hasNext();
                ) {
                    DataproviderObject_1_0 depotReference = (DataproviderObject_1_0)j.next();
                    Number depotUsage = (Number)depotReference.values("depotUsage").get(0);
                    if((depotUsage != null) && (depotUsage.shortValue() == depotSelector)) {
                        depotCredit = this.backend.retrieveObject(
                            (Path)depotReference.values("depot").get(0)
                        );
                    }
                }            
                // Depot debit
                // Get assigned depot of activity with usage DEPOT_USAGE_WORK_EFFORT
                DataproviderObject_1_0 depotDebit = null;
                DataproviderObject_1_0 activity = this.backend.retrieveObjectFromDelegation(
                    workRecord.path().getPrefix(workRecord.path().size() - 4)
                );
                depotReferences = this.backend.getDelegatingRequests().addFindRequest(
                    activity.path().getChild("depotReference"),
                    null,
                    AttributeSelectors.ALL_ATTRIBUTES,
                    0,
                    Integer.MAX_VALUE,
                    Directions.ASCENDING
                );
                for(
                    Iterator j = depotReferences.iterator();
                    j.hasNext();
                ) {
                    DataproviderObject_1_0 depotReference = (DataproviderObject_1_0)j.next();
                    Number depotUsage = (Number)depotReference.values("depotUsage").get(0);
                    if((depotUsage != null) && (depotUsage.shortValue() == depotSelector)) {
                        depotDebit = this.backend.retrieveObject(
                            (Path)depotReference.values("depot").get(0)
                        );
                    }
                }
                Path activityTypeIdentity = (Path)activity.values("activityType").get(0);
                if(
                    (depotCredit != null) &&
                    (depotDebit != null) &&
                    (activityTypeIdentity != null)
                ) {
                    if(!depotCredit.path().getPrefix(7).equals(depotDebit.path().getPrefix(7))) {
                        throw new ServiceException(
                            OpenCrxException.DOMAIN,
                            OpenCrxException.DEPOT_POSITION_IS_LOCKED,
                            new BasicException.Parameter[]{
                                new BasicException.Parameter("param0", depotDebit.path()),
                                new BasicException.Parameter("param1", depotCredit.path())
                            },
                            "Depot entity not equal"
                        );
                    }
                    else {
                        DataproviderObject_1_0 activityType = this.backend.retrieveObjectFromDelegation(
                            activityTypeIdentity
                        );
                        Date valueDate = this.backend.parseDate((String)workRecord.values("endedAt").get(0));
                        Path positionCreditIdentity = this.backend.getDepots().openDepotPosition(
                            depotCredit,
                            (String)activityType.values("name").get(0),
                            (String)activityType.values("description").get(0),
                            valueDate,
                            null, // if position does not exist open latest at value date
                            null,
                            null,
                            Boolean.FALSE
                        ).path();
                        Path positionDebitIdentity = this.backend.getDepots().openDepotPosition(
                            depotDebit,
                            (String)activityType.values("name").get(0),
                            (String)activityType.values("description").get(0),
                            valueDate,
                            null, // if position does not exist open latest at value date
                            null,
                            null,
                            Boolean.FALSE
                        ).path();
                        DataproviderObject_1_0 workCb = 
                            this.backend.getDepots().createCreditDebitBooking(
                                depotCredit.path().getPrefix(7),
                                this.backend.parseDate((String)workRecord.values("endedAt").get(0)),
                                Depots.BOOKING_TYPE_STANDARD,
                                new BigDecimal(durationHours.intValue()).add(new BigDecimal(durationMinutes.intValue()).divide(new BigDecimal(60), 2, BigDecimal.ROUND_DOWN)),
                                BOOKING_TEXT_NAME_WORK_EFFORT,
                                (Path)activityType.values("workBt").get(0),
                                positionCreditIdentity,
                                positionDebitIdentity,
                                null,
                                workRecord.path(),
                                null
                            );
                        workRecord.clearValues("workCb").add(
                            workCb.path()
                        );
                    }
                }
            }
        }
    }
    
    //-------------------------------------------------------------------------
    public ActivityWorkRecord addWorkRecord(
        Path resourceIdentity,
        Path activityIdentity,
        String name,
        String description,
        Date startedAt,
        Date endedAt,
        Number durationHours,
        Number durationMinutes,
        Number pauseDurationHours,
        Number pauseDurationMinutes,
        Number rateType,
        short durationCalculationMode,
        short depotSelector
    ) throws ServiceException {
        DataproviderObject workRecord = null;
        if(resourceIdentity == null) {
           throw new ServiceException(
               OpenCrxException.DOMAIN,
               OpenCrxException.ACTIVITY_CAN_NOT_ADD_WORK_RECORD_MISSING_RESOURCE,
               null,
               "Can not add work record. Missing resource"
           );            
        }
        if(activityIdentity == null) {
           throw new ServiceException(
               OpenCrxException.DOMAIN,
               OpenCrxException.ACTIVITY_CAN_NOT_ADD_WORK_RECORD_MISSING_ACTIVITY,
               null,
               "Can not add work record. Missing activity"
           );            
        }
        DataproviderObject_1_0 activity = this.backend.retrieveObjectFromDelegation(activityIdentity);
        DataproviderObject_1_0 resource = this.backend.retrieveObjectFromDelegation(resourceIdentity);            
        List resourceAssignments = this.backend.getDelegatingRequests().addFindRequest(
            activityIdentity.getChild("assignedResource"),
            new FilterProperty[]{
                new FilterProperty(
                    Quantors.THERE_EXISTS,
                    "resource",
                    FilterOperators.IS_IN,
                    new Object[]{resourceIdentity}
                )
            }
        );
        Path resourceAssignmentIdentity = null;
        if(resourceAssignments.isEmpty()) {
            resourceAssignmentIdentity = this.createResourceAssignment(
                activity, 
                resource, 
                (short)0
            ).path();
        }
        if(!resourceAssignments.isEmpty()) {
            resourceAssignmentIdentity = 
                ((DataproviderObject_1_0)resourceAssignments.iterator().next()).path();
        }            
        workRecord = new DataproviderObject(
            resourceAssignmentIdentity.getDescendant(new String[]{"workRecord", this.backend.getUidAsString()})
        );
        workRecord.values(SystemAttributes.OBJECT_CLASS).add(
            "org:opencrx:kernel:activity1:ActivityWorkRecord"
        );
        if(name != null) {
            workRecord.values("name").add(name);
        }
        if(description != null) {
            workRecord.values("description").add(description);
        }                
        workRecord.values("startedAt").add(
            startedAt == null
                ? DateFormat.getInstance().format(new Date()).substring(0, 8) + "T000000.000Z"
                : DateFormat.getInstance().format(startedAt)
        );
        workRecord.values("endedAt").add(
            endedAt == null
                ? startedAt == null
                    ? DateFormat.getInstance().format(new Date(System.currentTimeMillis() + 86400000L)).substring(0, 8) + "T000000.000Z"
                    : DateFormat.getInstance().format(new Date(startedAt.getTime() + 86400000L)).substring(0, 8) + "T000000.000Z"
                : DateFormat.getInstance().format(endedAt)
        );
        if(durationHours != null) {
            workRecord.values("durationHours").add(durationHours);
        }
        if(durationMinutes != null) {
            workRecord.values("durationMinutes").add(durationMinutes);
        }
        if(pauseDurationHours != null) {
            workRecord.values("pauseDurationHours").add(pauseDurationHours);
        }
        if(pauseDurationMinutes != null) {
            workRecord.values("pauseDurationMinutes").add(pauseDurationMinutes);
        }
        workRecord.values("durationCalculationMode").add(
            new Short(durationCalculationMode)
        );
        workRecord.values("isBillable").add(Boolean.TRUE);
        workRecord.values("billingCurrency").addAll(
            resource.values("rateCurrency")
        );
        workRecord.values("rateType").add(
            rateType == null
                ? new Short(RATE_TYPE_NA)
                : rateType
        );  
        // Take DEPOT_USAGE_WORK_EFFORT as default
        workRecord.values("depotSelector").add(
            depotSelector == 0
                ? new Short(Depots.DEPOT_USAGE_WORK_EFFORT)
                : new Short(depotSelector)
        );                
        this.backend.createObject(
            workRecord
        );
        this.updateWorkRecord(
            this.backend.retrieveObjectForModification(
                workRecord.path()
            ),
            null
        );
        return workRecord == null
            ? null
            : (ActivityWorkRecord)this.backend.getDelegatingPkg().refObject(workRecord.path().toXri());
    }
    
    //-------------------------------------------------------------------------
    public ActivityWorkRecord resourceAddWorkRecordByDuration(
        Path resourceIdentity,
        Path activityIdentity,
        String name,
        String description,
        Date startAt,
        Date endAt,
        Short durationHours,
        Short durationMinutes,
        short rateType,
        short depotSelector
    ) throws ServiceException {
        return this.addWorkRecord(
            resourceIdentity,
            activityIdentity,
            name,
            description,
            startAt, 
            endAt,
            durationHours,
            durationMinutes,
            null,
            null,
            rateType,
            Activities.DURATION_CALCULATION_MODE_CALC_PAUSE,
            depotSelector == 0
                ? Depots.DEPOT_USAGE_WORK_EFFORT
                : depotSelector           
        );
    }
    
    //-------------------------------------------------------------------------
    public ActivityWorkRecord activityAddWorkRecordByDuration(
        Path activityIdentity,
        String name,
        String description,
        Date startAt,
        Date endAt,
        Short durationHours,
        Short durationMinutes,
        short rateType,
        short depotSelector,
        Path resourceIdentity
    ) throws ServiceException {
        return this.addWorkRecord(
            resourceIdentity,
            activityIdentity,
            name,
            description,
            startAt, 
            endAt,
            durationHours,
            durationMinutes,
            null,
            null,
            rateType,
            Activities.DURATION_CALCULATION_MODE_CALC_PAUSE,
            depotSelector == 0
                ? Depots.DEPOT_USAGE_WORK_EFFORT
                : depotSelector            
        );
    }
    
    //-------------------------------------------------------------------------
    public void removeWorkRecord(
        Path workRecordIdentity
    ) throws ServiceException {
        DataproviderObject_1_0 workRecord = this.backend.retrieveObjectFromDelegation(workRecordIdentity);
        if(!workRecord.values("workCb").isEmpty()) {
            this.backend.getDepots().removeCompoundBooking(
                (Path)workRecord.values("workCb").get(0)
            );
        }
        this.backend.removeObject(
            workRecordIdentity
        );
    }
    
    //-------------------------------------------------------------------------
    public void removeActivityGroup(
        Path activityGroupIdentity
    ) throws ServiceException {
        List activities = this.backend.getDelegatingRequests().addFindRequest(
            activityGroupIdentity.getChild("filteredActivity"),
            null
        );
        // Don't allow removal if activity group has assigned activities
        if(activities.size() > 0) {
            throw new ServiceException(
                OpenCrxException.DOMAIN,
                OpenCrxException.ACTIVITY_GROUP_HAS_ASSIGNED_ACTIVITIES, 
                new BasicException.Parameter[]{
                    new BasicException.Parameter("param0", activityGroupIdentity)
                },
                "Activity group has assigned activities. Can not remove."
            );
        }
        else {
            this.backend.removeObject(
                activityGroupIdentity
            );
        }
    }
    
    //-------------------------------------------------------------------------
    public void updateActivity(
        DataproviderObject object,
        DataproviderObject_1_0 oldValues
    ) throws ServiceException {
        if(this.backend.isActivity(object)) {
            if(oldValues != null) {
                List votes = this.backend.getDelegatingRequests().addFindRequest(
                    object.path().getChild("vote"),
                    null,
                    AttributeSelectors.ALL_ATTRIBUTES,
                    0, Integer.MAX_VALUE, Directions.ASCENDING
                );
                object.clearValues("totalVotes").add(
                    new Integer(votes.size())
                );
            }
            if(oldValues == null) {
                // init dueBy on creation
                if((object.getValues("dueBy") == null) || object.values("dueBy").isEmpty()) {
                    object.clearValues("dueBy").add(MAX_DATE);
                }
                // init percentComplete on creation
                if((object.getValues("percentComplete") == null) || object.values("percentComplete").isEmpty()) {
                    object.clearValues("percentComplete").add(new Short((short)0));
                }
            }
            List<String> statusMessage = new ArrayList<String>();
            String ical = icalendar.mergeIcal(
                object,
                (String)object.values("ical").get(0),
                statusMessage
            );
            object.clearValues("ical").add(
                ical == null ? "" : ical
            );
        }
    }
        
    //-------------------------------------------------------------------------
    public DataproviderObject createResourceAssignment(
        DataproviderObject_1_0 activity,
        DataproviderObject_1_0 resource,
        short resourceOrder
    ) throws ServiceException {
        DataproviderObject resourceAssignment = new DataproviderObject(
            activity.path().getDescendant(new String[]{"assignedResource", this.backend.getUidAsString()})
        );
        resourceAssignment.values(SystemAttributes.OBJECT_CLASS).add(
            "org:opencrx:kernel:activity1:ResourceAssignment"
        );
        resourceAssignment.values("name").addAll(
            resource.values("name")
        );
        resourceAssignment.values("description").add(
            "#" + activity.values("activityNumber").get(0) + ": " + 
            (activity.values("name").size() > 0 ? activity.values("name").get(0) : "") 
        );
        resourceAssignment.values("resource").add(
            resource.path()
        );
        resourceAssignment.values("resourceRole").add(
            new Short((short)0)
        );
        resourceAssignment.values("resourceOrder").add(
            new Short(resourceOrder)
        );                    
        resourceAssignment.values("workDurationPercentage").add(
            new Short((short)100)
        );
        this.backend.createObject(
            resourceAssignment
        );
        return resourceAssignment;
    }
    
    //-------------------------------------------------------------------------
    public void reapplyActivityCreator(
        Path activityIdentity,
        ActivityCreator activityCreator
    ) throws ServiceException {
        if(activityCreator != null) {
            Path activityTypeIdentity = (Path)activityCreator.getActivityType().refGetPath();
            if(activityTypeIdentity != null) {
                DataproviderObject_1_0 activityType = this.backend.retrieveObjectFromDelegation(
                    activityTypeIdentity
                );
                DataproviderObject updatedActivity = this.backend.retrieveObjectForModification(
                    activityIdentity
                );
                // Type of activity must match defined activity class
                String activityClass = !activityType.values("activityClassName").isEmpty()
                    ? (String)activityType.values("activityClassName").get(0)                
                    : ACTIVITY_TYPES[((Number)activityType.values("activityClass").get(0)).intValue()];
                if(activityClass.equals(updatedActivity.values(SystemAttributes.OBJECT_CLASS).get(0))) {
                    // Replace owning groups. The owning groups of the activity is the
                    // the union of all owning groups of the assigned activity groups. 
                    // This way it is guaranteed that the activity can be viewed in all
                    // assigned activity groups.
                    List<ActivityGroup> activityGroups = activityCreator.getActivityGroup();
                    Set<Path> owningGroupIdentities = new HashSet<Path>();
                    for(ActivityGroup activityGroup: activityGroups) {
                        List<PrincipalGroup> owningGroups = activityGroup.getOwningGroup();
                        for(PrincipalGroup owningGroup: owningGroups) {
                            owningGroupIdentities.add(owningGroup.refGetPath());
                        }
                    }
                    updatedActivity.clearValues("owningGroup").addAll(
                        owningGroupIdentities
                    );
                    this.backend.flushObjectModifications(
                        this.backend.getServiceHeader()
                    );
                    
                    // Create GroupAssignments
                    // Remove already assigned activity groups from list to be added 
                    List existingGroupAssignments = this.backend.getDelegatingRequests().addFindRequest(
                        activityIdentity.getChild("assignedGroup"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );
                    List<Path> excludeActivityGroups = new ArrayList<Path>();
                    for(Iterator i = existingGroupAssignments.iterator(); i.hasNext(); ) {
                        DataproviderObject_1_0 existingGroupAssignment = (DataproviderObject_1_0)i.next();
                        if(!existingGroupAssignment.values("activityGroup").isEmpty()) {
                            excludeActivityGroups.add(
                                (Path)existingGroupAssignment.values("activityGroup").get(0)
                            );
                        }
                    }
                    // Add new group assignments
                    for(ActivityGroup activityGroup: activityGroups) {
                        if(!excludeActivityGroups.contains(activityGroup.refGetPath())) {
                            DataproviderObject activityGroupAssignment = new DataproviderObject(
                                activityIdentity.getDescendant(
                                    new String[]{"assignedGroup", this.backend.getUidAsString()}
                                )
                            );
                            activityGroupAssignment.values(SystemAttributes.OBJECT_CLASS).add(
                                "org:opencrx:kernel:activity1:ActivityGroupAssignment"
                            );
                            activityGroupAssignment.values("activityGroup").add(
                                activityGroup.refGetPath()
                            );
                            this.backend.createObject(
                                activityGroupAssignment
                            );
                        }
                    }            
                    // Create ResourceAssignments
                    List<Path> resourceIdentities = new ArrayList<Path>();
                    if(!activityCreator.getResource().isEmpty()) {
                        List<Resource> resources = activityCreator.getResource();
                        for(Resource resource: resources) {
                            resourceIdentities.add(resource.refGetPath());
                        }
                    }
                    else {
                        // Try to find resource matching the current user
                        List allResources = this.backend.getDelegatingRequests().addFindRequest(
                            activityIdentity.getPrefix(5).getChild("resource"),
                            new FilterProperty[]{
                                new FilterProperty(
                                    Quantors.THERE_EXISTS,
                                    "contact",
                                    FilterOperators.IS_IN,
                                    new Object[]{
                                        this.backend.getUserHomes().getUserHome(
                                            activityIdentity
                                        ).values("contact").get(0)
                                    }
                                )
                            }
                        );
                        if(!allResources.isEmpty()) {
                            resourceIdentities.add(
                                ((DataproviderObject_1_0)allResources.iterator().next()).path()
                            );
                        }
                    }
                    // Remove already assigned resources from list to be added 
                    List existingResourceAssignments = this.backend.getDelegatingRequests().addFindRequest(
                        activityIdentity.getChild("assignedResource"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );
                    List<Path> excludeResources = new ArrayList<Path>();
                    for(Iterator i = existingResourceAssignments.iterator(); i.hasNext(); ) {
                        DataproviderObject_1_0 existingResourceAssignment = (DataproviderObject_1_0)i.next();
                        if(!existingResourceAssignment.values("resource").isEmpty()) {
                            excludeResources.add(
                                (Path)existingResourceAssignment.values("resource").get(0)
                            );
                        }
                    }                    
                    int ii = 0;
                    for(Iterator i = resourceIdentities.iterator(); i.hasNext(); ii++) {
                        Path resourceIdentity = (Path)i.next();
                        if(!excludeResources.contains(resourceIdentity)) {
                            DataproviderObject_1_0 resource = this.backend.retrieveObjectFromDelegation(
                                resourceIdentity
                            );
                            this.createResourceAssignment(
                                updatedActivity,
                                resource,
                                (short)ii
                            );
                        }
                    }
                    // Create depot references
                    List existingDepotReferences = this.backend.getDelegatingRequests().addFindRequest(
                        activityIdentity.getChild("depotReference"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );
                    List<Short> excludesDepotUsages = new ArrayList<Short>();
                    for(Iterator i = existingDepotReferences.iterator(); i.hasNext(); ) {
                        DataproviderObject_1_0 existingDepotReference = (DataproviderObject_1_0)i.next();
                        excludesDepotUsages.add(
                            new Short(((Number)existingDepotReference.values("depotUsage").get(0)).shortValue())
                        );
                    }                    
                    List depotReferences = this.backend.getDelegatingRequests().addFindRequest(
                        activityCreator.refGetPath().getChild("depotReference"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );        
                    for(
                        Iterator i = depotReferences.iterator();
                        i.hasNext();
                    ) {
                        DataproviderObject_1_0 depotReference = (DataproviderObject_1_0)i.next();
                        if(!excludesDepotUsages.contains(new Short(((Number)depotReference.values("depotUsage").get(0)).shortValue()))) {
                            this.backend.getCloneable().cloneAndUpdateReferences(
                                depotReference,
                                activityIdentity.getChild("depotReference"),
                                null,
                                "",
                                true
                            );
                        }
                    }                            
                    // Create PropertySet
                    List existingPropertySets = this.backend.getDelegatingRequests().addFindRequest(
                        activityIdentity.getChild("propertySet"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );
                    List<String> excludePropertySets = new ArrayList<String>();
                    for(Iterator i = existingPropertySets.iterator(); i.hasNext(); ) {
                        DataproviderObject_1_0 existingPropertySet = (DataproviderObject_1_0)i.next();
                        excludePropertySets.add(
                            (String)existingPropertySet.values("name").get(0)
                        );
                    }                    
                    List propertySets = this.backend.getDelegatingRequests().addFindRequest(
                        activityCreator.refGetPath().getChild("propertySet"),
                        null,
                        AttributeSelectors.ALL_ATTRIBUTES,
                        0,
                        Integer.MAX_VALUE,
                        Directions.ASCENDING
                    );        
                    for(
                        Iterator i = propertySets.iterator();
                        i.hasNext();
                    ) {
                        DataproviderObject_1_0 propertySet = (DataproviderObject_1_0)i.next();
                        if(!excludePropertySets.contains(propertySet.values("name").get(0))) {
                            this.backend.getCloneable().cloneAndUpdateReferences(
                                propertySet,
                                activityIdentity.getChild("propertySet"),
                                null,
                                "property",
                                true
                            );
                        }
                    }                            
                    // Set processState, lastTransition
                    updatedActivity.clearValues("activityType").add(
                        activityTypeIdentity
                    );
                    updatedActivity.clearValues("processState");
                    updatedActivity.clearValues("lastTransition");
                    if(!activityType.values("controlledBy").isEmpty()) {
                        DataproviderObject_1_0 activityProcess = this.backend.retrieveObjectFromDelegation(
                            (Path)activityType.values("controlledBy").get(0)
                        );
                        // Try to find transition which most closely matches the current activity
                        // completeness and state. If no transition can be found set to start transition.
                        DataproviderObject_1_0 lastTransition = null;
                        Path processStateIdentity = null;
                        if(!updatedActivity.values("percentComplete").isEmpty()) {
                            List transitions = 
                                this.backend.getDelegatingRequests().addFindRequest(
                                    activityProcess.path().getChild("transition"),
                                    new FilterProperty[]{
                                        new FilterProperty(
                                            Quantors.THERE_EXISTS,
                                            "newPercentComplete",
                                            FilterOperators.IS_IN,
                                            new Object[]{updatedActivity.values("percentComplete").get(0)}
                                        )
                                    },
                                    AttributeSelectors.ALL_ATTRIBUTES,
                                    0,
                                    Integer.MAX_VALUE,
                                    Directions.ASCENDING
                                );
                            if(!transitions.isEmpty()) {
                                lastTransition = (DataproviderObject_1_0)transitions.iterator().next();
                                processStateIdentity = (Path)lastTransition.values("nextState").get(0);
                            }
                        }
                        if(
                            (lastTransition == null) &&
                            !updatedActivity.values("activityState").isEmpty()
                        ) {
                            List transitions = 
                                this.backend.getDelegatingRequests().addFindRequest(
                                    activityProcess.path().getChild("transition"),
                                    new FilterProperty[]{
                                        new FilterProperty(
                                            Quantors.THERE_EXISTS,
                                            "newActivityState",
                                            FilterOperators.IS_IN,
                                            new Object[]{updatedActivity.values("activityState").get(0)}
                                        )
                                    },
                                    AttributeSelectors.ALL_ATTRIBUTES,
                                    0,
                                    Integer.MAX_VALUE,
                                    Directions.ASCENDING
                                );
                            if(!transitions.isEmpty()) {
                                lastTransition = (DataproviderObject_1_0)transitions.iterator().next();
                                processStateIdentity = (Path)lastTransition.values("nextState").get(0);
                            }
                        }
                        if(lastTransition == null) {
                            lastTransition = null;
                            processStateIdentity = (Path)activityProcess.values("startState").get(0);
                        }
                        if(processStateIdentity != null) {
                            updatedActivity.values("processState").add(processStateIdentity);
                        }
                        if(lastTransition != null) {
                            updatedActivity.values("lastTransition").add(lastTransition.path());
                            updatedActivity.clearValues("percentComplete").addAll(lastTransition.values("newPercentComplete"));
                            updatedActivity.clearValues("activityState").addAll(lastTransition.values("newActivityState"));
                        }
                    }
                }
            }
        }
    }
    
    //-------------------------------------------------------------------------
    public FilterProperty[] getActivityFilterProperties(
        Path activityFilterIdentity,
        boolean forCounting
    ) throws ServiceException {
        List filterProperties = this.backend.getDelegatingRequests().addFindRequest(
            activityFilterIdentity.getChild("filterProperty"),
            null,
            AttributeSelectors.ALL_ATTRIBUTES,
            null,
            0, 
            Integer.MAX_VALUE,
            Directions.ASCENDING
        );
        List filter = new ArrayList();
        boolean hasQueryFilterClause = false;
        for(
            Iterator i = filterProperties.iterator();
            i.hasNext();
        ) {
            DataproviderObject_1_0 filterProperty = (DataproviderObject_1_0)i.next();
            String filterPropertyClass = (String)filterProperty.values(SystemAttributes.OBJECT_CLASS).get(0);

            Boolean isActive = (Boolean)filterProperty.values("isActive").get(0);
            
            if((isActive != null) && isActive.booleanValue()) {
                // Query filter
                if("org:opencrx:kernel:activity1:ActivityQueryFilterProperty".equals(filterPropertyClass)) {     
                    String queryFilterContext = SystemAttributes.CONTEXT_PREFIX + this.backend.getUidAsString() + ":";
                    // Clause and class
                    filter.add(
                        new FilterProperty(
                            Quantors.PIGGY_BACK,
                            queryFilterContext + Database_1_Attributes.QUERY_FILTER_CLAUSE,
                            FilterOperators.PIGGY_BACK,
                            new Object[]{
                                (forCounting ? Database_1_Attributes.HINT_COUNT : "") +
                                filterProperty.values("clause").get(0)
                            }
                        )
                    );
                    filter.add(
                        new FilterProperty(
                            Quantors.PIGGY_BACK,
                            queryFilterContext + SystemAttributes.OBJECT_CLASS,
                            FilterOperators.PIGGY_BACK,
                            new Object[]{Database_1_Attributes.QUERY_FILTER_CLASS}
                        )
                    );
                    // stringParam
                    List values = filterProperty.values(Database_1_Attributes.QUERY_FILTER_STRING_PARAM);
                    filter.add(
                        new FilterProperty(
                            Quantors.PIGGY_BACK,
                            queryFilterContext + Database_1_Attributes.QUERY_FILTER_STRING_PARAM,
                            FilterOperators.PIGGY_BACK,
                            values.toArray(new String[values.size()])
                        )
                    );
                    // integerParam
                    values = filterProperty.values(Database_1_Attributes.QUERY_FILTER_INTEGER_PARAM);
                    filter.add(
                        new FilterProperty(
                            Quantors.PIGGY_BACK,
                            queryFilterContext + Database_1_Attributes.QUERY_FILTER_INTEGER_PARAM,
                            FilterOperators.PIGGY_BACK,
                            values.toArray(new Integer[values.size()])
                        )
                    );
                    // decimalParam
                    values = filterProperty.values(Database_1_Attributes.QUERY_FILTER_DECIMAL_PARAM);
                    filter.add(
                        new FilterProperty(
                            Quantors.PIGGY_BACK,
                            queryFilterContext + Database_1_Attributes.QUERY_FILTER_DECIMAL_PARAM,
                            FilterOperators.PIGGY_BACK,
                            values.toArray(new BigDecimal[values.size()])
                        )
                    );
                    // booleanParam
                    values = filterProperty.values(Database_1_Attributes.QUERY_FILTER_BOOLEAN_PARAM);
                    filter.add(
                        new FilterProperty(
                            Quantors.PIGGY_BACK,
                            queryFilterContext + Database_1_Attributes.QUERY_FILTER_BOOLEAN_PARAM,
                            FilterOperators.PIGGY_BACK,
                            values.toArray(new Boolean[values.size()])
                        )
                    );
                    // dateParam
                    values = filterProperty.values(Database_1_Attributes.QUERY_FILTER_DATE_PARAM);
                    filter.add(
                        new FilterProperty(
                            Quantors.PIGGY_BACK,
                            queryFilterContext + Database_1_Attributes.QUERY_FILTER_DATE_PARAM,
                            FilterOperators.PIGGY_BACK,
                            values.toArray(new XMLGregorianCalendar[values.size()])
                        )
                    );
                    // dateTimeParam
                    values = filterProperty.values(Database_1_Attributes.QUERY_FILTER_DATETIME_PARAM);
                    filter.add(
                        new FilterProperty(
                            Quantors.PIGGY_BACK,
                            queryFilterContext + Database_1_Attributes.QUERY_FILTER_DATETIME_PARAM,
                            FilterOperators.PIGGY_BACK,
                            values.toArray(new Date[values.size()])
                        )
                    );
                    hasQueryFilterClause = true;
                }
                // Attribute filter
                else {
                    // Get filterOperator, filterQuantor
                    short filterOperator = filterProperty.values("filterOperator").size() == 0
                        ? FilterOperators.IS_IN
                        : ((Number)filterProperty.values("filterOperator").get(0)).shortValue();
                    filterOperator = filterOperator == 0
                        ? FilterOperators.IS_IN
                        : filterOperator;
                    short filterQuantor = filterProperty.values("filterQuantor").size() == 0
                        ? Quantors.THERE_EXISTS
                        : ((Number)filterProperty.values("filterQuantor").get(0)).shortValue();
                    filterQuantor = filterQuantor == 0
                        ? Quantors.THERE_EXISTS
                        : filterQuantor;
                    
                    if("org:opencrx:kernel:activity1:ActivityStateFilterProperty".equals(filterPropertyClass)) {
                        filter.add(
                            new FilterProperty(
                                filterQuantor,
                                "activityState",
                                filterOperator,
                                filterProperty.values("activityState").toArray()
                            )
                        );
                    }
                    else if("org:opencrx:kernel:activity1:ScheduledStartFilterProperty".equals(filterPropertyClass)) {
                        if(filterProperty.values("scheduledStart").isEmpty()) {
                            filterProperty.values("scheduledStart").add(
                                DateFormat.getInstance().format(new Date())
                            );
                        }
                        if(!filterProperty.values("offsetInHours").isEmpty()) {
                            int offsetInHours = ((Number)filterProperty.values("offsetInHours").get(0)).intValue();
                            for(int j = 0; j < filterProperty.values("scheduledStart").size(); j++) {
                                try {
                                    GregorianCalendar date = new GregorianCalendar();
                                    date.setTime(
                                        DateFormat.getInstance().parse((String)filterProperty.values("scheduledStart").get(j))
                                    );
                                    date.add(GregorianCalendar.HOUR_OF_DAY, offsetInHours);
                                    filterProperty.values("scheduledStart").set(
                                        j, 
                                        DateFormat.getInstance().format(date.getTime())
                                    );
                                } catch(Exception e) {}
                            }
                        }
                        filter.add(
                            new FilterProperty(
                                filterQuantor,
                                "scheduledStart",
                                filterOperator,
                                filterProperty.values("scheduledStart").toArray()
                            )
                        );
                    }
                    else if("org:opencrx:kernel:activity1:ScheduledEndFilterProperty".equals(filterPropertyClass)) {
                        if(filterProperty.values("scheduledEnd").isEmpty()) {
                            filterProperty.values("scheduledEnd").add(
                                DateFormat.getInstance().format(new Date())
                            );
                        }
                        if(!filterProperty.values("offsetInHours").isEmpty()) {
                            int offsetInHours = ((Number)filterProperty.values("offsetInHours").get(0)).intValue();
                            for(int j = 0; j < filterProperty.values("scheduledEnd").size(); j++) {
                                try {
                                    GregorianCalendar date = new GregorianCalendar();
                                    date.setTime(
                                        DateFormat.getInstance().parse((String)filterProperty.values("scheduledEnd").get(j))
                                    );
                                    date.add(GregorianCalendar.HOUR_OF_DAY, offsetInHours);
                                    filterProperty.values("scheduledEnd").set(
                                        j, 
                                        DateFormat.getInstance().format(date.getTime())
                                    );
                                } catch(Exception e) {}
                            }
                        }
                        filter.add(
                            new FilterProperty(
                                filterQuantor,
                                "scheduledEnd",
                                filterOperator,
                                filterProperty.values("scheduledEnd").toArray()
                            )
                        );
                    }
                    else if("org:opencrx:kernel:activity1:ActivityProcessStateFilterProperty".equals(filterPropertyClass)) {
                        filter.add(
                            new FilterProperty(
                                filterQuantor,
                                "processState",
                                filterOperator,
                                filterProperty.values("processState").toArray()
                            )                    
                        );
                    }
                    else if("org:opencrx:kernel:activity1:ActivityTypeFilterProperty".equals(filterPropertyClass)) {
                        filter.add(
                            new FilterProperty(
                                filterQuantor,
                                "activityType",
                                filterOperator,
                                filterProperty.values("activityType").toArray()
                            )
                        );
                    }
                    else if("org:opencrx:kernel:activity1:AssignedToFilterProperty".equals(filterPropertyClass)) {
                        filter.add(
                            new FilterProperty(
                                filterQuantor,
                                "assignedTo",
                                filterOperator,
                                filterProperty.values("contact").toArray()
                            )
                        );
                    }
                    else if("org:opencrx:kernel:activity1:ActivityNumberFilterProperty".equals(filterPropertyClass)) {
                        filter.add(
                            new FilterProperty(
                                filterQuantor,
                                "activityNumber",
                                filterOperator,
                                filterProperty.values("activityNumber").toArray()
                            )
                        );
                    }
                    else if("org:opencrx:kernel:activity1:DisabledFilterProperty".equals(filterPropertyClass)) {
                        filter.add(
                            new FilterProperty(
                                filterQuantor,
                                "disabled",
                                filterOperator,
                                filterProperty.values("disabled").toArray()
                            )
                        );
                    }
                }
            }
        }
        if(!hasQueryFilterClause && forCounting) {
            String queryFilterContext = SystemAttributes.CONTEXT_PREFIX + this.backend.getUidAsString() + ":";
            // Clause and class
            filter.add(
                new FilterProperty(
                    Quantors.PIGGY_BACK,
                    queryFilterContext + Database_1_Attributes.QUERY_FILTER_CLAUSE,
                    FilterOperators.PIGGY_BACK,
                    new Object[]{
                        Database_1_Attributes.HINT_COUNT + "(1=1)"
                    }
                )
            );
            filter.add(
                new FilterProperty(
                    Quantors.PIGGY_BACK,
                    queryFilterContext + SystemAttributes.OBJECT_CLASS,
                    FilterOperators.PIGGY_BACK,
                    new Object[]{Database_1_Attributes.QUERY_FILTER_CLASS}
                )
            );            
        }
        return (FilterProperty[])filter.toArray(new FilterProperty[filter.size()]);
    }
    
    //-------------------------------------------------------------------------
    public DataproviderObject_1_0 findWorkingDay(
        Path calendarIdentity,
        GregorianCalendar dateOfDayAsCal
    ) throws ServiceException {
        int year = dateOfDayAsCal.get(GregorianCalendar.YEAR);
        int month = dateOfDayAsCal.get(GregorianCalendar.MONTH) + 1;
        int day = dateOfDayAsCal.get(GregorianCalendar.DAY_OF_MONTH);
        String dateOfDay = "" +
            year +
            (month < 10 ? "0" : "") + month +
            (day < 10 ? "0" : "") + day;
        // Test for CalendarDay
        List days = 
            this.backend.getDelegatingRequests().addFindRequest(
                calendarIdentity.getChild("calendarDay"),
                new FilterProperty[]{
                    new FilterProperty(
                        Quantors.THERE_EXISTS,
                        "dateOfDay",
                        FilterOperators.IS_IN,
                        new String[]{dateOfDay}
                    )
                },
                AttributeSelectors.ALL_ATTRIBUTES,
                0,
                Integer.MAX_VALUE,
                Directions.ASCENDING
            );
        if(days.size() > 0) {
            return (DataproviderObject_1_0)days.iterator().next();
        }
        // Test for WeekDay. Sunday = 1
        int dayOfWeek = 
            dateOfDayAsCal.get(GregorianCalendar.DAY_OF_WEEK) - dateOfDayAsCal.getFirstDayOfWeek() + 1;
        days = 
            this.backend.getDelegatingRequests().addFindRequest(
                calendarIdentity.getChild("weekDay"),
                new FilterProperty[]{
                    new FilterProperty(
                        Quantors.THERE_EXISTS,
                        "dayOfWeek",
                        FilterOperators.IS_IN,
                        new Number[]{new Short((short)dayOfWeek)}
                    )
                },
                AttributeSelectors.ALL_ATTRIBUTES,
                0,
                Integer.MAX_VALUE,
                Directions.ASCENDING
            );
        if(days.size() > 0) {
            return (DataproviderObject_1_0)days.iterator().next();
        }     
        // Not found. Lookup in base calendar
        DataproviderObject_1_0 calendar = this.backend.retrieveObjectFromDelegation(
            calendarIdentity
        );
        if(calendar.values("baseCalendar").size() > 0) {
            return this.findWorkingDay(
                (Path)calendar.values("baseCalendar").get(0),
                dateOfDayAsCal
            );
        }
        return null;
    }
    
    //-------------------------------------------------------------------------
    public void assignTo(
        Path activityIdentity,
        Path resourceIdentity
    ) throws ServiceException {
        if(resourceIdentity != null) {
            DataproviderObject activity = this.backend.retrieveObjectForModification(
                activityIdentity
            );
            DataproviderObject_1_0 resource = this.backend.retrieveObjectFromDelegation(
                resourceIdentity
            );
            Path contactIdentity = (Path)resource.values("contact").get(0);
            activity.clearValues("assignedTo").add(
                contactIdentity
            );
        }
    }

    //-------------------------------------------------------------------------
    public void updateIcal(
        Path activityIdentity
    ) throws ServiceException {
        List messages = new ArrayList();
        List errors = new ArrayList();
        List report = new ArrayList();
        String ical = this.icalendar.mergeIcal(
            this.backend.retrieveObject(activityIdentity),
            null, 
            messages
        );        
        byte[] item = null;
        try {
            item = ical.getBytes("UTF-8");
        } catch(Exception e) {
            item = ical.getBytes();    
        }
        this.icalendar.importItem(
            item, 
            activityIdentity, 
            (short)0, 
            errors, 
            report
        );
    }

    //-------------------------------------------------------------------------
    public void completeActualEffortForActivity(
        DataproviderObject_1_0 activity, 
        String filterAttribute,
        Set fetchSet
    ) throws ServiceException {        
        if(
            (fetchSet == null) || 
            fetchSet.contains("actualEffortHours") || 
            fetchSet.contains("actualEffortMinutes") ||
            fetchSet.contains("actualEffortHhMm")
        ) {        
            Path activityIdentity = null;
            if(!activity.values(SystemAttributes.OBJECT_IDENTITY).isEmpty()) {
                activityIdentity = new Path((String)activity.values(SystemAttributes.OBJECT_IDENTITY).get(0));
            }
            else {
                activityIdentity = activity.path();
            }            
            List workReportEntries = this.backend.getDelegatingRequests().addFindRequest(
                activityIdentity.getPrefix(5).getChild("workReportEntry"),
                new FilterProperty[]{
                    new FilterProperty(
                        Quantors.THERE_EXISTS,
                        filterAttribute,
                        FilterOperators.IS_IN,
                        new Object[]{activityIdentity.getPrefix(7)}                        
                    )
                },
                AttributeSelectors.SPECIFIED_AND_TYPICAL_ATTRIBUTES,
                0,
                Integer.MAX_VALUE,
                Directions.ASCENDING
                
            );
            BigDecimal actualEffortHours = new BigDecimal(0);
            BigDecimal actualEffortMinutes = new BigDecimal(0);
            for(
                Iterator i = workReportEntries.iterator();
                i.hasNext();
            ) {
                DataproviderObject workReportEntry = (DataproviderObject)i.next();
                actualEffortHours = actualEffortHours.add(
                    (BigDecimal)workReportEntry.values("durationHours").get(0)
                );
                actualEffortMinutes = actualEffortMinutes.add(
                    (BigDecimal)workReportEntry.values("durationMinutes").get(0)
                );
            }
            int hours = Math.abs(actualEffortHours.intValue() + (actualEffortMinutes.intValue() / 60));
            int minutes = Math.abs(actualEffortMinutes.intValue() % 60);
            boolean isNegative = 
                actualEffortHours.intValue() < 0 || 
                actualEffortMinutes.intValue() < 0;            
            activity.clearValues("actualEffortHours").add(
                new Integer(hours)
            );
            activity.clearValues("actualEffortMinutes").add(
                new Integer(minutes)
            );   
            activity.clearValues("actualEffortHhMm").add(
                (isNegative ? "-" : "") + hours + ":" + (minutes < 10 ? "0" + minutes : "" + minutes) + "'"
            );
        }     
    }
    
    //-------------------------------------------------------------------------
    /**
     * Calculates the actual effort for the activity group in hours and minutes.
     * @return array with actual effort hours at index 0 and minutes at index 1.
     */
    public int[] calcActualEffort(
        Path activityGroupIdentity
    ) throws ServiceException {        
        List workReportEntries = this.backend.getDelegatingRequests().addFindRequest(
            activityGroupIdentity.getChild("workReportEntry"),
            null,
            AttributeSelectors.ALL_ATTRIBUTES,
            0,
            Integer.MAX_VALUE,
            Directions.ASCENDING
            
        );
        BigDecimal actualEffortHours = new BigDecimal(0);
        BigDecimal actualEffortMinutes = new BigDecimal(0);
        for(
            Iterator i = workReportEntries.iterator();
            i.hasNext();
        ) {
            DataproviderObject workReportEntry = (DataproviderObject)i.next();
            actualEffortHours = actualEffortHours.add(
                (BigDecimal)workReportEntry.values("durationHours").get(0)
            );
            actualEffortMinutes = actualEffortMinutes.add(
                (BigDecimal)workReportEntry.values("durationMinutes").get(0)
            );
        }
        return new int[]{
            Math.abs(actualEffortHours.intValue() + (actualEffortMinutes.intValue() / 60)),                    
            Math.abs(actualEffortMinutes.intValue() % 60)
        };
    }
    
    //-------------------------------------------------------------------------
    public void completeMainEffortEstimate(
        DataproviderObject_1_0 activity, 
        Set fetchSet
    ) throws ServiceException {        
        if(
            (fetchSet == null) || 
            fetchSet.contains("mainEstimateEffortHours") || 
            fetchSet.contains("mainEstimateEffortMinutes") ||
            fetchSet.contains("mainEstimateEffortHhMm")
        ) {     
            Path activityIdentity = null;
            if(!activity.values(SystemAttributes.OBJECT_IDENTITY).isEmpty()) {
                activityIdentity = new Path((String)activity.values(SystemAttributes.OBJECT_IDENTITY).get(0));
            }
            else {
                activityIdentity = activity.path();
            }
            List estimates = this.backend.getDelegatingRequests().addFindRequest(
                activityIdentity.getChild("effortEstimate"),
                null,
                AttributeSelectors.ALL_ATTRIBUTES,
                0,
                Integer.MAX_VALUE,
                Directions.ASCENDING
                
            );
            BigDecimal estimateEffortHours = new BigDecimal(0);
            BigDecimal effortEstimateMinutes = new BigDecimal(0);
            for(
                Iterator i = estimates.iterator();
                i.hasNext();
            ) {
                DataproviderObject estimate = (DataproviderObject)i.next();
                if(
                    !estimate.values("isMain").isEmpty() &&
                    ((Boolean)estimate.values("isMain").get(0)).booleanValue()
                ) {
                    estimateEffortHours = (BigDecimal)estimate.values("estimateEffortHours").get(0);
                    effortEstimateMinutes = (BigDecimal)estimate.values("estimateEffortMinutes").get(0);    
                    break;
                }
            }
            int hours = Math.abs(estimateEffortHours.intValue() + (effortEstimateMinutes.intValue() / 60));
            int minutes = Math.abs(effortEstimateMinutes.intValue() % 60);
            boolean isNegative = 
                estimateEffortHours.intValue() < 0 || 
                effortEstimateMinutes.intValue() < 0;
            activity.clearValues("mainEstimateEffortHours").add(
                new Integer(hours)
            );
            activity.clearValues("mainEstimateEffortMinutes").add(
                new Integer(minutes)
            );   
            activity.clearValues("mainEstimateEffortHhMm").add(
                (isNegative ? "-" : "") + hours + ":" + (minutes < 10 ? "0" + minutes : "" + minutes) + "'"
            );
        }     
    }
    
    //-------------------------------------------------------------------------
    public List completeActivity(
        DataproviderObject_1_0 activity, 
        Set fetchSet
    ) throws ServiceException {        
        this.completeActualEffortForActivity(
            activity,
            "activity",
            fetchSet
        );
        this.completeMainEffortEstimate(
            activity,
            fetchSet
        );
        return Collections.EMPTY_LIST;
    }
    
    //-------------------------------------------------------------------------
    public int countFilteredActivity(
        Path activityFilterIdentity
    ) throws ServiceException {
        List activities = this.backend.getDelegatingRequests().addFindRequest(
            activityFilterIdentity.getPrefix(5).getChild("activity"),
            this.getActivityFilterProperties(
            	activityFilterIdentity, 
                true
            ),
            AttributeSelectors.NO_ATTRIBUTES,
            null,
            0, 
            1,
            Directions.ASCENDING
        );
        return activities.size();
    }
    
    //-----------------------------------------------------------------------
    /**
     * Creates and adds an email recipient to the email activity.
     * 
     * @param emailActivity    The openCRX EMailActivity currently in process
     * @param address          The email address object
     * @param type             The address type (TO, CC, BCC)
     */
    public static void addEmailRecipient(
        PersistenceManager pm,
        Email emailActivity,
        EmailAddress address,
        Message.RecipientType type
    ) {
        Activity1Package activityPkg = Utils.getActivityPackage(pm);
        pm.currentTransaction().begin();
        EmailRecipient recipient = activityPkg.getEmailRecipient().createEmailRecipient();
        emailActivity.addEmailRecipient(
            false,
            UUIDs.getGenerator().next().toString(),
            recipient
        );
        recipient.setParty(address);
        if(Message.RecipientType.TO.toString().equalsIgnoreCase(type.toString())) {
            recipient.setPartyType(PARTY_TYPE_TO);
        }
        else if(Message.RecipientType.CC.toString().equalsIgnoreCase(type.toString())) {
            recipient.setPartyType(PARTY_TYPE_CC);
        }
        else if(Message.RecipientType.BCC.toString().equalsIgnoreCase(type.toString())) {
            recipient.setPartyType(PARTY_TYPE_BCC);
        }
        // 'copy' the email's owning groups
        recipient.getOwningGroup().addAll(
            emailActivity.getOwningGroup()
        );
        pm.currentTransaction().commit();
    }
    
    //-----------------------------------------------------------------------
    /**
     * Adds a Media object to the currently processed email activity with the
     * given content. This is how attachments of email messages can be imported.
     * 
     * @param rootPkg          The root package to be used for this request
     * @param emailActivity    The openCRX EMailActivity currently in process
     * @param content          The content of the media object, e.g. an
     *                          attachment
     */
    public static void addMedia(
        PersistenceManager pm,
        Email emailActivity,
        String contentType,
        String contentName,
        InputStream content
    ) throws IOException {
        pm.currentTransaction().begin();
        Media media = Utils.getGenericPackage(pm).getMedia().createMedia();
        emailActivity.addMedia(
            false,
            UUIDs.getGenerator().next().toString(),
            media
        );
        media.setContentName(contentName);
        media.setContentMimeType(contentType);    
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int b;
        while((b = content.read()) != -1) {
            bos.write(b);
        }
        bos.close();
        media.setContent(
            BinaryLargeObjects.valueOf(bos.toByteArray())
        );
        if(AppLog.isTraceOn()) {
            AppLog.trace("Media to add: " + content.toString());
        }
        // 'copy' the email's owning groups
        media.getOwningGroup().addAll(
            emailActivity.getOwningGroup()
        );
        pm.currentTransaction().commit();
    }
    
    //-----------------------------------------------------------------------
    /**
     * Adds an email recipient to the currently processed email activity if
     * the email message contains an email address which is contained in an
     * openCRX account. Email addresses for which no account can be found, are
     * recorded via a note attached to the email activity.
     * 
     * @param rootPkg                   The root package to be used for this request
     * @param providerName              The name of the current provider
     * @param segmentName               The name of the current segment
     * @param emailActivity             The EMailActivity currently in process
     * @param addresses                 A list of addresses
     * @param type                      The address type (TO, CC, BCC)
     */
    public static void addRecipientToEmailActivity(
        PersistenceManager pm,
        String providerName,
        String segmentName,
        Email emailActivity,
        String[] addresses,
        Message.RecipientType type,
        boolean caseInsensitiveAddressLookup
    ) {
        if (addresses == null || addresses.length == 0) {
            AppLog.trace("Message does not contain any recipient of type '" + type.toString() + "'");
        }
        Set<String> newAddresses = new HashSet(Arrays.asList(addresses));
        Collection<AbstractEmailRecipient> recipients = emailActivity.getEmailRecipient();
        for(AbstractEmailRecipient recipient: recipients) {
            if(recipient instanceof EmailRecipient) {
                EmailAddress address = (EmailAddress)((EmailRecipient)recipient).getParty();
                if((address != null) && (address.getEmailAddress() != null)) {
                    newAddresses.remove(address.getEmailAddress());
                }
            }
        }
        for(String address: newAddresses) {
            List emailAddresses = Accounts.lookupEmailAddress(
                pm,
                providerName,
                segmentName,
                address,
                caseInsensitiveAddressLookup
            );
            if(!emailAddresses.isEmpty()) {
                Activities.addEmailRecipient(
                    pm, 
                    emailActivity, 
                    (EmailAddress)emailAddresses.iterator().next(), 
                    type
                );
            }
        }
    }
    
    //-----------------------------------------------------------------------
    /**
     * Search email activity with the given external link, i.e. the given
     * message id.
     * 
     * @param rootPkg          The root package to be used for this request
     * @param providerName     The name of the current provider
     * @param segmentName      The name of the current segment
     * @param externalLink     The message id
     * @return                 A List of activities containing the message id
     */
    public static List<org.opencrx.kernel.activity1.jmi1.Activity> lookupEmailActivity(
        PersistenceManager pm,
        String providerName,
        String segmentName,
        String externalLink
    ) {
        if(externalLink == null) {
            return Collections.EMPTY_LIST;
        }
        else {
            EmailQuery query = Utils.getActivityPackage(pm).createEmailQuery();
            org.opencrx.kernel.activity1.jmi1.Segment activitySegment =
                Activities.getActivitySegment(
                    pm,
                    providerName,
                    segmentName
                );
            query.thereExistsExternalLink().equalTo(
                externalLink  
            );
            return activitySegment.getActivity(query);
        }
    }
        
    //-----------------------------------------------------------------------
    /**
     * @return Returns the activitySegment.
     */
    public static org.opencrx.kernel.activity1.jmi1.Segment getActivitySegment(
        PersistenceManager pm,
        String providerName,
        String segmentName
    ) {
        return (org.opencrx.kernel.activity1.jmi1.Segment) pm.getObjectById(
            "xri:@openmdx:org.opencrx.kernel.activity1/provider/"
            + providerName + "/segment/" + segmentName
        );
    }

    //-----------------------------------------------------------------------
    /**
     * Formats a text containing all the addresses of the different types
     * (i.e., TO, CC, BCC) to be attached to the email activity by a note
     * indicating whether an account containing the email address could be
     * found.
     * 
     * @param rootPkg          The root package to be used for this request
     * @param providerName     The name of the current provider
     * @param segmentName      The name of the current segment
     * @param mimeMsg          The email to be imported as openCRX EMailActivity
     * @return                 The formatted content for the 'Recipients' note
     */
    public static String getRecipientsAsNoteText(
        PersistenceManager pm,
        String providerName,
        String segmentName,
        String[] from,
        String[] to,
        String[] cc,
        String[] bcc,        
        boolean caseInsensitiveAddressLookup
    ) throws MessagingException {
        StringBuffer text = new StringBuffer();  

        // add 'FROM's to the note
        String addresses[] = from;
        for (int i = 0; i < addresses.length; i++) {
            List emailAddresses = Accounts.lookupEmailAddress(
                pm,
                providerName,
                segmentName,
                addresses[i],
                caseInsensitiveAddressLookup
            );
            text.append("FROM: " + addresses[i] + " ["
                + ((emailAddresses == null || emailAddresses.size() == 0) ? "UNMATCHED" : "MATCHED") + "]\n");
        }
  
        // add 'TO's to the note
        addresses = to;
        for (int i = 0; i < addresses.length; i++) {
            List emailAddresses = Accounts.lookupEmailAddress(
                pm,
                providerName,
                segmentName,
                addresses[i],
                caseInsensitiveAddressLookup
            );
            text.append("TO: " + addresses[i] + " ["
                + ((emailAddresses == null || emailAddresses.size() == 0) ? "UNMATCHED" : "MATCHED") + "]\n");
        }
  
        // add 'CC's to the note
        addresses = cc;
        for (int i = 0; i < addresses.length; i++) {
            List emailAddresses = Accounts.lookupEmailAddress(
                pm,
                providerName,
                segmentName,
                addresses[i],
                caseInsensitiveAddressLookup
            );
            text.append("CC: " + addresses[i] + " ["
                + ((emailAddresses == null || emailAddresses.size() == 0) ? "UNMATCHED" : "MATCHED") + "]\n");
        }
  
        // add 'BCC's to the note
        addresses = bcc;
        for (int i = 0; i < addresses.length; i++) {
            List emailAddresses = Accounts.lookupEmailAddress(
                pm,
                providerName,
                segmentName,
                addresses[i],
                caseInsensitiveAddressLookup
            );
            text.append("BCC: " + addresses[i] + " ["
                + ((emailAddresses == null || emailAddresses.size() == 0) ? "UNMATCHED" : "MATCHED") + "]\n");
        }
        return text.toString();
    }

    //-----------------------------------------------------------------------
    /**
     * Adds a note to the currently processed email activity.
     * 
     * @param rootPkg          The root package to be used for this request
     * @param emailActivity    The openCRX EMailActivity currently in process
     * @param title            The note's title
     * @param content          The note's content
     */
    public static void addNote(
        PersistenceManager pm,
        Email emailActivity,
        String title,
        String content
    ) {
        pm.currentTransaction().begin();
        Note note = Utils.getGenericPackage(pm).getNote().createNote();
        emailActivity.addNote(
            false,
            UUIDs.getGenerator().next().toString(),
            note
        );
        note.setTitle(title);
        note.setText(content);
        // 'copy' the email's owning groups
        note.getOwningGroup().addAll(
            emailActivity.getOwningGroup()
        );
        pm.currentTransaction().commit();
    }
    
    //-------------------------------------------------------------------------  
    /**
     * Extract the priority from the email message. Note that if no header
     * element is found this indicates a "normal" priority. Note that rfc822
     * does not define a standard header field for priority. The name of the
     * "priority" header field depends on your mail client used. "Importance"
     * with values high, normal and low "Priority" with values Urgent and
     * Non-Urgent "X-Priority" with values 1=high and 5=low These values are
     * mapped to:
     * <UL>
     * <LI>ACTIVITY_PRIORITY_LOW,
     * <LI>ACTIVITY_PRIORITY_NORMAL and
     * <LI>ACTIVITY_PRIORITY_HIGH
     * </UL>
     * respectively.
     * 
     * @return the subject of the message
     */
    public static short getMessagePriority(
        Message message
    ) throws MessagingException {
        String priority = "normal";
        short priorityAsShort = PRIORITY_NORMAL;
        String[] values = message.getHeader("Importance");
        if (values != null && values.length > 0) {
            priority = values[0];
        }
        values = message.getHeader("X-Priority");
        if (values != null && values.length > 0) {
            priority = values[0];
        }
        values = message.getHeader("Priority");
        if (values != null && values.length > 0) {
            priority = values[0];
        }
        if (priority.equalsIgnoreCase("normal") || priority.equalsIgnoreCase("3")) {
            priorityAsShort = PRIORITY_NORMAL;
        } else if (priority.equalsIgnoreCase("high")
                || priority.equalsIgnoreCase("1")
                || priority.equalsIgnoreCase("Urgent")) {
            priorityAsShort = PRIORITY_HIGH;
        } else if (priority.equalsIgnoreCase("low")
                || priority.equalsIgnoreCase("5")
                || priority.equalsIgnoreCase("Non-Urgent")) {
            priorityAsShort = PRIORITY_LOW;
        }
        return priorityAsShort;
    }
    
    //-------------------------------------------------------------------------      
    public static String getMessageBody(
        MimeMessage mimeMessage
    ) throws IOException, MessagingException {
        Object content = mimeMessage.getContent();
        Part part = null;
        if(content instanceof MimeMultipart) {
            part = ((MimeMultipart)content).getBodyPart(0);
            content = part.getContent();
            // If body is itself multipart take body part as message body
            if(content instanceof MimeMultipart) {
                part = ((MimeMultipart)content).getBodyPart(0);
                content = part.getContent();                
            }
        }
        else {
            part = mimeMessage;
        }
        String contentType = part.getContentType();
        if(content instanceof String) {
            return (String)content;
        }
        else if (content instanceof InputStream) {
            contentType = part.getContentType();
            if(part.isMimeType("text/plain") || part.isMimeType("text/html")) {
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    part.getInputStream())
                );
                StringBuffer body = new StringBuffer();
                while (in.ready()) {
                    body.append(in.readLine());
                    if(in.ready()) {
                        body.append(System.getProperty("line.separator", "\n"));
                    }
                }
                return body.toString();
            }
            else {
                return "";
            }
        }
        return null;
    }

    //-------------------------------------------------------------------------
    public static boolean isAllAscii(
        String s
    ) {
        int ascii = 0, nonAscii = 0;
        int l = s.length();
        for(int i = 0; i < l; i++) {
            char c = s.charAt(i);
            boolean isNonAscii = (c >= 0177) || (c < 040 && c != '\r' && c != '\n' && c != '\t');
            if(isNonAscii) {
                nonAscii++;
            }
            else {
                ascii++;
            }
        }        
        return nonAscii == 0;
    }
    
    //-------------------------------------------------------------------------
    public static void mapMessageContent(
        org.opencrx.kernel.activity1.jmi1.Email emailActivity,
        Message message
    ) throws MessagingException {
        Multipart multipart = new MimeMultipart();
        MimeBodyPart messageBodyPart = new MimeBodyPart();
        String text = emailActivity.getMessageBody();
        text = text == null
            ? ""
            : text;
        if(text.startsWith("<!DOCTYPE html")) {
            String charset = null;
            if (!isAllAscii(text)) {
                charset = MimeUtility.getDefaultJavaCharset();
            }
            else {
                charset = "us-ascii";                
            }
            messageBodyPart.setContent(
                text, 
                "text/html; charset=" + MimeUtility.quote(charset, HeaderTokenizer.MIME)
            );        
        }
        else {
            messageBodyPart.setText(text);            
        }
        multipart.addBodyPart(messageBodyPart);    
        Collection<org.opencrx.kernel.generic.jmi1.Media> medias = emailActivity.getMedia();
        for(org.opencrx.kernel.generic.jmi1.Media media: medias) {
            if(media.getContentName() != null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try {
                        InputStream is = media.getContent().getContent();
                        int b;
                        while((b = is.read()) != -1) {
                            bos.write(b);
                        }
                    }
                    catch(Exception e) {
                        AppLog.warning("Unable to get media content (see detail for more info)", e.getMessage());
                        AppLog.info(e.getMessage(), e.getCause(), 1);
                    }
                    bos.close();
                    InternetHeaders headers = new InternetHeaders();
                    headers.addHeader("Content-Type", media.getContentMimeType() + "; name=\"" + media.getContentName() + "\"");
                    headers.addHeader("Content-Disposition", "attachment");
                    headers.addHeader("Content-Transfer-Encoding", "base64");
                    messageBodyPart = new MimeBodyPart(                        
                        headers,
                        org.openmdx.base.text.conversion.Base64.encode(bos.toByteArray()).getBytes("US-ASCII")
                    );
                    multipart.addBodyPart(messageBodyPart);
                }
                catch(Exception e) {
                    new ServiceException(e).log();
                }
            }
        }
        message.setContent(multipart);
    }
    
    //-------------------------------------------------------------------------
    public static List<Address> mapMessageRecipients(
        org.opencrx.kernel.activity1.jmi1.Email emailActivity,
        Message message            
    ) throws AddressException, MessagingException {
        List<Address> recipients = new ArrayList<Address>();        
        if(emailActivity.getSender() != null) {
            Address sender = new InternetAddress(
                emailActivity.getSender().getEmailAddress()
            );
            message.setFrom(sender);
        }
        for(Iterator i = emailActivity.getEmailRecipient().iterator(); i.hasNext(); ) {
            EmailRecipient recipient = (EmailRecipient)i.next();
            RecipientType recipientType = null;
            if(recipient.getPartyType() == PARTY_TYPE_TO) {
                recipientType = RecipientType.TO;
            }
            else if(recipient.getPartyType() == PARTY_TYPE_CC) {
                recipientType = RecipientType.CC;
            }
            else if(recipient.getPartyType() == PARTY_TYPE_BCC) {
                recipientType = RecipientType.BCC;
            }
            if(recipientType != null) {
                try {
                    Address to = new InternetAddress(
                        recipient.getParty().getEmailAddress()
                    );
                    recipients.add(to);
                    message.addRecipient(
                        recipientType,
                        to
                    );
                }
                catch(Exception e) {
                    AppLog.warning("Email has invalid recipients", emailActivity.getActivityNumber());
                }
            }
        }        
        return recipients;        
    }
    
    //-------------------------------------------------------------------------
    public static Message mapToMessage(
        org.opencrx.kernel.activity1.jmi1.Email emailActivity,
        Message message
    ) throws MessagingException {
        try {
            mapMessageContent(emailActivity, message);
        }
        catch(Exception e) {
            new ServiceException(e).log();
        }
        try {
            mapMessageRecipients(emailActivity, message);
        }
        catch(Exception e) {
            new ServiceException(e).log();
        }        
        if(emailActivity.getMessageSubject() != null) {
            message.setSubject(emailActivity.getMessageSubject());
        }
        SimpleDateFormat dateFormatter = (SimpleDateFormat)SimpleDateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, new Locale("US"));
        dateFormatter.applyPattern("EEE, dd MMM yyyy HH:mm:ss Z");
        message.setHeader(
            "Date", 
            dateFormatter.format(
                emailActivity.getSendDate() != null 
                    ? emailActivity.getSendDate()
                    : emailActivity.getCreatedAt()
            )
        );
        return message;
    }
        
    //-------------------------------------------------------------------------
    // Members
    //-------------------------------------------------------------------------
    public static final String MAX_DATE = "99991231T000000.000Z";

    private final static int BATCHING_MODE_SIZE = 1000;

    private static final String CODEVALUENAME_ACTIVITY_TYPE = "org:opencrx:kernel:activity1:ActivityType:activityClass";
  
    private static final String[] ACTIVITY_TYPES = 
        new String[]{
            "org:opencrx:kernel:activity1:EMail",
            "org:opencrx:kernel:activity1:Fax",
            "org:opencrx:kernel:activity1:Incident",
            "org:opencrx:kernel:activity1:Mailing",
            "org:opencrx:kernel:activity1:Meeting",
            "org:opencrx:kernel:activity1:Mms",
            "org:opencrx:kernel:activity1:PhoneCall",            
            "org:opencrx:kernel:activity1:Sms",            
            "org:opencrx:kernel:activity1:Task",
            "org:opencrx:kernel:activity1:Absence",
            "org:opencrx:kernel:activity1:ExternalActivity",
            "org:opencrx:kernel:activity1:SalesVisit"            
        };
    
    public static final short ACTIVITY_CLASS_EMAIL = 0;
    
    // PARTY_TYPE
    public final static short PARTY_TYPE_FROM = 210;
    public final static short PARTY_TYPE_TO = 220;
    public final static short PARTY_TYPE_CC = 230;
    public final static short PARTY_TYPE_BCC = 240;
    
    // LINK_TYPE
    public static final short ACTIVITY_LINK_TYPE_IS_DERIVED_FROM = 97;
    
    // DURATION_CALCULATION_MODE
    public static final short DURATION_CALCULATION_MODE_CALC_DURATION = 1;
    public static final short DURATION_CALCULATION_MODE_CALC_PAUSE = 2;
    
    // RATE_TYPE
    public static final short RATE_TYPE_NA = 0;
    public static final short RATE_TYPE_STANDARD = 1;
    public static final short RATE_TYPE_OVERTIME = 2;
    
    // PRIORITY
    public static final short PRIORITY_LOW = 1;
    public static final short PRIORITY_NORMAL = 2;
    public static final short PRIORITY_HIGH = 3;
        
    // Booking texts
    protected static final String BOOKING_TEXT_NAME_WORK_EFFORT = "work efforts";
    
    protected final Backend backend;
    protected final ICalendar icalendar;
        
}

//--- End of File -----------------------------------------------------------
