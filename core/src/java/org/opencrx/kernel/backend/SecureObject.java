/*
 * ====================================================================
 * Project:     opencrx, http://www.opencrx.org/
 * Name:        $Id: SecureObject.java,v 1.8 2007/12/26 22:41:47 wfro Exp $
 * Description: SecureObject
 * Revision:    $Revision: 1.8 $
 * Owner:       CRIXP AG, Switzerland, http://www.crixp.com
 * Date:        $Date: 2007/12/26 22:41:47 $
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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openmdx.application.log.AppLog;
import org.openmdx.base.exception.ServiceException;
import org.openmdx.compatibility.base.dataprovider.cci.AttributeSelectors;
import org.openmdx.compatibility.base.dataprovider.cci.AttributeSpecifier;
import org.openmdx.compatibility.base.dataprovider.cci.DataproviderObject;
import org.openmdx.compatibility.base.dataprovider.cci.DataproviderObject_1_0;
import org.openmdx.compatibility.base.dataprovider.cci.Directions;
import org.openmdx.compatibility.base.dataprovider.cci.SystemAttributes;
import org.openmdx.compatibility.base.marshalling.Marshaller;
import org.openmdx.compatibility.base.naming.Path;
import org.openmdx.model1.accessor.basic.cci.ModelElement_1_0;
import org.openmdx.model1.code.AggregationKind;

public class SecureObject {

    //-----------------------------------------------------------------------
    public SecureObject(
        Backend backend,
        DataproviderObject args
    ) {
        this(
            backend,
            (Path)args.values("user").get(0),
            (Path)args.values("group").get(0),
            ((Number)args.values("group").get(0)).shortValue(),
            ((Number)args.values("accessLevelBrowse").get(0)).shortValue(),
            ((Number)args.values("accessLevelUpdate").get(0)).shortValue(),
            ((Number)args.values("accessLevelDelete").get(0)).shortValue()
        );
    }
    
    //-----------------------------------------------------------------------
    public SecureObject(
        Backend backend,
        Path paramUserIdentity,
        Path paramGroupIdentity,
        Short paramMode,
        Short paramAccessLevelBrowse,
        Short paramAccessLevelUpdate,
        Short paramAccessLevelDelete
    ) {
        this.backend = backend;
        this.paramUserIdentity = paramUserIdentity;
        this.paramGroupIdentity = paramGroupIdentity;
        this.paramMode = paramMode;
        this.paramAccessLevelBrowse = paramAccessLevelBrowse;
        this.paramAccessLevelUpdate = paramAccessLevelUpdate;
        this.paramAccessLevelDelete = paramAccessLevelDelete;
    }
    
    //-------------------------------------------------------------------------
    public DataproviderObject_1_0 getUser(
        String principalName
    ) throws ServiceException {
        
        // principal
        DataproviderObject_1_0 principal = null;
        try {
            principal = this.backend.getDelegatingRequests().addGetRequest(
                this.backend.getRealmIdentity().getDescendant(new String[]{"principal", principalName}),
                AttributeSelectors.ALL_ATTRIBUTES,
                new AttributeSpecifier[]{}        
            );
        }
        catch(ServiceException e) {
            AppLog.warning("principal not found", principalName);
            e.log();
        }

        // user
        if((principal == null) || (principal.values("subject").size() == 0)) {
            AppLog.warning("user for principal not defined", principal);
            return null;
        }
        Path userIdentity = (Path)principal.values("subject").get(0);
        DataproviderObject_1_0 user = null;
        try {
            user = this.backend.getDelegatingRequests().addGetRequest(
                userIdentity,
                AttributeSelectors.ALL_ATTRIBUTES,
                new AttributeSpecifier[]{}        
            );
        }
        catch(ServiceException e) {
            AppLog.warning("user for principal not found", principal + "; user=" + userIdentity);
            e.log();
        }            
        return user;
    }
    
    //-----------------------------------------------------------------------
    public void applyAcls(
        DataproviderObject_1_0 obj,
        Marshaller marshaller,
        Short mode,
        String reportText,
        List report
    ) {
        try {
            // apply acls to obj
            DataproviderObject modifiedObj = this.backend.retrieveObjectForModification(
                obj.path()
            );
            modifiedObj.clearValues("owningUser").addAll(obj.values("owningUser"));
            modifiedObj.clearValues("owningGroup").addAll(obj.values("owningGroup"));            
            marshaller.marshal(modifiedObj);
            report.add(reportText);
           
            if((mode != null) && (mode.intValue() == MODE_RECURSIVE)) {
                // apply acls to object's content
                Map references = (Map)this.backend.getModel().getElement(
                    obj.values(SystemAttributes.OBJECT_CLASS).get(0)
                ).values("reference").get(0);
                for(
                    Iterator i = references.values().iterator();
                    i.hasNext();
                ) {
                    ModelElement_1_0 featureDef = (ModelElement_1_0)i.next();
                    ModelElement_1_0 referencedEnd = this.backend.getModel().getElement(
                        featureDef.values("referencedEnd").get(0)
                    );
                    if(
                        this.backend.getModel().isReferenceType(featureDef) &&
                        AggregationKind.COMPOSITE.equals(referencedEnd.values("aggregation").get(0)) &&
                        ((Boolean)referencedEnd.values("isChangeable").get(0)).booleanValue()
                    ) {
                        String reference = (String)featureDef.values("name").get(0);
                        Path referencePath = obj.path().getChild(reference);
                        List content = this.backend.getDelegatingRequests().addFindRequest(
                            referencePath,
                            null,
                            AttributeSelectors.ALL_ATTRIBUTES,
                            0,
                            Integer.MAX_VALUE,
                            Directions.ASCENDING
                        );
                        for(
                            Iterator j = content.iterator();
                            j.hasNext();
                        ) {
                            this.applyAcls(
                                (DataproviderObject)j.next(),
                                marshaller,
                                mode,
                                reportText,
                                report
                            );
                        }
                    }
                }
            }
        }
        catch(ServiceException e){
            e.log();
            report.add(e.getMessage());
        }
    }
    
    //-----------------------------------------------------------------------
    public void setOwningUser(
        Path secureObjectIdentity,
        List<String> report
    ) throws ServiceException {
        this.setOwningUser(
            this.backend.retrieveObject(
                secureObjectIdentity
            ),
            report
        );
    }
    
    //-----------------------------------------------------------------------
    public void setOwningUser(
        DataproviderObject_1_0 obj,
        List report
    ) throws ServiceException {        
        this.applyAcls(
            obj,
            new Marshaller() {
                public Object marshal(Object s) throws ServiceException {
                    if(s instanceof DataproviderObject) {
                        DataproviderObject obj = (DataproviderObject)s;
                        Path userIdentity = (Path)SecureObject.this.paramUserIdentity;
                        if((userIdentity == null) && (obj.values(SystemAttributes.CREATED_BY).size() > 0)) {
                            DataproviderObject_1_0 user = SecureObject.this.getUser((String)obj.values(SystemAttributes.CREATED_BY).get(0));
                            if(user != null) {
                                userIdentity = user.path();                
                            }
                        }
                        if(userIdentity != null) {
                            obj.clearValues("owningUser").add(userIdentity);
                        }
                    }
                    return s;
                }
                public Object unmarshal(Object s) {
                  throw new UnsupportedOperationException();
                }
            },
            this.paramMode,
            "setOwningUser",
            report
        );
    }
    
    //-----------------------------------------------------------------------
    public void addOwningGroup(
        Path secureObjectIdentity,
        List<String> report
    ) throws ServiceException {
        this.addOwningGroup(
            this.backend.retrieveObject(
                secureObjectIdentity
            ), 
            report
        );
    }
    
    //-----------------------------------------------------------------------
    public void addOwningGroup(
        DataproviderObject_1_0 obj,
        List<String> report
    ) throws ServiceException {        
        this.applyAcls(
            obj,
            new Marshaller() {
                public Object marshal(Object s) throws ServiceException {
                    if(s instanceof DataproviderObject) {
                        Path owningGroupIdentity = (Path)SecureObject.this.paramGroupIdentity;
                        if(owningGroupIdentity != null) {
                            List groups = ((DataproviderObject)s).values("owningGroup");
                            if(!groups.contains(owningGroupIdentity)) {
                                ((DataproviderObject)s).values("owningGroup").add(
                                    owningGroupIdentity
                                );
                            }
                        }
                    }
                    return s;
                }
                public Object unmarshal(Object s) {
                  throw new UnsupportedOperationException();
                }
            },
            this.paramMode,
            "addOwningGroup",
            report
        );
    }
            
    //-----------------------------------------------------------------------
    public void removeOwningGroup(
        Path secureObjectIdentity,
        List<String> report
    ) throws ServiceException {
        this.removeOwningGroup(
            this.backend.retrieveObject(
                secureObjectIdentity
            ), 
            report
        );
    }
        
    //-----------------------------------------------------------------------
    public void removeOwningGroup(
        DataproviderObject_1_0 obj,
        List<String> report
    ) throws ServiceException {        
        this.applyAcls(
            obj,
            new Marshaller() {
                public Object marshal(Object s) throws ServiceException {
                    if(s instanceof DataproviderObject) {
                        Path owningGroupIdentity = (Path)SecureObject.this.paramGroupIdentity;
                        if(owningGroupIdentity != null) {
                            ((DataproviderObject)s).values("owningGroup").remove(
                                owningGroupIdentity
                            );
                        }
                    }
                    return s;
                }
                public Object unmarshal(Object s) {
                  throw new UnsupportedOperationException();
                }
            },
            this.paramMode,
            "removeOwningGroup",
            report
        );
    }

    //-----------------------------------------------------------------------
    public void removeAllOwningGroup(
        Path secureObjectIdentity,
        List<String> report
    ) throws ServiceException {
        this.removeAllOwningGroup(
            this.backend.retrieveObject(
                secureObjectIdentity
            ), 
            report
        );
    }
            
    //-----------------------------------------------------------------------
    public void removeAllOwningGroup(
        DataproviderObject_1_0 obj,
        List<String> report
    ) throws ServiceException {        
        this.applyAcls(
            obj,
            new Marshaller() {
                public Object marshal(Object s) throws ServiceException {
                    if(s instanceof DataproviderObject) {
                        ((DataproviderObject)s).clearValues("owningGroup");
                    }
                    return s;
                }
                public Object unmarshal(Object s) {
                  throw new UnsupportedOperationException();
                }
            },
            this.paramMode,
            "removeAllOwningGroup",
            report
        );
    }
        
    //-----------------------------------------------------------------------
    public void setAccessLevel(
        Path secureObjectIdentity,
        List<String> report
    ) throws ServiceException {
        this.setAccessLevel(
            this.backend.retrieveObject(
                secureObjectIdentity
            ), 
            report
        );
    }
            
    //-----------------------------------------------------------------------
    public void setAccessLevel(
        DataproviderObject_1_0 obj,
        List report
    ) throws ServiceException {        
        this.applyAcls(
            obj,
            new Marshaller() {
                public Object marshal(Object s) throws ServiceException {
                    Number accessLevelBrowse = SecureObject.this.paramAccessLevelBrowse;
                    if((accessLevelBrowse != null) && (accessLevelBrowse.intValue() > 0)) {
                        ((DataproviderObject)s).clearValues("accessLevelBrowse").add(
                            accessLevelBrowse
                        );
                    }
                    Number accessLevelUpdate = SecureObject.this.paramAccessLevelUpdate;
                    if((accessLevelUpdate != null) && (accessLevelUpdate.intValue() > 0)) {
                        ((DataproviderObject)s).clearValues("accessLevelUpdate").add(
                            accessLevelUpdate
                        );
                    }
                    Number accessLevelDelete = SecureObject.this.paramAccessLevelDelete;
                    if((accessLevelDelete != null) && (accessLevelDelete.intValue() > 0)) {
                        ((DataproviderObject)s).clearValues("accessLevelDelete").add(
                            accessLevelDelete
                        );
                    }
                    return s;
                }
                public Object unmarshal(Object s) {
                  throw new UnsupportedOperationException();
                }
            },
            this.paramMode,
            "setAccessLevel",
            report
        );
    }
            
    //-------------------------------------------------------------------------
    // Members
    //-------------------------------------------------------------------------
    public static final int MODE_LOCAL = 0;
    public static final int MODE_RECURSIVE = 1;
    
    private final Backend backend;
    private final Path paramUserIdentity;
    private final Path paramGroupIdentity;
    private final Short paramAccessLevelBrowse;
    private final Short paramAccessLevelUpdate;
    private final Short paramAccessLevelDelete;
    private final Short paramMode;
    
}

//--- End of File -----------------------------------------------------------
