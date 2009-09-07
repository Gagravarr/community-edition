/*
 * Copyright (C) 2005-2009 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.domain.audit;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.audit.AuditState;
import org.alfresco.repo.audit.hibernate.HibernateAuditDAO;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.propval.PropertyIdSearchRow;
import org.alfresco.repo.domain.propval.PropertyValueDAO;
import org.alfresco.service.cmr.audit.AuditInfo;
import org.alfresco.service.cmr.audit.AuditService.AuditQueryCallback;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Abstract helper DAO for <b>alf_audit_XXX</b> tables.
 * 
 * @author Derek Hulley
 * @since 3.2
 */
public abstract class AbstractAuditDAOImpl implements AuditDAO 
{
    protected final Log logger = LogFactory.getLog(this.getClass());
    
    private HibernateAuditDAO oldDAO;
    private ContentService contentService;
    private ContentDataDAO contentDataDAO;
    protected PropertyValueDAO propertyValueDAO;
    
    public void setOldDAO(HibernateAuditDAO oldDAO)
    {
        this.oldDAO = oldDAO;
    }
    
    public void setContentService(ContentService contentService)
    {
        this.contentService = contentService;
    }

    public void setContentDataDAO(ContentDataDAO contentDataDAO)
    {
        this.contentDataDAO = contentDataDAO;
    }
    
    public void setPropertyValueDAO(PropertyValueDAO propertyValueDAO)
    {
        this.propertyValueDAO = propertyValueDAO;
    }
    
    protected PropertyValueDAO getPropertyValueDAO()
    {
        return this.propertyValueDAO;
    }
    
    /*
     * Support for older audit DAO
     */

    /**
     * Uses {@link HibernateAuditDAO older DAO}
     * @since 3.2
     */
    public void audit(AuditState auditInfo)
    {
        oldDAO.audit(auditInfo);
    }

    /**
     * Uses {@link HibernateAuditDAO older DAO}
     * @since 3.2
     */
    public List<AuditInfo> getAuditTrail(NodeRef nodeRef)
    {
        return oldDAO.getAuditTrail(nodeRef);
    }

    /*
     * alf_audit_model
     */

    /**
     * {@inheritDoc}
     */
    public Pair<Long, ContentData> getOrCreateAuditModel(URL url)
    {
        InputStream is = null;
        try
        {
            is = url.openStream();
            // Calculate the CRC and find an entry that matches
            CRC32 crcCalc = new CRC32();
            byte[] buffer = new byte[1024];
            int read = -1;
            do
            {
                read = is.read(buffer);
                if (read < 0)
                {
                    break;
                }
                crcCalc.update(buffer, 0, read);
            }
            while (true);
            long crc = crcCalc.getValue();
            // Find an existing entry
            AuditModelEntity existingEntity = getAuditModelByCrc(crc);
            if (existingEntity != null)
            {
                Long existingEntityId = existingEntity.getId();
                // Locate the content
                ContentData existingContentData = contentDataDAO.getContentData(
                        existingEntity.getContentDataId()
                        ).getSecond();
                Pair<Long, ContentData> result = new Pair<Long, ContentData>(existingEntityId, existingContentData);
                // Done
                if (logger.isDebugEnabled())
                {
                    logger.debug(
                            "Found existing model with same CRC: \n" +
                            "   URL:    " + url + "\n" +
                            "   CRC:    " + crc + "\n" +
                            "   Result: " + result);
                }
                return result;
            }
            else
            {
                // Upload the content afresh
                is.close();
                is = url.openStream();
                ContentWriter writer = contentService.getWriter(null, null, false);
                writer.setEncoding("UTF-8");
                writer.setMimetype(MimetypeMap.MIMETYPE_XML);
                writer.putContent(is);
                ContentData newContentData = writer.getContentData();
                Long newContentDataId = contentDataDAO.createContentData(newContentData).getFirst();
                AuditModelEntity newEntity = createAuditModel(newContentDataId, crc);
                Pair<Long, ContentData> result = new Pair<Long, ContentData>(newEntity.getId(), newContentData);
                // Done
                if (logger.isDebugEnabled())
                {
                    logger.debug(
                            "Created new audit model: \n" +
                            "   URL:    " + url + "\n" +
                            "   CRC:    " + crc + "\n" +
                            "   Result: " + result);
                }
                return result;
            }
        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException("Failed to read Audit model: " + url);
        }
        finally
        {
            if (is != null)
            {
                try { is.close(); } catch (Throwable e) {}
            }
        }
    }
    
    protected abstract AuditModelEntity getAuditModelByCrc(long crc);
    protected abstract AuditModelEntity createAuditModel(Long contentDataId, long crc);
    
    /*
     * alf_audit_application
     */
    
    public Long getOrCreateAuditApplication(Long modelId, String application)
    {
        // Search for it
        AuditApplicationEntity entity = getAuditApplicationByModelIdAndName(modelId, application);
        if (entity == null)
        {
            // Create it
            // Persist the string
            Long appNameId = propertyValueDAO.getOrCreatePropertyValue(application).getFirst();
            // Create the audit session
            entity = createAuditApplication(modelId, appNameId);
            // Done
            if (logger.isDebugEnabled())
            {
                logger.debug(
                        "Created new audit application: \n" +
                        "   Model:  " + modelId + "\n" +
                        "   App:    " + application + "\n" +
                        "   Result: " + entity);
            }
        }
        // Done
        return entity.getId();
    }

    protected abstract AuditApplicationEntity getAuditApplicationByModelIdAndName(Long modelId, String appName);
    protected abstract AuditApplicationEntity createAuditApplication(Long modelId, Long appNameId);
    
    /*
     * alf_audit_entry
     */

    public Long createAuditEntry(Long applicationId, long time, String username, Map<String, Serializable> values)
    {
        final Long usernameId;
        if (username != null)
        {
            usernameId = propertyValueDAO.getOrCreatePropertyValue(username).getFirst();
        }
        else
        {
            usernameId = null;
        }
        // Now persist the data values
        Long valuesId = null;
        if (values != null && values.size() > 0)
        {
            valuesId = propertyValueDAO.getOrCreatePropertyValue((Serializable)values).getFirst();
        }

        // Create the audit entry
        AuditEntryEntity entity = createAuditEntry(applicationId, time, usernameId, valuesId);

        // Done
        if (logger.isDebugEnabled())
        {
            logger.debug(
                    "Created new audit entry: \n" +
                    "   Application: " + applicationId + "\n" +
                    "   Time:        " + (new Date(time)) + "\n" +
                    "   User:        " + username + "\n" +
                    "   Result:      " + entity);
        }
        return entity.getId();
    }
    
    protected abstract AuditEntryEntity createAuditEntry(Long applicationId, long time, Long usernameId, Long valuesId);
    
    /*
     * Searches
     */
    
    /**
     * Class that passes results from a result entity into the client callback
     */
    protected class AuditQueryRowHandler
    {
        private final AuditQueryCallback callback;
        private boolean more;
        private AuditQueryRowHandler(AuditQueryCallback callback)
        {
            this.callback = callback;
            this.more = true;
        }
        @SuppressWarnings("unchecked")
        public void processResult(AuditQueryResult row)
        {
            if (!more)
            {
                // No more results required
                return;
            }
            // Get the value map
            Map<String, Serializable> auditValues;
            List<PropertyIdSearchRow> propMapRows = row.getAuditValues();
            if (propMapRows == null)
            {
                // Use the audit values ID
                Long auditValuesId = row.getAuditValuesId();
                Pair<Long, Serializable> auditValuesPair = propertyValueDAO.getPropertyValueById(auditValuesId);
                if (auditValuesPair == null)
                {
                    // Ignore
                    logger.warn("Audit entry not joined to audit properties: " + row);
                    return;
                }
                auditValues = (Map<String, Serializable>) auditValuesPair.getSecond();
            }
            else
            {
                // Resolve the map
                try
                {
                    auditValues = (Map<String, Serializable>) propertyValueDAO.convertPropertyIdSearchRows(propMapRows);
                }
                catch (ClassCastException e)
                {
                    logger.warn("Audit entry not linked to a Map<String, Serializable> value: " + row);
                    return;
                }
                if (auditValues == null)
                {
                    logger.warn("Audit entry incompletely joined to audit properties: " + row);
                    return;
                }
            }
            more = callback.handleAuditEntry(
                    row.getAuditEntryId(),
                    row.getAuditAppName(),
                    row.getAuditUser(),
                    row.getAuditTime(),
                    auditValues);
        }
    }

    public void findAuditEntries(
            AuditQueryCallback callback,
            String applicationName, String user, Long from, Long to, int maxResults)
    {
        AuditQueryRowHandler rowHandler = new AuditQueryRowHandler(callback);
        findAuditEntries(rowHandler, applicationName, user, from, to, maxResults);
    }
    
    protected abstract void findAuditEntries(
            AuditQueryRowHandler rowHandler,
            String applicationName, String user, Long from, Long to, int maxResults);
}
