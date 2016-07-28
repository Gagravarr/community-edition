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
package org.alfresco.rest.api.tests;

import static org.alfresco.rest.api.tests.util.RestApiUtil.toJsonAsString;
import static org.alfresco.rest.api.tests.util.RestApiUtil.toJsonAsStringNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.tenant.TenantUtil;
import org.alfresco.repo.tenant.TenantUtil.TenantRunAsWork;
import org.alfresco.rest.api.Nodes;
import org.alfresco.rest.api.model.Site;
import org.alfresco.rest.api.nodes.NodesEntityResource;
import org.alfresco.rest.api.tests.RepoService.SiteInformation;
import org.alfresco.rest.api.tests.RepoService.TestNetwork;
import org.alfresco.rest.api.tests.RepoService.TestPerson;
import org.alfresco.rest.api.tests.RepoService.TestSite;
import org.alfresco.rest.api.tests.client.HttpResponse;
import org.alfresco.rest.api.tests.client.PublicApiClient;
import org.alfresco.rest.api.tests.client.PublicApiHttpClient.BinaryPayload;
import org.alfresco.rest.api.tests.client.PublicApiHttpClient.RequestBuilder;
import org.alfresco.rest.api.tests.client.RequestContext;
import org.alfresco.rest.api.tests.client.data.Document;
import org.alfresco.rest.api.tests.client.data.Folder;
import org.alfresco.rest.api.tests.client.data.Node;
import org.alfresco.rest.api.tests.client.data.Rendition;
import org.alfresco.rest.api.tests.client.data.SiteRole;
import org.alfresco.rest.api.tests.util.MultiPartBuilder;
import org.alfresco.rest.api.tests.util.RestApiUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.site.SiteVisibility;
import org.alfresco.util.TempFileProvider;
import org.springframework.util.ResourceUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

/**
 * Generic methods for calling the Api, taken from BaseCustomModelApiTest
 */
public abstract class AbstractBaseApiTest extends EnterpriseTestApi
{
    public static final String LAST_MODIFIED_HEADER = "Last-Modified";
    public static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";

    private static final String RESOURCE_PREFIX = "publicapi/upload/";

    protected static final String URL_NODES = "nodes";

    private static final String URL_RENDITIONS = "renditions";
    private static final String URL_CHILDREN = "children";
    private static final String URL_CONTENT = "content";

    protected static final String TYPE_CM_FOLDER = "cm:folder";
    protected static final String TYPE_CM_CONTENT = "cm:content";
    protected static final String TYPE_CM_OBJECT = "cm:cmobject";

    protected static final String ASPECT_CM_PREFERENCES = "cm:preferences";
    protected static final String ASSOC_TYPE_CM_PREFERENCE_IMAGE = "cm:preferenceImage";

    protected static final String ASSOC_TYPE_CM_CONTAINS = "cm:contains";


    /**
     * The api scope. either public or private
     * @return public or private
     */
    public abstract String getScope();

    protected HttpResponse post(String url, String runAsUser, String body, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));

        HttpResponse response = publicApiClient.post(getScope(), url, null, null, null, body);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse post(String url, String runAsUser, String body, Map<String, String> params, Map<String, String> headers, String apiName, int expectedStatus) throws Exception
    {
        RequestBuilder requestBuilder = httpClient.new PostRequestBuilder()
                    .setBodyAsString(body)
                    .setRequestContext(new RequestContext(runAsUser))
                    .setScope(getScope())
                    .setApiName(apiName)
                    .setEntityCollectionName(url)
                    .setHeaders(headers)
                    .setParams(params);
        HttpResponse response = publicApiClient.execute(requestBuilder);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse post(String url, String runAsUser, String body,  String queryString, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));
        if (queryString != null)
        {
            url += queryString;
        }
        HttpResponse response = publicApiClient.post(getScope(), url, null, null, null, body);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse post(String url, String runAsUser, String body, String queryString, String contentType, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));
        if (queryString != null)
        {
            url += queryString;
        }
        HttpResponse response = publicApiClient.post(getScope(), url, null, null, null, body, contentType);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse post(String url, String runAsUser, byte[] body, String queryString, String contentType, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));
        if (queryString != null)
        {
            url += queryString;
        }
        HttpResponse response = publicApiClient.post(getScope(), url, null, null, null, body, contentType);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse post(String runAsUser, String entityCollectionName, String entityId, String relationCollectionName, byte[] body, String queryString, String contentType, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));
        HttpResponse response = publicApiClient.post(getScope(), entityCollectionName, entityId, relationCollectionName, null, body, contentType);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse getAll(String url, String runAsUser, PublicApiClient.Paging paging, int expectedStatus) throws Exception
    {
        return getAll(url, runAsUser, paging, null, expectedStatus);
    }

    protected HttpResponse getAll(String url, String runAsUser, PublicApiClient.Paging paging, Map<String, String> otherParams, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));
        Map<String, String> params = createParams(paging, otherParams);

        HttpResponse response = publicApiClient.get(getScope(), url, null, null, null, params);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse getAll(Class<?> entityResource, String runAsUser, PublicApiClient.Paging paging, Map<String, String> otherParams, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));

        HttpResponse response = publicApiClient.get(entityResource, null, null, otherParams);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse getAll(String url, String runAsUser, PublicApiClient.Paging paging, Map<String, String> otherParams, Map<String, String> headers, int expectedStatus) throws Exception
    {
        return getAll(url, runAsUser, paging, otherParams, headers, null, expectedStatus);
    }

    protected HttpResponse getAll(String url, String runAsUser, PublicApiClient.Paging paging, Map<String, String> otherParams, Map<String, String> headers, String apiName, int expectedStatus) throws Exception
    {
        Map<String, String> params = createParams(paging, otherParams);
        RequestBuilder requestBuilder = httpClient.new GetRequestBuilder()
                    .setRequestContext(new RequestContext(runAsUser))
                    .setScope(getScope())
                    .setApiName(apiName)
                    .setEntityCollectionName(url)
                    .setParams(params)
                    .setHeaders(headers);

        HttpResponse response = publicApiClient.execute(requestBuilder);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse getSingle(String url, String runAsUser, String entityId, int expectedStatus) throws Exception
    {
        return getSingle(url, runAsUser, entityId, null, expectedStatus);
    }

    protected HttpResponse getSingle(String url, String runAsUser, String entityId, Map<String, String> params, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));

        HttpResponse response = publicApiClient.get(getScope(), url, entityId, null, null, params);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse getSingle(Class<?> entityResource, String runAsUser, String entityId, Map<String, String> params, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));

        HttpResponse response = publicApiClient.get(entityResource, entityId, null, params);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse getSingle(String url, String runAsUser, String entityId, Map<String, String> params, Map<String, String> headers, int expectedStatus) throws Exception
    {
        return getSingle(url, runAsUser, entityId, params, headers, null, expectedStatus);
    }

    protected HttpResponse getSingle(String url, String runAsUser, String entityId, Map<String, String> params, Map<String, String> headers, String apiName, int expectedStatus) throws Exception
    {
        RequestBuilder requestBuilder = httpClient.new GetRequestBuilder()
                    .setRequestContext(new RequestContext(runAsUser))
                    .setScope(getScope())
                    .setApiName(apiName)
                    .setEntityCollectionName(url)
                    .setEntityId(entityId)
                    .setParams(params)
                    .setHeaders(headers);

        HttpResponse response = publicApiClient.execute(requestBuilder);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse getSingleWithDelayRetry(String url, String runAsUser, String entityId, Map<String, String> params,
                Map<String, String> headers, int repeat, long pauseInMillisecond, int expectedStatus) throws Exception
    {
        int retryCount = 0;
        while (retryCount < repeat)
        {
            try
            {
                return getSingle(url, runAsUser, entityId, params, headers, expectedStatus);
            }
            catch (AssertionError ex)
            {
                retryCount++;
                Thread.sleep(pauseInMillisecond);
            }
        }
        return null;
    }

    protected HttpResponse put(String url, String runAsUser, String entityId, String body, String queryString, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));
        if (queryString != null)
        {
            entityId += queryString;
        }
        HttpResponse response = publicApiClient.put(getScope(), url, entityId, null, null, body, null);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse putBinary(String url, int version, String runAsUser, BinaryPayload payload, String queryString, Map<String, String> params,
                int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));
        if (queryString != null)
        {
            url += queryString;
        }

        HttpResponse response = publicApiClient.putBinary(getScope(), version, url, null, null, null, payload, params);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse putBinary(String url, String runAsUser, BinaryPayload payload, String queryString, Map<String, String> params,
                int expectedStatus) throws Exception
    {
        return putBinary(url, 1, runAsUser, payload, queryString, params, expectedStatus);
    }

    protected HttpResponse delete(String url, String runAsUser, String entityId, int expectedStatus) throws Exception
    {
        return delete(url, runAsUser, entityId,  null, expectedStatus);
    }

    protected HttpResponse delete(String url, String runAsUser, String entityId,  Map<String, String> params, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(runAsUser));

        HttpResponse response = publicApiClient.delete(getScope(), 1, url, entityId, null, null, params);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected HttpResponse delete(String url, String runAsUser, String entityId, Map<String, String> params, Map<String, String> headers, int expectedStatus) throws Exception
    {
        return delete(url, runAsUser, entityId, params, headers, null, expectedStatus);
    }

    protected HttpResponse delete(String url, String runAsUser, String entityId, Map<String, String> params, Map<String, String> headers, String apiName, int expectedStatus) throws Exception
    {
        RequestBuilder requestBuilder = httpClient.new DeleteRequestBuilder()
                    .setRequestContext(new RequestContext(runAsUser))
                    .setScope(getScope())
                    .setApiName(apiName)
                    .setEntityCollectionName(url)
                    .setEntityId(entityId)
                    .setParams(params)
                    .setHeaders(headers);

        HttpResponse response = publicApiClient.execute(requestBuilder);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    protected String createUser(String username)
    {
        return createUser(username, "password");
    }

    protected String createUser(String username, String password)
    {
        PersonInfo personInfo = new PersonInfo(username, username, username, password, null, null, null, null, null, null, null);
        RepoService.TestPerson person = repoService.createUser(personInfo, username, null);
        return person.getId();
    }

    protected String getOrCreateUser(String username, String password)
    {
        PersonInfo personInfo = new PersonInfo(username, username, username, password, null, null, null, null, null, null, null);
        RepoService.TestPerson person = repoService.getOrCreateUser(personInfo, username, null);
        return person.getId();
    }

    // @deprecated
    protected TestSite createSite(final TestNetwork testNetwork, TestPerson user, final SiteVisibility siteVisibility)
    {
        final String siteName = "RandomSite" + System.currentTimeMillis();

        final TestSite site = TenantUtil.runAsUserTenant(() -> {
            SiteInformation siteInfo = new SiteInformation(siteName, siteName, siteName, siteVisibility);
            return repoService.createSite(testNetwork, siteInfo);
        }, user.getId(), testNetwork.getId());

        return site;
    }

    protected Site createSite(String networkId, String userId, SiteVisibility siteVisibility) throws Exception
    {
        String siteTitle = "RandomSite" + System.currentTimeMillis();
        return createSite(networkId, userId, null, siteTitle, siteVisibility);
    }

    protected Site createSite(String networkId, String userId, String siteId, String siteTitle, SiteVisibility siteVisibility) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(networkId, userId));

        Site site = new Site();
        site.setId(siteId);
        site.setTitle(siteTitle);
        site.setVisibility(siteVisibility);

        HttpResponse response = publicApiClient.post(getScope(), "sites", null, null, null, toJsonAsStringNonNull(site));
        return RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Site.class);
    }

    protected HttpResponse deleteSite(String networkId, String userId, String siteId, int expectedStatus) throws Exception
    {
        publicApiClient.setRequestContext(new RequestContext(networkId, userId));

        HttpResponse response = publicApiClient.delete(getScope(), "sites", siteId, null, null);
        checkStatus(expectedStatus, response.getStatusCode());

        return response;
    }

    /**
     * Helper: to get site container id (see also RepoService.getContainerNodeRef -> SiteService.getContainer)
     *
     *    GET /nodes/-root?relativePath=/Sites/siteId/documentLibrary
     *
     * alternatively:
     *
     *    GET /nodes/siteNodeId?relativePath=documentLibrary
     */
    protected String getSiteContainerNodeId(String networkId, String runAsUserId, String siteId, String containerNameId) throws Exception
    {
        Map<String, String> params = Collections.singletonMap(Nodes.PARAM_RELATIVE_PATH, "/Sites/"+siteId+"/"+containerNameId);

        publicApiClient.setRequestContext(new RequestContext(networkId, runAsUserId));

        HttpResponse response = publicApiClient.get(NodesEntityResource.class, Nodes.PATH_ROOT, null, params);
        checkStatus(200, response.getStatusCode());

        Node node = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Node.class);
        return node.getId();
    }

    protected void inviteToSite(final TestSite testSite, final TestPerson invitee, final SiteRole siteRole)
    {
        TenantUtil.runAsTenant((TenantRunAsWork<Void>) () -> {
            testSite.inviteToSite(invitee.getId(), siteRole);
            return null;
        }, testSite.getNetworkId());
    }

    protected NodeRef getSiteDocLib(final TestSite testSite)
    {
        return TenantUtil.runAsTenant(() -> testSite.getContainerNodeRef(("documentLibrary")), testSite.getNetworkId());
    }

    protected void checkStatus(int expectedStatus, int actualStatus)
    {
        if (expectedStatus > 0 && expectedStatus != actualStatus)
        {
            fail("Status code " + actualStatus + " returned, but expected " + expectedStatus);
        }
    }

    // -root- (eg. Company Home for on-prem)
    protected String getRootNodeId(String runAsUserId) throws Exception
    {
        HttpResponse response = getSingle(NodesEntityResource.class, runAsUserId, Nodes.PATH_ROOT, null, 200);
        Node node = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Node.class);
        return node.getId();
    }

    // -my- (eg. User's Home for on-prem)
    protected String getMyNodeId(String runAsUserId) throws Exception
    {
        HttpResponse response = getSingle(NodesEntityResource.class, runAsUserId, Nodes.PATH_MY, null, 200);
        Node node = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Node.class);
        return node.getId();
    }

    // -shared- (eg. "Shared" folder for on-prem)
    protected String getSharedNodeId(String runAsUserId) throws Exception
    {
        HttpResponse response = getSingle(NodesEntityResource.class, runAsUserId, Nodes.PATH_SHARED, null, 200);
        Node node = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Node.class);
        return node.getId();
    }

    protected Folder createFolder(String runAsUserId, String parentId, String folderName) throws Exception
    {
        return createFolder(runAsUserId, parentId, folderName, null);
    }

    protected Folder createFolder(String runAsUserId, String parentId, String folderName, Map<String, Object> props) throws Exception
    {
        return createNode(runAsUserId, parentId, folderName, TYPE_CM_FOLDER, props, Folder.class);
    }

    protected Node createNode(String runAsUserId, String parentId, String nodeName, String nodeType, Map<String, Object> props) throws Exception
    {
        return createNode(runAsUserId, parentId, nodeName, nodeType, props, Node.class);
    }

    protected <T> T createNode(String runAsUserId, String parentId, String nodeName, String nodeType, Map<String, Object> props, Class<T> returnType)
                throws Exception
    {
        Node n = new Node();
        n.setName(nodeName);
        n.setNodeType(nodeType);
        n.setProperties(props);

        // create node
        HttpResponse response = post(getNodeChildrenUrl(parentId), runAsUserId, RestApiUtil.toJsonAsStringNonNull(n), 201);

        return RestApiUtil.parseRestApiEntry(response.getJsonResponse(), returnType);
    }

    protected Document createTextFile(String userId, String parentId, String fileName, String textContent) throws IOException, Exception
    {
        return createTextFile(userId, parentId, fileName, textContent, "UTF-8", null);
    }

    protected Document createTextFile(String userId, String parentId, String fileName, String textContent, String encoding, Map<String,String> props) throws IOException, Exception
    {
        return createTextFile(userId, parentId, fileName, textContent, encoding, props, 201);
    }

    protected Document createTextFile(String userId, String parentId, String fileName, String textContent, String encoding, Map<String,String> props, int expectedStatus) throws IOException, Exception
    {
        if (props == null)
        {
            props = Collections.EMPTY_MAP;

        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(textContent.getBytes());
        File txtFile = TempFileProvider.createTempFile(inputStream, getClass().getSimpleName(), ".txt");

        MultiPartBuilder.MultiPartRequest reqBody = MultiPartBuilder.create()
                .setFileData(new MultiPartBuilder.FileData(fileName, txtFile))
                .setProperties(props)
                .build();

        HttpResponse response = post(getNodeChildrenUrl(parentId), userId, reqBody.getBody(), null, reqBody.getContentType(), expectedStatus);

        if (response.getJsonResponse().get("error") != null)
        {
            return null;
        }

        return RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Document.class);
    }

    protected Document updateTextFile(String userId, String contentId, String textContent, Map<String,String> parameters) throws IOException, Exception
    {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(textContent.getBytes());
        File txtFile = TempFileProvider.createTempFile(inputStream, getClass().getSimpleName(), ".txt");
        BinaryPayload payload = new BinaryPayload(txtFile);

        HttpResponse response = putBinary(getNodeContentUrl(contentId), userId, payload, null, parameters, 200);
        return RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Document.class);
    }

    protected File getResourceFile(String fileName) throws FileNotFoundException
    {
        URL url = NodeApiTest.class.getClassLoader().getResource(RESOURCE_PREFIX + fileName);
        if (url == null)
        {
            fail("Cannot get the resource: " + fileName);
        }
        return ResourceUtils.getFile(url);
    }

    protected static final long PAUSE_TIME = 5000; //millisecond
    protected static final int MAX_RETRY = 20;

    protected Rendition waitAndGetRendition(String userId, String sourceNodeId, String renditionId) throws Exception
    {
        int retryCount = 0;
        while (retryCount < MAX_RETRY)
        {
            try
            {
                HttpResponse response = getSingle(getNodeRenditionsUrl(sourceNodeId), userId, renditionId, 200);
                Rendition rendition = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Rendition.class);
                assertNotNull(rendition);
                assertEquals(Rendition.RenditionStatus.CREATED, rendition.getStatus());
                return rendition;
            }
            catch (AssertionError ex)
            {
                // If the asynchronous create rendition action is not finished yet,
                // wait for 'PAUSE_TIME' and try again.
                retryCount++;

                System.out.println("waitAndGetRendition: "+retryCount);
                Thread.sleep(PAUSE_TIME);
            }
        }

        return null;
    }

    protected Rendition createAndGetRendition(String userId, String sourceNodeId, String renditionId) throws Exception
    {
        Rendition renditionRequest = new Rendition();
        renditionRequest.setId(renditionId);

        int retryCount = 0;
        while (retryCount < MAX_RETRY)
        {
            try
            {
                HttpResponse res = post(getNodeRenditionsUrl(sourceNodeId), userId, toJsonAsString(renditionRequest), 202);
                assertNull(res.getJsonResponse());
                break;
            }
            catch (AssertionError ex)
            {
                // If no transformer is currently available,
                // wait for 'PAUSE_TIME' and try again.
                retryCount++;

                System.out.println("waitAndGetRendition: "+retryCount);
                Thread.sleep(PAUSE_TIME);
            }
        }

        return waitAndGetRendition(userId, sourceNodeId, renditionId);
    }

    protected String getNodeRenditionsUrl(String nodeId)
    {
        return URL_NODES + "/" + nodeId + "/" + URL_RENDITIONS;
    }

    protected String getNodeChildrenUrl(String nodeId)
    {
        return URL_NODES + "/" + nodeId + "/" + URL_CHILDREN;
    }

    protected String getNodeContentUrl(String nodeId)
    {
        return URL_NODES + "/" + nodeId + "/" + URL_CONTENT;
    }
}

