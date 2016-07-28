/*
 * #%L
 * Alfresco Remote API
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

package org.alfresco.rest.framework.webscripts;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

import org.alfresco.rest.framework.Api;
import org.alfresco.rest.framework.core.ResourceInspector;
import org.alfresco.rest.framework.core.ResourceInspectorUtil;
import org.alfresco.rest.framework.core.ResourceLocator;
import org.alfresco.rest.framework.core.ResourceWithMetadata;
import org.alfresco.rest.framework.core.exceptions.ApiException;
import org.alfresco.rest.framework.core.exceptions.InvalidArgumentException;
import org.alfresco.rest.framework.core.exceptions.NotFoundException;
import org.alfresco.rest.framework.core.exceptions.PermissionDeniedException;
import org.alfresco.rest.framework.jacksonextensions.BeanPropertiesFilter;
import org.alfresco.rest.framework.jacksonextensions.ExecutionResult;
import org.alfresco.rest.framework.jacksonextensions.JacksonHelper;
import org.alfresco.rest.framework.resource.actions.ActionExecutor;
import org.alfresco.rest.framework.resource.parameters.CollectionWithPagingInfo;
import org.alfresco.rest.framework.resource.parameters.InvalidSelectException;
import org.alfresco.rest.framework.resource.parameters.Paging;
import org.alfresco.rest.framework.resource.parameters.Params;
import org.alfresco.rest.framework.resource.parameters.Params.RecognizedParams;
import org.alfresco.rest.framework.resource.parameters.SortColumn;
import org.alfresco.rest.framework.resource.parameters.where.InvalidQueryException;
import org.alfresco.rest.framework.resource.parameters.where.Query;
import org.alfresco.rest.framework.resource.parameters.where.QueryImpl;
import org.alfresco.rest.framework.resource.parameters.where.WhereCompiler;
import org.alfresco.rest.framework.tools.ApiAssistant;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonErrorNode;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.RewriteCardinalityException;
import org.antlr.runtime.tree.Tree;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.JsonMappingException;
import org.springframework.beans.BeanUtils;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.http.HttpMethod;

/**
 * Helps a Webscript with various tasks
 * 
 * @author Gethin James
 * @author janv
 */
public class ResourceWebScriptHelper
{
    private static Log logger = LogFactory.getLog(ResourceWebScriptHelper.class);
    public static final String PARAM_RELATIONS = "relations";

    public static final String PARAM_FILTER_FIELDS = "fields";

    @Deprecated
    public static final String PARAM_FILTER_PROPERTIES = "properties";

    public static final String PARAM_PAGING_SKIP = "skipCount";
    public static final String PARAM_PAGING_MAX = "maxItems";
    public static final String PARAM_ORDERBY = "orderBy";
    public static final String PARAM_WHERE = "where";

    public static final String PARAM_SELECT = "select";

    public static final String PARAM_INCLUDE = "include";
    public static final String PARAM_INCLUDE_SOURCE_ENTITY = "includeSource";

    public static final List<String> KNOWN_PARAMS = Arrays.asList(
            PARAM_RELATIONS, PARAM_FILTER_PROPERTIES, PARAM_FILTER_FIELDS,PARAM_PAGING_SKIP,PARAM_PAGING_MAX,
            PARAM_ORDERBY, PARAM_WHERE, PARAM_SELECT, PARAM_INCLUDE_SOURCE_ENTITY);
    
    private ResourceLocator locator;

    private ActionExecutor executor;

    /**
     * Takes the web request and looks for a "fields" parameter  (otherwise deprecated "properties" parameter).
     *
     * Parses the parameter and produces a list of bean properties to use as a filter A
     * SimpleBeanPropertyFilter it returned that uses the bean properties. If no
     * filter param is set then a default BeanFilter is returned that will never
     * filter fields (ie. Returns all bean properties).
     * 
     * @param filterParams String
     * @return BeanPropertyFilter - if no parameter then returns a new
     *         ReturnAllBeanProperties class
     */
    public static BeanPropertiesFilter getFilter(String filterParams)
    {
    	return getFilter(filterParams, null);
    }

    /**
     * Takes the web request and looks for a "fields" parameter (otherwise deprecated "properties" parameter).
     *
     * Parses the parameter and produces a list of bean properties to use as a filter A
     * SimpleBeanPropertyFilter it returned that uses the bean properties. If no
     * filter param is set then a default BeanFilter is returned that will never
     * filter fields (ie. Returns all bean properties).
     *
     * If selectList is provided then it will take precedence (ie. be included) over the fields/properties filter
     * for top-level entries (bean properties).
     *
     * For example, this will return entries from both select & properties, eg.
     *
     *    select=abc,def&properties=id,name,ghi
     *
     * Note: it should be noted that API-generic "fields" clause does not currently work for sub-entries.
     *
     * Hence, even if the API-specific "select" clause allows selection of a sub-entries this cannot be used
     * with "fields" filtering. For example, an API-specific method may implement and return "abc/blah", eg.
     *
     *    select=abc/blah
     *
     * However the following will not return "abc/blah" if used with fields filtering, eg.
     *
     *    select=abc/blah&fields=id,name,ghi
     *
     * If fields filtering is desired then it would require "abc" to be selected and returned as a whole, eg.
     *
     *    select=abc&fields=id,name,ghi
     *
     * @param filterParams
     * @param selectList
     * @return
     */
    public static BeanPropertiesFilter getFilter(String filterParams, List<String> selectList)
    {
        if (filterParams != null)
        {
            StringTokenizer st = new StringTokenizer(filterParams, ",");
            Set<String> filteredProperties = new HashSet<String>(st.countTokens());
            while (st.hasMoreTokens())
            {
                filteredProperties.add(st.nextToken());
            }

            // if supplied, the select takes precedence over the filter (fields/properties) for top-level bean properties
            if (selectList != null)
            {
            	for (String select : selectList)
            	{
            		String[] split = select.split("/");
            		filteredProperties.add(split[0]);
            	}
            }
            
            logger.debug("Filtering using the following properties: " + filteredProperties);
            BeanPropertiesFilter filter = new BeanPropertiesFilter(filteredProperties);
            return filter;
        }
        return BeanPropertiesFilter.ALLOW_ALL;
    }

    /**
     * Takes the web request and looks for a "relations" parameter Parses the
     * parameter and produces a list of bean properties to use as a filter A
     * SimpleBeanPropertiesFilter it returned that uses the properties If no
     * filter param is set then a default BeanFilter is returned that will never
     * filter properties (ie. Returns all bean properties).
     * 
     * @param filterParams String
     * @return BeanPropertiesFilter - if no parameter then returns a new
     *         ReturnAllBeanProperties class
     */
    public static Map<String, BeanPropertiesFilter> getRelationFilter(String filterParams)
    {
        if (filterParams != null)
        {
            // Split by a comma when not in a bracket
            String[] relations = filterParams.split(",(?![^()]*+\\))");
            Map<String, BeanPropertiesFilter> filterMap = new HashMap<String, BeanPropertiesFilter>(relations.length);

            for (String relation : relations)
            {
                int bracketLocation = relation.indexOf("(");
                if (bracketLocation != -1)
                {
                    // We have properties
                    String relationKey = relation.substring(0, bracketLocation);
                    String props = relation.substring(bracketLocation + 1, relation.length() - 1);
                    filterMap.put(relationKey, getFilter(props));
                }
                else
                {
                    // no properties so just get the String
                    filterMap.put(relation, getFilter(null));
                }
            }
            return filterMap;
        }
        return Collections.emptyMap();
    }
    
    /**
     * Takes the "select" parameter and turns it into a List<String> property names
     * @param selectParam String
     * @return List<String> bean property names potentially using JSON Pointer syntax
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public static List<String> getSelectClause(String selectParam) throws InvalidArgumentException
    {
        return getClause(selectParam, "SELECT");
    }

    /**
     * Takes the "include" parameter and turns it into a List<String> property names
     * @param includeParam String
     * @return List<String> bean property names potentially using JSON Pointer syntax
     */
    @SuppressWarnings("unchecked")
    public static List<String> getIncludeClause(String includeParam) throws InvalidArgumentException
    {
        return getClause(includeParam, "INCLUDE");
    }

    private static List<String> getClause(String param, String paramName)
    {
        if (param == null) return Collections.emptyList();
        
		try {
			CommonTree selectedPropsTree = WhereCompiler.compileSelectClause(param);
			if (selectedPropsTree instanceof CommonErrorNode)
			{
				logger.debug("Error parsing the "+paramName+" clause "+selectedPropsTree);
				throw new InvalidSelectException(paramName, selectedPropsTree);
			}
			if (selectedPropsTree.getChildCount() == 0 && !selectedPropsTree.getText().isEmpty())
			{
				return Arrays.asList(selectedPropsTree.getText());
			}
			List<Tree> children = (List<Tree>) selectedPropsTree.getChildren();
			if (children!= null && !children.isEmpty())
			{
				List<String> properties = new ArrayList<String>(children.size());
				for (Tree child : children) {
					properties.add(child.getText());
				}
				return properties;
			}
		}
        catch (RewriteCardinalityException re)
        {
            //Catch any error so it doesn't get thrown up the stack
			logger.debug("Unhandled Error parsing the "+paramName+" clause: "+re);
		}
        catch (RecognitionException e)
        {
			logger.debug("Error parsing the \"+paramName+\" clause: "+param);
		}
        catch (InvalidQueryException iqe)
        {
            throw new InvalidSelectException(paramName, iqe.getQueryParam());
        }
        //Default to throw out an invalid query
        throw new InvalidSelectException(paramName, param);
    }
    
    /**
     * Takes the "where" parameter and turns it into a Java Object that can be used for querying
     * @param whereParam String
     * @return Query a parsed version of the where clause, represented in Java
     */
    public static Query getWhereClause(String whereParam) throws InvalidQueryException
    {
        if (whereParam == null) return QueryImpl.EMPTY;
        
		try {
			CommonTree whereTree = WhereCompiler.compileWhereClause(whereParam);
			if (whereTree instanceof CommonErrorNode)
			{
				logger.debug("Error parsing the WHERE clause "+whereTree);
				throw new InvalidQueryException(whereTree);
			}
	        return new QueryImpl(whereTree);
		} catch (RewriteCardinalityException re) {  //Catch any error so it doesn't get thrown up the stack
			logger.info("Unhandled Error parsing the WHERE clause: "+re);
		} catch (RecognitionException e) {
			whereParam += ", "+WhereCompiler.resolveMessage(e);
			logger.info("Error parsing the WHERE clause: "+whereParam);
		}
        //Default to throw out an invalid query
        throw new InvalidQueryException(whereParam);
    }

    /**
     * Takes the Sort parameter as a String and parses it into a List of SortColumn objects.
     * The format is a comma seperated list of "columnName sortDirection",
     * e.g. "name DESC, age ASC".  It is not case sensitive and the sort direction is optional
     * It default to sort ASCENDING.
     * @param sortParams - String passed in on the request
     * @return List<SortColumn> - the sort columns or an empty list if the params were invalid.
     */
    public static List<SortColumn> getSort(String sortParams)
    {
        if (sortParams != null)
        {
            StringTokenizer st = new StringTokenizer(sortParams, ",");
            List<SortColumn> sortedColumns = new ArrayList<SortColumn>(st.countTokens());
            while (st.hasMoreTokens())
            {
                String token = st.nextToken();
                StringTokenizer columnDesc = new StringTokenizer(token, " ");
                if (columnDesc.countTokens() <= 2)
                {
                String columnName = columnDesc.nextToken();
                String sortOrder = SortColumn.ASCENDING;
                if (columnDesc.hasMoreTokens())
                {
                  String sortDef = columnDesc.nextToken().toUpperCase();  
                  if (SortColumn.ASCENDING.equals(sortDef) || SortColumn.DESCENDING.equals(sortDef))
                  {
                      sortOrder = sortDef;
                  }
                  else
                  {
                      logger.debug("Invalid sort order definition ("+sortDef+").  Valid values are "+SortColumn.ASCENDING+" or "+SortColumn.DESCENDING+".");
                  }
                }
                sortedColumns.add(new SortColumn(columnName, SortColumn.ASCENDING.equals(sortOrder)));
                }
               // filteredProperties.add();
            }
//            logger.debug("Filtering using the following properties: " + filteredProperties);
//            BeanPropertiesFilter filter = new BeanPropertiesFilter(filteredProperties);
            return sortedColumns;
        }
        return Collections.emptyList();
    }

    /**
     * Extracts the body contents from the request
     * 
     * @param req the request
     * @param jsonHelper Jackson Helper
     * @param requiredType the type to return
     * @return the Object in the required type
     */
    public static <T> T extractJsonContent(WebScriptRequest req, JacksonHelper jsonHelper, Class<T> requiredType)
    {
        Reader reader;
        try
        {
            reader = req.getContent().getReader();
            return jsonHelper.construct(reader, requiredType);
        }
        catch (JsonMappingException e)
        {
        	logger.warn("Could not read content from HTTP request body.", e);
            throw new InvalidArgumentException("Could not read content from HTTP request body.");
        }
        catch (IOException e)
        {
            throw new ApiException("Could not read content from HTTP request body.", e.getCause());
        }
    }

    /**
     * Extracts the body contents from the request as a List, the JSON can be an array or just a single value without the [] symbols
     * 
     * @param req the request
     * @param jsonHelper Jackson Helper
     * @param requiredType the type to return (without the List param)
     * @return A List of "Object" as the required type
     */
    public static <T> List<T> extractJsonContentAsList(WebScriptRequest req, JacksonHelper jsonHelper, Class<T> requiredType)
    {
        Reader reader;
        try
        {
            reader = req.getContent().getReader();
            return jsonHelper.constructList(reader, requiredType);
        }
        catch (IOException e)
        {
            throw new ApiException("Could not read content from HTTP request body.", e.getCause());
        }
    }

    /**
     * Set the id of theObj to the uniqueId. Attempts to find a set method and
     * invoke it. If it fails it just swallows the exceptions and doesn't throw
     * them further.
     * 
     * @param theObj Object
     * @param uniqueId String
     */
    public static void setUniqueId(Object theObj, String uniqueId)
    {
        Method annotatedMethod = ResourceInspector.findUniqueIdMethod(theObj.getClass());
        if (annotatedMethod != null)
        {
            PropertyDescriptor pDesc = BeanUtils.findPropertyForMethod(annotatedMethod);
            if (pDesc != null)
            {
                Method writeMethod = pDesc.getWriteMethod();
                if (writeMethod != null)
                {
                    try
                    {
                        writeMethod.invoke(theObj, uniqueId);
                        if (logger.isDebugEnabled())
                        {
                            logger.debug("Unique id set for property: " + pDesc.getName());
                        }
                    }
                    catch (IllegalArgumentException error)
                    {
                        logger.warn("Invocation error", error);
                    }
                    catch (IllegalAccessException error)
                    {
                        logger.warn("IllegalAccessException", error);
                    }
                    catch (InvocationTargetException error)
                    {
                        logger.warn("InvocationTargetException", error);
                    }
                }
                else
                {
                    logger.warn("No setter method for property: " + pDesc.getName());
                }
            }

        }
    }

//    /**
//     * Renders the response result
//     * 
//     * @param response
//     * @param result
//     */
//    public static void renderResponseDep(Map<String, Object> response, Object result)
//    {
//
//        if (result == null) { return; }
//
//        if (result instanceof Collection)
//        {
//            response.put("list", result);
//        }
//        else if (result instanceof CollectionWithPagingInfo)
//        {
//            CollectionWithPagingInfo<?> col = (CollectionWithPagingInfo<?>) result;
//            if (col.getCollection() !=null && !col.getCollection().isEmpty())
//            {
//                response.put("list", col);
//            }
//        }
//        else if (result instanceof Pair<?,?>)
//        {
//            Pair<?,?> aPair = (Pair<?, ?>) result;
//            response.put("entry", aPair.getFirst());
//            response.put("relations", aPair.getSecond());
//        }
//        else
//        {
//            response.put("entry", result);
//        }
//    }

    /**
     * Looks at the object passed in and recursively expands any @EmbeddedEntityResource annotations or related relationship.
     * {@link org.alfresco.rest.framework.resource.EmbeddedEntityResource EmbeddedEntityResource} is expanded by calling the ReadById method for this entity.
     * 
     * Either returns a ExecutionResult object or a CollectionWithPagingInfo containing a collection of ExecutionResult objects.
     * 
     * @param api Api
     * @param entityCollectionName String
     * @param params  Params
     * @param objectToWrap Object
     * @return Object - Either ExecutionResult or CollectionWithPagingInfo<ExecutionResult>
     */
    public Object processAdditionsToTheResponse(WebScriptResponse res, Api api, String entityCollectionName, Params params, Object objectToWrap)
    {
        PropertyCheck.mandatory(this, null, params);
        if (objectToWrap == null ) return null;
        if (objectToWrap instanceof CollectionWithPagingInfo<?>)
        {
            CollectionWithPagingInfo<?> collectionToWrap = (CollectionWithPagingInfo<?>) objectToWrap;
            Object sourceEntity = executeIncludedSource(api, params, entityCollectionName, collectionToWrap);
            Collection<Object> resultCollection = new ArrayList(collectionToWrap.getCollection().size());
            if (!collectionToWrap.getCollection().isEmpty())
            {
                for (Object obj : collectionToWrap.getCollection())
                {
                    resultCollection.add(processAdditionsToTheResponse(res, api,entityCollectionName,params,obj));
                }
            }
            return CollectionWithPagingInfo.asPaged(collectionToWrap.getPaging(), resultCollection, collectionToWrap.hasMoreItems(), collectionToWrap.getTotalItems(), sourceEntity);
        }
        else
        {           
            if (BeanUtils.isSimpleProperty(objectToWrap.getClass())  || objectToWrap instanceof Collection)
            {
                //Simple property or Collection that can't be embedded so just return it.
                return objectToWrap;
            }

            final ExecutionResult execRes = new ExecutionResult(objectToWrap, params.getFilter());
            
            Map<String,Pair<String,Method>> embeddded = ResourceInspector.findEmbeddedResources(objectToWrap.getClass());
            if (embeddded != null && !embeddded.isEmpty())
            {
                Map<String, Object> results = executeEmbeddedResources(api, params,objectToWrap, embeddded);
                execRes.addEmbedded(results);
            }
            
            if (params.getRelationsFilter() != null && !params.getRelationsFilter().isEmpty())
            {
                Map<String, ResourceWithMetadata> relationshipResources = locator.locateRelationResource(api,entityCollectionName, params.getRelationsFilter().keySet(), HttpMethod.GET);
                String uniqueEntityId = ResourceInspector.findUniqueId(objectToWrap);
                Map<String,Object> relatedResources = executeRelatedResources(api, params, relationshipResources, uniqueEntityId);
                execRes.addRelated(relatedResources);
            }

            return execRes; 

        }
    }

    private Object executeIncludedSource(Api api, Params params, String entityCollectionName, CollectionWithPagingInfo<?> collectionToWrap)
    {
        if (params.includeSource())
        {
            if (collectionToWrap.getSourceEntity() != null)
            {
                //The implementation has already set it so return it;
                return collectionToWrap.getSourceEntity();
            }

            ResourceWithMetadata res = locator.locateEntityResource(api, entityCollectionName, HttpMethod.GET);
            if (res != null)
            {
                Object result = executeResource(api, params, params.getEntityId(), null, res);
                if (result!=null && result instanceof ExecutionResult) return ((ExecutionResult) result).getRoot();
            }
        }
        return null;
    }

    /**
     * Loops through the embedded Resources and executes them.  The results are added to list of embedded results used by
     * the ExecutionResult object.
     *
     * @param api Api
     * @param params Params
     * @param objectToWrap Object
     * @param embeddded Map<String, Pair<String, Method>>
     * @return Map
     */
    private Map<String, Object> executeEmbeddedResources(Api api, Params params, Object objectToWrap, Map<String, Pair<String, Method>> embeddded)
    {
        final Map<String,Object> results = new HashMap<String,Object>(embeddded.size());
        for (Entry<String, Pair<String,Method>> embeddedEntry : embeddded.entrySet())
        {
            ResourceWithMetadata res = locator.locateEntityResource(api, embeddedEntry.getValue().getFirst(), HttpMethod.GET);
            if (res != null)
            {
                Object id = ResourceInspectorUtil.invokeMethod(embeddedEntry.getValue().getSecond(), objectToWrap);
                if (id != null)
                {
                    Object execEmbeddedResult = executeResource(api, params, String.valueOf(id), embeddedEntry.getKey(), res);
                    if (execEmbeddedResult != null)
                    {
                        if (execEmbeddedResult instanceof ExecutionResult)
                        {
                           ((ExecutionResult) execEmbeddedResult).setAnEmbeddedEntity(true);
                        }
                        results.put(embeddedEntry.getKey(), execEmbeddedResult);
                    }
                }
                else
                {
                    //Call to embedded id for null value, 
                    logger.warn("Cannot embed resource with path "+embeddedEntry.getKey()+". No unique id because the method annotated with @EmbeddedEntityResource returned null.");
                }
            }
        }
        return results;
    }

    /**
     * Loops through the related Resources and executed them.  The results are added to list of embedded results used by
     * the ExecutionResult object.
     *
     * @param api Api
     * @param params Params
     * @param relatedResources Map<String, ResourceWithMetadata>
     * @param uniqueEntityId String
     * @return Map
     */
    private Map<String,Object> executeRelatedResources(final Api api, Params params,
                                                       Map<String, ResourceWithMetadata> relatedResources,
                                                       String uniqueEntityId)
    {
        final Map<String,Object> results = new HashMap<String,Object>(relatedResources.size());
        for (final Entry<String, ResourceWithMetadata> relation : relatedResources.entrySet())
        {
            Object execResult = executeResource(api, params, uniqueEntityId, relation.getKey(), relation.getValue());
            if (execResult != null)
            {
              results.put(relation.getKey(), execResult);
            }
        }
        return results;
    }

    /**
     * Executes a single related Resource.  The results are added to list of embedded results used by
     * the ExecutionResult object.
     *
     * @param api Api
     * @param params Params
     * @param uniqueEntityId String
     * @param resourceKey String
     * @param resource ResourceWithMetadata
     * @return Object
     */
    private Object executeResource(final Api api, Params params,
                                   final String uniqueEntityId, final String resourceKey, final ResourceWithMetadata resource)
    {
        try
        {
            BeanPropertiesFilter paramFilter = null;
            final Object[] resultOfExecution = new Object[1];
            Map<String, BeanPropertiesFilter> filters = params.getRelationsFilter();
            if (filters!=null)
            {
                paramFilter = filters.get(resourceKey);
            }
            final Params executionParams = Params.valueOf(paramFilter, uniqueEntityId, params.getRequest());
            final WithResponse callBack = new WithResponse(Status.STATUS_OK, ApiAssistant.DEFAULT_JSON_CONTENT,ApiAssistant.CACHE_NEVER);
            //Read only because this only occurs for GET requests
            Object result = executor.executeAction(resource, executionParams, callBack);
            return processAdditionsToTheResponse(null, api, null, executionParams, result);
        }
        catch(NotFoundException e)
        {
        	// ignore, cannot access the object so don't embed it
            if (logger.isDebugEnabled())
            {
                logger.debug("Ignored error, cannot access the object so can't embed it ", e);
            }
        }
        catch(PermissionDeniedException e)
        {
        	// ignore, cannot access the object so don't embed it
            if (logger.isDebugEnabled())
            {
                logger.debug("Ignored error, cannot access the object so can't embed it ", e);
            }
        } catch (Throwable throwable)
        {
            logger.warn("Failed to execute a RelatedResource for "+resourceKey+" "+throwable.getMessage());
        }

        return null; //default
    }

    /**
     * Finds all request parameters that aren't already known about (eg. not paging or filter params)
     * and returns them for use.
     * 
     * @param req - the WebScriptRequest object
     * @return the request parameters
     */
    public static Map<String, String[]> getRequestParameters(WebScriptRequest req)
    {
        if (req!= null)
        {
            String[] paramNames = req.getParameterNames();
            if (paramNames!= null)
            {
                Map<String, String[]> requestParameteters = new HashMap<String, String[]>(paramNames.length);
                
                for (int i = 0; i < paramNames.length; i++)
                {
                    String paramName = paramNames[i];
                    if (!KNOWN_PARAMS.contains(paramName))
                    {
                        String[] vals = req.getParameterValues(paramName);
                        requestParameteters.put(paramName, vals);
                    }
                }
                return requestParameteters;
            }
        }
        
        return Collections.emptyMap();
    }
    
    /**
     * Finds the formal set of params that any rest service could potentially have passed in as request params
     * @param req WebScriptRequest
     * @return RecognizedParams a POJO containing the params for use with the Params objects
     */
    public static RecognizedParams getRecognizedParams(WebScriptRequest req)
    {
    	Paging paging = findPaging(req);
        List<SortColumn> sorting = getSort(req.getParameter(ResourceWebScriptHelper.PARAM_ORDERBY));
        Map<String, BeanPropertiesFilter> relationFilter = getRelationFilter(req.getParameter(ResourceWebScriptHelper.PARAM_RELATIONS));
        Query whereQuery = getWhereClause(req.getParameter(ResourceWebScriptHelper.PARAM_WHERE));
        Map<String, String[]> requestParams = getRequestParameters(req);
        boolean includeSource = Boolean.valueOf(req.getParameter(ResourceWebScriptHelper.PARAM_INCLUDE_SOURCE_ENTITY));

        List<String> includedFields = getIncludeClause(req.getParameter(ResourceWebScriptHelper.PARAM_INCLUDE));
        List<String> selectFields = getSelectClause(req.getParameter(ResourceWebScriptHelper.PARAM_SELECT));

        String fields = req.getParameter(ResourceWebScriptHelper.PARAM_FILTER_FIELDS);
        String properties = req.getParameter(ResourceWebScriptHelper.PARAM_FILTER_PROPERTIES);

        if ((fields != null) && (properties != null))
        {
            if (logger.isWarnEnabled())
            {
                logger.warn("Taking 'fields' param [" + fields + "] and ignoring deprecated 'properties' param [" + properties + "]");
            }
        }

        BeanPropertiesFilter filter = getFilter((fields != null ? fields : properties), includedFields);

        return new RecognizedParams(requestParams, paging, filter, relationFilter, includedFields, selectFields, whereQuery, sorting, includeSource);
    }

    /**
     * Find paging setings based on the request parameters.
     * 
     * @param req
     * @return Paging
     */
    public static Paging findPaging(WebScriptRequest req)
    {
        int skipped = Paging.DEFAULT_SKIP_COUNT;
        int max = Paging.DEFAULT_MAX_ITEMS;
        String skip = req.getParameter(PARAM_PAGING_SKIP);
        String maxItems = req.getParameter(PARAM_PAGING_MAX);

        try
        {
            if (skip != null) { skipped = Integer.parseInt(skip);}
            if (maxItems != null) { max = Integer.parseInt(maxItems); }
            if (max < 0 || skipped < 0)
            {
                throw new InvalidArgumentException("Negative values not supported.");  
            }
        }
        catch (NumberFormatException error)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Invalid paging params skip: " + skip + ",maxItems:" + maxItems);
            }
            throw new InvalidArgumentException();
        }

        return Paging.valueOf(skipped, max);
    }

    public void setLocator(ResourceLocator locator)
    {
        this.locator = locator;
    }

    public void setExecutor(ActionExecutor executor)
    {
        this.executor = executor;
    }
        
}
