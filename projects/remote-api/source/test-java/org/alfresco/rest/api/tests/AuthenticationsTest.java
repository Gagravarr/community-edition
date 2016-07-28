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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.rest.api.Nodes;
import org.alfresco.rest.api.People;
import org.alfresco.rest.api.model.LoginTicket;
import org.alfresco.rest.api.model.LoginTicketResponse;
import org.alfresco.rest.api.sites.SiteEntityResource;
import org.alfresco.rest.api.tests.client.HttpResponse;
import org.alfresco.rest.api.tests.client.PublicApiClient.Paging;
import org.alfresco.rest.api.tests.client.data.Document;
import org.alfresco.rest.api.tests.client.data.Folder;
import org.alfresco.rest.api.tests.util.RestApiUtil;
import org.alfresco.service.cmr.security.MutableAuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.apache.commons.codec.binary.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Authentication tickets API tests.
 *
 * @author Jamal Kaabi-Mofrad
 */
public class AuthenticationsTest extends AbstractBaseApiTest
{
    private static final String TICKETS_URL = "tickets";
    private static final String TICKETS_API_NAME = "authentication";

    private String user1;
    private String user2;
    private List<String> users = new ArrayList<>();
    private MutableAuthenticationService authenticationService;
    private PersonService personService;

    @Before
    public void setup() throws Exception
    {
        authenticationService = applicationContext.getBean("authenticationService", MutableAuthenticationService.class);
        personService = applicationContext.getBean("personService", PersonService.class);

        user1 = createUser("user1" + System.currentTimeMillis(), "user1Password");
        user2 = createUser("user2" + System.currentTimeMillis(), "user2Password");

        users.add(user1);
        users.add(user2);
        AuthenticationUtil.clearCurrentSecurityContext();
    }

    @After
    public void tearDown() throws Exception
    {
        AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();
        for (final String user : users)
        {
            transactionHelper.doInTransaction(new RetryingTransactionCallback<Void>()
            {
                @Override
                public Void execute() throws Throwable
                {
                    if (personService.personExists(user))
                    {
                        authenticationService.deleteAuthentication(user);
                        personService.deletePerson(user);
                    }
                    return null;
                }
            });
        }
        users.clear();
        AuthenticationUtil.clearCurrentSecurityContext();
    }

    /**
     * Tests login (create ticket), logout (delete ticket), and validate (get ticket).
     *
     * <p>POST:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/authentication/versions/1/tickets}
     *
     * <p>GET:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/authentication/versions/1/tickets/-me-}
     *
     * <p>DELETE:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/authentication/versions/1/tickets/-me-}
     */
    @Test
    public void testCreateValidateDeleteTicket() throws Exception
    {
        Paging paging = getPaging(0, 100);
        // Unauthorized call
        getAll(SiteEntityResource.class, null, paging, null, 401);

        /*
         *  user1 login - via alf_ticket parameter
         */

        // User1 login request
        LoginTicket loginRequest = new LoginTicket();
        // Invalid login details
        post(TICKETS_URL, null, RestApiUtil.toJsonAsString(loginRequest), null, null, TICKETS_API_NAME, 400);

        loginRequest.setUserId(null);
        loginRequest.setPassword("user1Password");
        // Invalid login details
        post(TICKETS_URL, null, RestApiUtil.toJsonAsString(loginRequest), null, null, TICKETS_API_NAME, 400);

        loginRequest.setUserId(user1);
        loginRequest.setPassword(null);
        // Invalid login details
        post(TICKETS_URL, null, RestApiUtil.toJsonAsString(loginRequest), null, null, TICKETS_API_NAME, 400);

        loginRequest.setUserId(user1);
        loginRequest.setPassword("user1Password");
        // Authenticate and create a ticket
        HttpResponse response = post(TICKETS_URL, null, RestApiUtil.toJsonAsString(loginRequest), null, null, TICKETS_API_NAME, 201);
        LoginTicketResponse loginResponse = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), LoginTicketResponse.class);
        assertNotNull(loginResponse.getId());
        assertNotNull(loginResponse.getUserId());

        // Get list of sites by appending the alf_ticket to the URL
        // e.g. .../alfresco/versions/1/sites/?alf_ticket=TICKET_57866258ea56c28491bb3e75d8355ebf6fbaaa23
        Map<String, String> ticket = Collections.singletonMap("alf_ticket", loginResponse.getId());
        getAll(SiteEntityResource.class, null, paging, ticket, 200);

        // Unauthorized - Invalid ticket
        getAll(SiteEntityResource.class, null, paging, Collections.singletonMap("alf_ticket", "TICKET_" + System.currentTimeMillis()), 401);

        // Validate ticket - Invalid parameter. Only '-me-' is supported
        getSingle(TICKETS_URL, null, loginResponse.getId(), ticket, null, TICKETS_API_NAME, 400);

        // Validate ticket
        response = getSingle(TICKETS_URL, null, People.DEFAULT_USER, ticket, null, TICKETS_API_NAME, 200);
        LoginTicketResponse validatedTicket = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), LoginTicketResponse.class);
        assertEquals(loginResponse.getId(), validatedTicket.getId());

        // Validate ticket - Invalid parameter. Only '-me-' is supported
        getSingle(TICKETS_URL, null, loginResponse.getId(), ticket, null, TICKETS_API_NAME, 400);

        // Delete the ticket  - Logout
        delete(TICKETS_URL, null, People.DEFAULT_USER, ticket, null, TICKETS_API_NAME, 204);

        // Validate ticket - 401 as ticket has been invalidated so the API call is unauthorized
        getSingle(TICKETS_URL, null, People.DEFAULT_USER, ticket, null, TICKETS_API_NAME, 401);
        // Check the ticket has been invalidated - the difference with the above is that the API call is authorized
        getSingle(TICKETS_URL, user1, People.DEFAULT_USER, ticket, null, TICKETS_API_NAME, 404);

        // Ticket has already been invalidated
        delete(TICKETS_URL, user1, People.DEFAULT_USER, ticket, null, TICKETS_API_NAME, 404);

        // Get list of site by appending the invalidated ticket
        getAll(SiteEntityResource.class, null, paging, ticket, 401);


        /*
         *  user2 login - Via Authorization header
         */

        // User2 create a folder within his home folder (-my-)
        Folder folderResp = createFolder(user2, Nodes.PATH_MY, "F2", null);
        assertNotNull(folderResp.getId());

        getAll(getNodeChildrenUrl(Nodes.PATH_MY), null, paging, 401);

        // User2 login request
        loginRequest = new LoginTicket();
        loginRequest.setUserId(user2);
        loginRequest.setPassword("wrongPassword");
        // Authentication failed - wrong password
        post(TICKETS_URL, null, RestApiUtil.toJsonAsString(loginRequest), null, null, TICKETS_API_NAME, 403);

        loginRequest.setUserId(user1);
        loginRequest.setPassword("user2Password");
        // Authentication failed - userId/password mismatch
        post(TICKETS_URL, null, RestApiUtil.toJsonAsString(loginRequest), null, null, TICKETS_API_NAME, 403);

        // Set the correct details
        loginRequest.setUserId(user2);
        loginRequest.setPassword("user2Password");
        // Authenticate and create a ticket
        response = post(TICKETS_URL, null, RestApiUtil.toJsonAsString(loginRequest), null, null, TICKETS_API_NAME, 201);
        loginResponse = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), LoginTicketResponse.class);
        assertNotNull(loginResponse.getId());
        assertNotNull(loginResponse.getUserId());

        String encodedTicket = encodeB64(loginResponse.getId());
        // Set the authorization (encoded ticket only) header rather than appending the ticket to the URL
        Map<String, String> header = Collections.singletonMap("Authorization", "Basic " + encodedTicket);
        // Get children of user2 home folder
        response = getAll(getNodeChildrenUrl(Nodes.PATH_MY), null, paging, null, header, 200);
        List<Document> nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Document.class);
        assertEquals(1, nodes.size());

        // Validate ticket - Invalid parameter. Only '-me-' is supported
        getSingle(TICKETS_URL, null, loginResponse.getId(), null, header, TICKETS_API_NAME, 400);

        // Validate ticket - user2
        response = getSingle(TICKETS_URL, null, People.DEFAULT_USER, null, header, TICKETS_API_NAME, 200);
        validatedTicket = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), LoginTicketResponse.class);
        assertEquals(loginResponse.getId(), validatedTicket.getId());

        // Try list children for user2 again.
        // Encode Alfresco predefined userId for ticket authentication, ROLE_TICKET, and the ticket
        String encodedUserIdAndTicket = encodeB64("ROLE_TICKET:" + loginResponse.getId());
        // Set the authorization (encoded userId:ticket) header rather than appending the ticket to the URL
        header = Collections.singletonMap("Authorization", "Basic " + encodedUserIdAndTicket);
        // Get children of user2 home folder
        response = getAll(getNodeChildrenUrl(Nodes.PATH_MY), null, paging, null, header, 200);
        nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Document.class);
        assertEquals(1, nodes.size());

        // Try list children for user2 again - appending ticket
        ticket = Collections.singletonMap("alf_ticket", loginResponse.getId());
        response = getAll(getNodeChildrenUrl(Nodes.PATH_MY), null, paging, ticket, 200);
        nodes = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Document.class);
        assertEquals(1, nodes.size());

        // Try to validate the ticket without supplying the Authorization header or the alf_ticket param
        getSingle(TICKETS_URL, user2, People.DEFAULT_USER, null, null, TICKETS_API_NAME, 400);

        // Delete the ticket  - Invalid parameter. Only '-me-' is supported
        header = Collections.singletonMap("Authorization", "Basic " + encodedUserIdAndTicket);
        delete(TICKETS_URL, null, loginResponse.getId(), null, header, TICKETS_API_NAME, 400);

        // Delete the ticket  - Logout
        delete(TICKETS_URL, null, People.DEFAULT_USER, null, header, TICKETS_API_NAME, 204);

        // Get children of user2 home folder - invalidated ticket
        getAll(getNodeChildrenUrl(Nodes.PATH_MY), null, paging, null, header, 401);
    }

    private String encodeB64(String str)
    {
        return Base64.encodeBase64String(str.getBytes());
    }

    @Override
    public String getScope()
    {
        return "public";
    }
}
