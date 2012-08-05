/*
 * ====================================================================
 * Project:     opencrx, http://www.opencrx.org/
 * Name:        $Id: Cloneable.java,v 1.12 2007/12/28 18:47:38 wfro Exp $
 * Description: Cloneable
 * Revision:    $Revision: 1.12 $
 * Owner:       CRIXP AG, Switzerland, http://www.crixp.com
 * Date:        $Date: 2007/12/28 18:47:38 $
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.openmdx.base.exception.ServiceException;
import org.openmdx.base.jmi1.BasicObject;
import org.openmdx.base.text.format.DateFormat;
import org.openmdx.compatibility.base.dataprovider.cci.AttributeSelectors;
import org.openmdx.compatibility.base.dataprovider.cci.DataproviderObject;
import org.openmdx.compatibility.base.dataprovider.cci.DataproviderObject_1_0;
import org.openmdx.compatibility.base.dataprovider.cci.Directions;
import org.openmdx.compatibility.base.dataprovider.cci.SystemAttributes;
import org.openmdx.compatibility.base.marshalling.Marshaller;
import org.openmdx.compatibility.base.naming.Path;
import org.openmdx.kernel.exception.BasicException;
import org.openmdx.model1.accessor.basic.cci.ModelElement_1_0;
import org.openmdx.model1.code.AggregationKind;

public class Cloneable {

    //-------------------------------------------------------------------------
    public Cloneable(
        Backend backend
    ) {
        this.backend = backend;
    }
    
    //-------------------------------------------------------------------------
    public BasicObject cloneAndUpdateReferences(
        Path originalIdentity,
        String referenceFilterAsString,
        boolean replaceExisting
    ) throws ServiceException {
        DataproviderObject clonedObject = this.cloneAndUpdateReferences(
            this.backend.retrieveObject(
                originalIdentity
            ), 
            originalIdentity.getParent(),
            null,
            referenceFilterAsString == null  
                ? DEFAULT_REFERENCE_FILTER
                : referenceFilterAsString, 
            replaceExisting
        );
        return clonedObject == null
            ? null
            : (BasicObject)this.backend.getDelegatingPkg().refObject(clonedObject.path().toXri());
    }
    
    //-------------------------------------------------------------------------
    /**
     * Model-driven object cloning. Copies the original object to the target
     * toContainer. If a corresponding marshaller is found, the orginal object
     * is marshalled before it is written to the target. The target is either
     * replaced or newly created. If cloneContent=true the operation is recursive,
     * i.e. the composite objects of original are cloned recursively. 
     */
    public DataproviderObject cloneAndUpdateReferences(
        DataproviderObject_1_0 original,
        Path toContainer,
        Map objectMarshallers,
        String referenceFilterAsString,
        boolean replaceExisting
    ) throws ServiceException {
        // Clone object
        List referenceFilter = referenceFilterAsString == null ? null : new ArrayList();
        if(referenceFilterAsString != null) {
            StringTokenizer tokenizer = new StringTokenizer(referenceFilterAsString, " ;,", false);
            while(tokenizer.hasMoreTokens()) {
                referenceFilter.add(
                    new Path(original.path().toXri() + "/" + tokenizer.nextToken())
                );
            }
        }        
        List replacements = new ArrayList();
        DataproviderObject cloned = this.cloneObject(
            original,
            toContainer,
            CLONE_EXCLUDE_ATTRIBUTES,
            objectMarshallers,
            referenceFilter,
            replacements,
            replaceExisting
        );
        // Update references
        referenceFilter = referenceFilterAsString == null ? null : new ArrayList();
        if(referenceFilterAsString != null) {
            StringTokenizer tokenizer = new StringTokenizer(referenceFilterAsString, " ;,", false);
            while(tokenizer.hasMoreTokens()) {
                referenceFilter.add(
                    new Path(cloned.path().toXri() + "/" + tokenizer.nextToken())
                );
            }
        }        
        this.applyReplacements(
            cloned.path(),
            true,
            replacements,
            referenceFilter
        );
        return cloned;
    }
    
    //-------------------------------------------------------------------------
    /**
     * Add the objects referenced by object to referencedObjectPaths if the
     * referenced object paths match the reference filter. Update the reference
     * filter for the referenced objects which match the reference filter.
     */
    public void collectReferencedObjects(
        DataproviderObject_1_0 object,
        List referenceFilter,
        Set referencedObjectPaths
    ) {
        for(
            Iterator i = object.attributeNames().iterator(); 
            i.hasNext(); 
        ) {
            String attributeName = (String)i.next();
            List values = object.values(attributeName);
            for(Iterator j = values.iterator(); j.hasNext(); ) {
                Object value = j.next();
                if(value instanceof Path) {
                    Path referencedObjectPath = (Path)value;
                    Path reference = object.path().getChild(attributeName);
                    // Only add objects which match the reference filter
                    boolean matches = referenceFilter == null;
                    Path matchingReferencePattern = null;
                    if(!matches) {
                        for(Iterator k = referenceFilter.iterator(); k.hasNext(); ) {
                            Path f = (Path)k.next();
                            if(reference.isLike(f) && !f.endsWith(new String[]{":*"})) { 
                                matchingReferencePattern = f;
                                matches = true;
                                break;
                            }
                        }
                    }
                    if(matches) {
                        referencedObjectPaths.add(referencedObjectPath);
                        List newReferenceFilter = new ArrayList();
                        if(referenceFilter != null) {
                            // Update the referenceFilter
                            for(Iterator k = referenceFilter.iterator(); k.hasNext(); ) {
                                Path f = (Path)k.next();
                                if(
                                    (f.size() > matchingReferencePattern.size()) &&
                                    f.startsWith(matchingReferencePattern)
                                ) {
                                    newReferenceFilter.add(
                                        referencedObjectPath.getDescendant(
                                            f.getSuffix(matchingReferencePattern.size()+1)
                                        )
                                    );
                                }
                            }
                            referenceFilter.addAll(newReferenceFilter);
                        }
                    }
                }
            }
        }
    }

    //-------------------------------------------------------------------------
    /**
     * Model-driven object cloning. Copies the original object to the target
     * toContainer. If a corresponding marshaller is found, the orginal object
     * is marshalled before it is written to the target. The target is either
     * replaced or newly created. If cloneContent=true the operation is recursive,
     * i.e. the composite objects of original are cloned recursively. 
     */
    private DataproviderObject cloneObject(
        DataproviderObject_1_0 original,
        Path toContainer,
        Set excludeAttributes,
        Map objectMarshallers,
        List referenceFilter,
        List replacements,
        boolean replaceExisting
    ) throws ServiceException {
        
        // Clone original
        String originalType = (String)original.values(SystemAttributes.OBJECT_CLASS).get(0);
        DataproviderObject clone = null;
        if((objectMarshallers != null) && (objectMarshallers.get(originalType) != null)) {
            clone = (DataproviderObject)((Marshaller)objectMarshallers.get(originalType)).marshal(
                original
            );
        }
        else {
            clone = new DataproviderObject(new Path(""));
            clone.addClones(
              original,
              true
            );
            // By default remove security settings of original. 
            // They will be set automatically by access control for the clone
            clone.attributeNames().remove("owningUser");
            clone.attributeNames().remove("owningGroup");
        }
        
        // Cloned object has same qualifier as original in case of replacement. Otherwise
        // try to keep qualifier if length is < MANUAL_QUALIFIER_THRESHOLD and toContainer
        // is different from original path
        clone.path().setTo(
            toContainer.getChild(
                replaceExisting
                    ? original.path().getBase()
                    : !original.path().startsWith(toContainer) && (original.path().getBase().length() < MANUAL_QUALIFIER_THRESHOLD) 
                        ? original.path().getBase() 
                        : this.backend.getUidAsString()
            )
        );
        // Create ReferenceReplacement. References to cloned objects must be updated
        DataproviderObject replacement = new DataproviderObject(new Path("xri:@openmdx:*"));
        replacement.values(SystemAttributes.OBJECT_CLASS).add("org:opencrx:kernel:base:ReferenceReplacement");
        replacement.values("oldReference").add(
            new Path((String)original.values(SystemAttributes.OBJECT_IDENTITY).get(0))
        );
        replacement.values("newReference").add(clone.path());
        replacements.add(replacement);        
        // Exclude attributes
        if(excludeAttributes != null) {
            clone.attributeNames().removeAll(excludeAttributes);
        }
        // Remove unknown and readonly features
        try {
            ModelElement_1_0 classDef = this.backend.getModel().getElement(originalType);
            for(
                Iterator i = clone.attributeNames().iterator();
                i.hasNext();
            ) {
                String featureName = (String)i.next();
                ModelElement_1_0 featureDef = this.backend.getModel().getFeatureDef(classDef, featureName, false);
                String qualifiedFeatureName = featureDef == null
                    ? null
                    : (String)featureDef.values("qualifiedName").get(0);
                if(
                    !(SystemAttributes.OBJECT_CLASS.equals(featureName) || CLONEABLE_READONLY_FEATURES.contains(qualifiedFeatureName)) &&
                    ((featureDef == null) || 
                    !((Boolean)featureDef.values("isChangeable").get(0)).booleanValue())
                ) {
                    i.remove();                    
                }                
            }            
        } catch(Exception e) {}
        
        // Either replace existing object with clone or create clone as new object 
        if(replaceExisting) {
            try {
                DataproviderObject existing = this.backend.retrieveObjectForModification(
                    clone.path()
                );
                existing.attributeNames().clear();
                existing.addClones(clone, true);
            }
            catch(ServiceException e) {
                // create object if not found
                if(e.getExceptionCode() == BasicException.Code.NOT_FOUND) {
                    this.backend.getDelegatingRequests().addCreateRequest(
                        clone
                    );                  
                }
            }
        }
        else {
            this.backend.getDelegatingRequests().addCreateRequest(
                clone
            );
        }
        // Clone content (shared and composite)
        Map references = (Map)this.backend.getModel().getElement(
            original.values(SystemAttributes.OBJECT_CLASS).get(0)
        ).values("reference").get(0);
        for(
            Iterator i = references.values().iterator();
            i.hasNext();
        ) {
            ModelElement_1_0 featureDef = (ModelElement_1_0)i.next();
            ModelElement_1_0 referencedEnd = this.backend.getModel().getElement(
                featureDef.values("referencedEnd").get(0)
            );
            boolean referenceIsCompositeAndChangeable = 
                this.backend.getModel().isReferenceType(featureDef) &&
                AggregationKind.COMPOSITE.equals(referencedEnd.values("aggregation").get(0)) &&
                ((Boolean)referencedEnd.values("isChangeable").get(0)).booleanValue();
            boolean referenceIsShared = 
                this.backend.getModel().isReferenceType(featureDef) &&
                AggregationKind.SHARED.equals(referencedEnd.values("aggregation").get(0));
            
            // Only navigate changeable references which are either 'composite' or 'shared'
            // Do not navigate references with aggregation 'none'.
            if(referenceIsCompositeAndChangeable || referenceIsShared) {
                String reference = (String)featureDef.values("name").get(0);
                Path referencePath = original.path().getChild(reference);
                boolean matches = referenceFilter == null;
                if(!matches) {
                    for(
                        Iterator k = referenceFilter.iterator(); 
                        k.hasNext(); 
                    ) {
                        Path f = (Path)k.next();
                        // Wildcard does only apply for composite references
                        if(referencePath.isLike(f) && (!f.endsWith(new String[]{":*"}) || referenceIsCompositeAndChangeable)) {
                            matches = true;
                            break;
                        }
                    }
                }
                if(matches) {                                    
                    List content = this.backend.getDelegatingRequests().addFindRequest(
                        original.path().getChild(reference),
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
                        DataproviderObject contained = (DataproviderObject)j.next();
                        Path containedIdentity = new Path((String)contained.values(SystemAttributes.OBJECT_IDENTITY).get(0));
                        this.cloneObject(
                            contained,
                            // Contained is a composite to original if its access path matches its identity
                            // In this case case add the clone of contained as child of clone. Otherwise
                            // add the clone of contained to its composite parent
                            contained.path().equals(containedIdentity)
                                ? clone.path().getChild(reference)
                                : containedIdentity.getParent(),
                            excludeAttributes,
                            objectMarshallers,
                            referenceFilter,
                            replacements,
                            replaceExisting
                        );
                    }
                }
            }
        }
        // Clone referenced objects
        Set referencedObjectIdentities = new HashSet();
        this.collectReferencedObjects(
            original,
            referenceFilter,
            referencedObjectIdentities  
        );
        for(
            Iterator i = referencedObjectIdentities.iterator(); 
            i.hasNext(); 
        ) {
            Path referencedObjectPath = (Path)i.next();
            DataproviderObject_1_0 referencedObject = null;
            try {
                referencedObject = this.backend.retrieveObject(referencedObjectPath);
            } catch(Exception e) {}
            if(referencedObject != null) {
                Path newReference = 
                    this.cloneObject(
                        this.backend.retrieveObject(referencedObjectPath),
                        referencedObjectPath.getParent(),
                        excludeAttributes,
                        objectMarshallers,
                        referenceFilter,
                        replacements,
                        replaceExisting
                    ).path();
                // Create ReferenceReplacement. To references to cloned objects must be updated
                replacement = new DataproviderObject(new Path("xri:@openmdx:*"));
                replacement.values(SystemAttributes.OBJECT_CLASS).add("org:opencrx:kernel:base:ReferenceReplacement");
                replacement.values("oldReference").add(referencedObjectPath);
                replacement.values("newReference").add(newReference);
                replacements.add(replacement);
            }
        }
        return clone;
    }

    //-------------------------------------------------------------------------
    public void deleteCompositeAndReferencedObjects(
        DataproviderObject_1_0 object,
        String referenceFilterAsString
    ) throws ServiceException {
        List referenceFilter = new ArrayList();
        if(referenceFilterAsString != null) {
            StringTokenizer tokenizer = new StringTokenizer(referenceFilterAsString, " ;,", false);
            while(tokenizer.hasMoreTokens()) {
                referenceFilter.add(
                    new Path(object.path().toXri() + "/" + tokenizer.nextToken())
                );
            }
        }
        this.deleteReferencedObjects(
            object,
            referenceFilter
        );        
        // Remove object and all composite objects (even composite not
        // member of referenceFilter)
        this.backend.removeObject(
            object.path()
        );
    }
    
    //-------------------------------------------------------------------------
    private void deleteReferencedObjects(
        DataproviderObject_1_0 object,
        List referenceFilter
    ) throws ServiceException {

        Path objectIdentity = object.path();
        
        // Remove composite (and their referenced) objects
        Map references = (Map)this.backend.getModel().getElement(
            object.values(SystemAttributes.OBJECT_CLASS).get(0)
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
                AggregationKind.COMPOSITE.equals(referencedEnd.values("aggregation").get(0))
            ) {
                String reference = (String)featureDef.values("name").get(0);
                if(!CLONE_EXCLUDE_COMPOSITE_REFERENCES.contains(reference)) {
                    Path referencePath = objectIdentity.getChild(reference);
                    boolean matches = referenceFilter == null;
                    if(!matches) {
                        for(
                            Iterator k = referenceFilter.iterator(); 
                            k.hasNext(); 
                        ) {
                            if(referencePath.isLike((Path)k.next())) {
                                matches = true;
                                break;
                            }
                        }
                    }
                    if(matches) {                
                        List content = this.backend.getDelegatingRequests().addFindRequest(
                            objectIdentity.getChild(reference),
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
                            DataproviderObject_1_0 composite = (DataproviderObject_1_0)j.next();
                            this.deleteReferencedObjects(
                                composite,
                                referenceFilter
                            );
                        }
                    }
                }
            }
        }
        
        // Remove referenced objects
        Set referencedObjectPaths = new HashSet();        
        this.collectReferencedObjects(
            object,
            referenceFilter,
            referencedObjectPaths  
        );
        for(
            Iterator i = referencedObjectPaths.iterator(); 
            i.hasNext(); 
        ) {
            Path referencedObject = (Path)i.next();
            try {
                this.backend.removeObject(
                    referencedObject
                );                
            } 
            // Don't care if removal of referenced objects fails
            catch(ServiceException e) {}
        }
    }
    
    //-------------------------------------------------------------------------
    public int applyReplacements(
        Path objectIdentity,
        boolean isChangeable,
        List replacements,
        String referenceFilterAsString
    ) throws ServiceException {
        List referenceFilter = new ArrayList();
        if(referenceFilterAsString != null) {
            StringTokenizer tokenizer = new StringTokenizer(referenceFilterAsString, " ;,", false);
            while(tokenizer.hasMoreTokens()) {
                referenceFilter.add(
                    new Path(objectIdentity.toXri() + "/" + tokenizer.nextToken())
                );
            }
        }
        return this.applyReplacements(
            objectIdentity,
            isChangeable,
            replacements,
            referenceFilter
        );
    }
    
    //-------------------------------------------------------------------------
    /**
     * Applies the replacements to object and its content including the referenced
     * objects specified by the reference filter.
     * @param isChangeable object is changeable. Replacements are applied to changeble
     *        objects only.
     * @param replacements list of TemplateReplacement
     * @param baseValues map of 'basedOn' attribute names/values.
     * 
     * @return number of replacements
     */
    private int applyReplacements(
        Path objectIdentity,
        boolean isChangeable,
        List replacements,
        List referenceFilter
    ) throws ServiceException {

        int numberOfReplacements = 0;
        DataproviderObject_1_0 object = null;
        try {
            object = this.backend.retrieveObject(objectIdentity);
        } catch(Exception e) {}
        if(object == null) return 0;
        
        if(isChangeable) {
            DataproviderObject replacedObject = null;
            for(
                Iterator i = object.attributeNames().iterator();
                i.hasNext();
            ) {
                String name = (String)i.next();
                Object oldValue = object.values(name).get(0);
                Object newValue = null;
                for(
                    Iterator j = replacements.iterator();
                    j.hasNext();
                ) {
                    DataproviderObject_1_0 replacement = (DataproviderObject_1_0)j.next();
                    String replacementType = (String)replacement.values(SystemAttributes.OBJECT_CLASS).get(0);
                    // matches?
                    boolean matches = replacement.getValues("name") != null
                        ? replacement.values("name").contains(name)
                        : true;
                    // StringReplacement
                    if(
                        matches &&
                        (oldValue instanceof String) && 
                        "org:opencrx:kernel:base:StringReplacement".equals(replacementType)
                    ) {
                        matches &= (replacement.getValues("oldString") != null) && (replacement.getValues("oldString").size() > 0) 
                            ? oldValue.equals(replacement.values("oldString").get(0))
                            : true;
                        newValue = replacement.values("newString").get(0);
                    }
                    // number
                    else if(
                        matches &&
                        (oldValue instanceof Comparable) && 
                        "org:opencrx:kernel:base:NumberReplacement".equals(replacementType)
                    ) {                    
                        matches &= (replacement.getValues("oldNumber") != null) && (replacement.getValues("oldNumber").size() > 0)
                            ? ((Comparable)oldValue).compareTo(replacement.values("oldNumber").get(0)) == 0
                            : true;
                        newValue = replacement.values("newNumber").get(0);
                    }
                    // DateTimeReplacement
                    else if(
                        matches &&
                        "org:opencrx:kernel:base:DateTimeReplacement".equals(replacementType)
                    ) {                    
                        matches &= (replacement.getValues("oldDateTime") != null) && (replacement.getValues("oldDateTime").size() > 0)
                            ? oldValue.equals(replacement.values("oldDateTime").get(0))
                            : true;
                        if(
                            (replacement.getValues("baseDateTime") != null) && 
                            (replacement.values("baseDateTime").size() > 0)
                        ) {
                            try {
                                DateFormat dateFormat = DateFormat.getInstance();                            
                                Date baseDate = dateFormat.parse((String)replacement.values("baseDateTime").get(0));
                                Date oldDate = dateFormat.parse((String)oldValue);
                                Date newDate = dateFormat.parse((String)replacement.values("newDateTime").get(0)); 
                                newValue = dateFormat.format(new Date(newDate.getTime() + (oldDate.getTime() - baseDate.getTime())));
                            }
                            catch(ParseException e) {
                                newValue = replacement.values("newDateTime").get(0);                                
                            }
                        }
                        else {
                            newValue = replacement.values("newDateTime").get(0);
                        }
                    }
                    // BooleanReplacement
                    else if(
                        matches &&
                        (oldValue instanceof Boolean) &&
                        "org:opencrx:kernel:base:BooleanReplacement".equals(replacementType)
                    ) {                    
                        matches &= (replacement.getValues("oldBoolean") != null) && (replacement.getValues("oldBoolean").size() > 0)
                            ? oldValue.equals(replacement.values("oldBoolean").get(0))
                            : true;
                        newValue = replacement.values("newBoolean").get(0);
                    }
                    // ReferenceReplacement
                    else if(
                        matches &&
                        (oldValue instanceof Path) &&
                        "org:opencrx:kernel:base:ReferenceReplacement".equals(replacementType)
                    ) {                    
                        matches &= (replacement.getValues("oldReference") != null) && (replacement.getValues("oldReference").size() > 0)
                            ? oldValue.equals(replacement.values("oldReference").get(0))
                            : true;
                        newValue = replacement.values("newReference").get(0);
                    }
                    else {
                        matches = false;
                    }
                    // Replace
                    if(matches) {
                        if(replacedObject == null) {
                            replacedObject = this.backend.retrieveObjectForModification(
                                object.path()
                            );
                        }
                        replacedObject.clearValues(name);
                        if(newValue != null) {
                            replacedObject.values(name).add(newValue);
                            numberOfReplacements++;
                        }
                    }
                }
            }
        }
            
        // Apply replacements to composite objects
        Map references = (Map)this.backend.getModel().getElement(
            object.values(SystemAttributes.OBJECT_CLASS).get(0)
        ).values("reference").get(0);
        for(
            Iterator i = references.values().iterator();
            i.hasNext();
        ) {
            ModelElement_1_0 featureDef = (ModelElement_1_0)i.next();
            ModelElement_1_0 referencedEnd = this.backend.getModel().getElement(
                featureDef.values("referencedEnd").get(0)
            );            
            boolean referenceIsCompositeAndChangeable = 
                this.backend.getModel().isReferenceType(featureDef) &&
                AggregationKind.COMPOSITE.equals(referencedEnd.values("aggregation").get(0)) &&
                ((Boolean)referencedEnd.values("isChangeable").get(0)).booleanValue();
            boolean referenceIsSharedAndChangeable = 
                this.backend.getModel().isReferenceType(featureDef) &&
                AggregationKind.SHARED.equals(referencedEnd.values("aggregation").get(0)) &&
                ((Boolean)referencedEnd.values("isChangeable").get(0)).booleanValue();                
            // Only navigate changeable references which are either 'composite' or 'shared'
            // Do not navigate references with aggregation 'none'.
            if(referenceIsCompositeAndChangeable || referenceIsSharedAndChangeable) {
                String reference = (String)featureDef.values("name").get(0);
                Path referencePath = objectIdentity.getChild(reference);
                boolean matches = referenceFilter == null;
                if(!matches) {
                    for(
                        Iterator k = referenceFilter.iterator(); 
                        k.hasNext(); 
                    ) {
                        if(referencePath.isLike((Path)k.next())) {
                            matches = true;
                            break;
                        }
                    }
                }
                if(matches) {                
                    List content = this.backend.getDelegatingRequests().addFindRequest(
                        objectIdentity.getChild(reference),
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
                        DataproviderObject_1_0 composite = (DataproviderObject_1_0)j.next();
                        numberOfReplacements += this.applyReplacements(
                            composite.path(),
                            ((Boolean)referencedEnd.values("isChangeable").get(0)).booleanValue(),
                            replacements,
                            referenceFilter
                        );
                    }
                }
            }
        }        
        
        // In addition apply replacement to referenced objects
        Set referencedObjectPaths = new HashSet();
        this.collectReferencedObjects(
            object,
            referenceFilter,
            referencedObjectPaths  
        );
        for(
            Iterator i = referencedObjectPaths.iterator(); 
            i.hasNext(); 
        ) {
            numberOfReplacements += this.applyReplacements(
                (Path)i.next(),
                true,
                replacements,
                referenceFilter
            );
        }
        return numberOfReplacements;
    }
    
    //-------------------------------------------------------------------------
    public Path createObjectFromTemplate(
        DataproviderObject_1_0 source,
        String name,
        String referenceFilterAsString
    ) throws ServiceException {
        Path clonedIdentity = this.cloneAndUpdateReferences(
            source,
            source.path().getParent(),
            null,
            referenceFilterAsString,
            false
        ).path();
        DataproviderObject cloned = this.backend.retrieveObjectForModification(
            clonedIdentity
        );
        cloned.clearValues("isTemplate").add(Boolean.FALSE);
        if(name != null) {
            cloned.clearValues("name").add(name);
        }
        return clonedIdentity;
    }

    //-------------------------------------------------------------------------
    // Variables
    //-------------------------------------------------------------------------    
    public static final Set CLONE_EXCLUDE_ATTRIBUTES =
        new HashSet(Arrays.asList(new String[]{"activityNumber"}));
    
    public static final Set CLONE_EXCLUDE_COMPOSITE_REFERENCES =
        new HashSet(Arrays.asList(new String[]{"view"}));
    
    public static final String DEFAULT_REFERENCE_FILTER = ":*, :*/:*/:*, :*/:*/:*/:*/:*";
    
    public static final Set CLONEABLE_READONLY_FEATURES =
        new HashSet(Arrays.asList(new String[]{
            "org:opencrx:kernel:contract1:ContractPosition:lineItemNumber",
            "org:opencrx:kernel:product1:ProductDescriptor:product",
            "org:opencrx:kernel:account1:AbstractAccount:fullName",
            "org:opencrx:kernel:product1:ProductConfigurationSet:configType",
            "org:opencrx:kernel:product1:ProductConfiguration:configType",
            "org:opencrx:kernel:activity1:Activity:ical"
        }));
    
    public static final int MANUAL_QUALIFIER_THRESHOLD = 10;
    
    protected final Backend backend;
}

//--- End of File -----------------------------------------------------------
