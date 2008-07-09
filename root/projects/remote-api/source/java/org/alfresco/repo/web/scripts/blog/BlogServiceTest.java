/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
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
 * FLOSS exception.  You should have received a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.web.scripts.blog;

import java.util.ArrayList;
import java.util.List;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationComponent;
import org.alfresco.repo.site.SiteInfo;
import org.alfresco.repo.site.SiteModel;
import org.alfresco.repo.site.SiteService;
import org.alfresco.repo.web.scripts.BaseWebScriptTest;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.util.PropertyMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit Test to test Blog Web Script API
 * 
 * @author mruflin
 */
public class BlogServiceTest extends BaseWebScriptTest
{
	private static Log logger = LogFactory.getLog(BlogServiceTest.class);
	
    private AuthenticationService authenticationService;
    private AuthenticationComponent authenticationComponent;
    private PersonService personService;
    private SiteService siteService;
    
    private static final String USER_ADMIN = "admin";
    private static final String USER_ONE = "UserOneSecond";
    private static final String USER_TWO = "UserTwoSecond";
    private static final String SITE_SHORT_NAME_BLOG = "BlogSiteShortName";
    private static final String COMPONENT_BLOG = "blog";

    private static final String URL_BLOG_POST = "/blog/post/site/" + SITE_SHORT_NAME_BLOG + "/" + COMPONENT_BLOG + "/";
    private static final String URL_BLOG_POSTS = "/blog/site/" + SITE_SHORT_NAME_BLOG + "/" + COMPONENT_BLOG + "/posts";
    
    private List<String> posts = new ArrayList<String>(5);
    private List<String> drafts = new ArrayList<String>(5);

    
    // General methods

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        
        this.authenticationService = (AuthenticationService)getServer().getApplicationContext().getBean("AuthenticationService");
        this.authenticationComponent = (AuthenticationComponent)getServer().getApplicationContext().getBean("authenticationComponent");
        this.personService = (PersonService)getServer().getApplicationContext().getBean("PersonService");
        this.siteService = (SiteService)getServer().getApplicationContext().getBean("siteService");
        
        // Create test site
        // - only create the site if it doesn't already exist
        SiteInfo siteInfo = this.siteService.getSite(SITE_SHORT_NAME_BLOG);
        if (siteInfo == null)
        {
            this.siteService.createSite("BlogSitePreset", SITE_SHORT_NAME_BLOG, "BlogSiteTitle",
                "BlogSiteDescription", true);
        }
        
        // Create users
        createUser(USER_ONE, SiteModel.SITE_COLLABORATOR);
        createUser(USER_TWO, SiteModel.SITE_COLLABORATOR);
        
        // Do tests as inviter user
        this.authenticationComponent.setCurrentUser(USER_ONE);
    }
    
    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        
        // admin user required to delete user
        this.authenticationComponent.setCurrentUser(USER_ADMIN);
        
        // delete the inviter user
        personService.deletePerson(USER_ONE);
        personService.deletePerson(USER_TWO);
        
        // delete invite site
        siteService.deleteSite(SITE_SHORT_NAME_BLOG);
    }
    
    private void createUser(String userName, String role)
    {
        // if user with given user name doesn't already exist then create user
        if (this.authenticationService.authenticationExists(userName) == false)
        {
            // create user
            this.authenticationService.createAuthentication(userName, "password".toCharArray());
            
            // create person properties
            PropertyMap personProps = new PropertyMap();
            personProps.put(ContentModel.PROP_USERNAME, userName);
            personProps.put(ContentModel.PROP_FIRSTNAME, "FirstName123");
            personProps.put(ContentModel.PROP_LASTNAME, "LastName123");
            personProps.put(ContentModel.PROP_EMAIL, "FirstName123.LastName123@email.com");
            personProps.put(ContentModel.PROP_JOBTITLE, "JobTitle123");
            personProps.put(ContentModel.PROP_JOBTITLE, "Organisation123");
            
            // create person node for user
            this.personService.createPerson(personProps);
        }
        
        // add the user as a member with the given role
        this.siteService.setMembership(SITE_SHORT_NAME_BLOG, USER_ONE, role);
    }
    
    
    // Test helper methods 
    
    private JSONObject getRequestObject(String title, String content, String[] tags, boolean isDraft)
    throws Exception
    {
        JSONObject post = new JSONObject();
        if (title != null)
        {
        	post.put("title", title);
        }
        if (content != null)
        {
        	post.put("content", content);
        }
        if (tags != null)
        {
        	JSONArray arr = new JSONArray();
        	for(String s : tags)
        	{
        		arr.put(s);
        	}
        	post.put("tags", arr);
        }
        post.put("draft", isDraft);
        return post;
    }
    
    private JSONObject createPost(String title, String content, String[] tags, boolean isDraft, int expectedStatus)
    throws Exception
    {
        JSONObject post = getRequestObject(title, content, tags, isDraft);
	    MockHttpServletResponse response = postRequest(URL_BLOG_POSTS, expectedStatus, post.toString(), "application/json");
	    
	    if (expectedStatus != 200)
	    {
	    	return null;
	    }
	    
    	//logger.debug(response.getContentAsString());
    	JSONObject result = new JSONObject(response.getContentAsString());
    	JSONObject item = result.getJSONObject("item");
    	if (isDraft)
    	{
    		this.drafts.add(item.getString("name"));
    	}
    	else
    	{
    	    this.posts.add(item.getString("name"));
    	}
    	return item;
    }
    
    private JSONObject updatePost(String name, String title, String content, String[] tags, boolean isDraft, int expectedStatus)
    throws Exception
    {
    	JSONObject post = getRequestObject(title, content, tags, isDraft);
	    MockHttpServletResponse response = putRequest(URL_BLOG_POST + name, expectedStatus, post.toString(), "application/json");
	    
	    if (expectedStatus != 200)
	    {
	    	return null;
	    }

    	JSONObject result = new JSONObject(response.getContentAsString());
    	return result.getJSONObject("item");
    }
    
    private JSONObject getPost(String name, int expectedStatus)
    throws Exception
    {
    	MockHttpServletResponse response = getRequest(URL_BLOG_POST + name, expectedStatus);
    	if (expectedStatus == 200)
    	{
    		JSONObject result = new JSONObject(response.getContentAsString());
    		return result.getJSONObject("item");
    	}
    	else
    	{
    		return null;
    	}
    }
    
    private String getCommentsUrl(String nodeRef)
    {
    	return "/node/" + nodeRef.replace("://", "/") + "/comments";
    }
    
    private String getCommentUrl(String nodeRef)
    {
    	return "/comment/node/" + nodeRef.replace("://", "/");
    }
    
    private JSONObject createComment(String nodeRef, String title, String content, int expectedStatus)
    throws Exception
    {
        JSONObject comment = new JSONObject();
        comment.put("title", title);
        comment.put("content", content);
	    MockHttpServletResponse response = postRequest(getCommentsUrl(nodeRef), expectedStatus, comment.toString(), "application/json");
	    
	    if (expectedStatus != 200)
	    {
	    	return null;
	    }
	    
	    //logger.debug("Comment created: " + response.getContentAsString());
    	JSONObject result = new JSONObject(response.getContentAsString());
    	return result.getJSONObject("item");
    }
    
    private JSONObject updateComment(String nodeRef, String title, String content, int expectedStatus)
    throws Exception
    {
    	JSONObject comment = new JSONObject();
        comment.put("title", title);
        comment.put("content", content);
	    MockHttpServletResponse response = putRequest(getCommentUrl(nodeRef), expectedStatus, comment.toString(), "application/json");
	    
	    if (expectedStatus != 200)
	    {
	    	return null;
	    }
	    
	    //logger.debug("Comment updated: " + response.getContentAsString());
    	JSONObject result = new JSONObject(response.getContentAsString());
    	return result.getJSONObject("item");
    }
    
    
    // Tests
    
    public void testCreateDraftPost() throws Exception
    {
    	String title = "test";
    	String content = "test";
    	JSONObject item = createPost(title, content, null, true, 200);
    	
    	// check that the values
    	assertEquals(title, item.get("title"));
    	assertEquals(content, item.get("content"));
    	assertEquals(true, item.get("isDraft"));
    	
    	// check that other user doesn't have access to the draft
    	this.authenticationComponent.setCurrentUser(USER_TWO);
    	getPost(item.getString("name"), 404);
    	this.authenticationComponent.setCurrentUser(USER_ONE);
    }
    
    public void testCreatePublishedPost() throws Exception
    {
    	String title = "published";
    	String content = "content";
    	
    	JSONObject item = createPost(title, content, null, false, 200);
    	
    	// check the values
    	assertEquals(title, item.get("title"));
    	assertEquals(content, item.get("content"));
    	assertEquals(false, item.get("isDraft"));
    	
    	// check that user two has access to it as well
    	this.authenticationComponent.setCurrentUser(USER_TWO);
    	getPost(item.getString("name"), 200);
    	this.authenticationComponent.setCurrentUser(USER_ONE);
    }
    
    public void testCreateEmptyPost() throws Exception
    {
    	JSONObject item = createPost(null, null, null, false, 200);
    	
    	// check the values
    	assertEquals("", item.get("title"));
    	assertEquals("", item.get("content"));
    	assertEquals(false, item.get("isDraft"));
    	
    	// check that user two has access to it as well
    	this.authenticationComponent.setCurrentUser(USER_TWO);
    	getPost(item.getString("name"), 200);
    	this.authenticationComponent.setCurrentUser(USER_ONE);
    }
    
    public void testUpdated() throws Exception
    {
    	JSONObject item = createPost("test", "test", null, false, 200);
    	String name = item.getString("name");
    	assertEquals(false, item.getBoolean("isUpdated"));
    	
    	// wait for 5 sec
    	Thread.sleep(5000);
    	
    	item = updatePost(name, "new title", "new content", null, false, 200);
    	assertEquals(true, item.getBoolean("isUpdated"));
    	assertEquals("new title", item.getString("title"));
    	assertEquals("new content", item.getString("content"));
    }
    
    public void testUpdateWithEmptyValues() throws Exception
    {
    	JSONObject item = createPost("test", "test", null, false, 200);
    	String name = item.getString("name");
    	assertEquals(false, item.getBoolean("isUpdated"));
    	
    	item = updatePost(item.getString("name"), null, null, null, false, 200);
    	assertEquals("test", item.getString("title"));
    	assertEquals("test", item.getString("content"));
    }
    
    public void testPublishThroughUpdate() throws Exception
    {
    	JSONObject item = createPost("test", "test", null, true, 200);
    	String name = item.getString("name");
    	assertEquals(true, item.getBoolean("isDraft"));
    	
    	// check that user two does not have access
    	this.authenticationComponent.setCurrentUser(USER_TWO);
    	getPost(name, 404);
    	this.authenticationComponent.setCurrentUser(USER_ONE);
    	
    	item = updatePost(name, "new title", "new content", null, false, 200);
    	assertEquals("new title", item.getString("title"));
    	assertEquals("new content", item.getString("content"));
    	assertEquals(false, item.getBoolean("isDraft"));
    	
    	// check that user two does have access
    	this.authenticationComponent.setCurrentUser(USER_TWO);
    	getPost(name, 200);
    	this.authenticationComponent.setCurrentUser(USER_ONE);
    }

    public void testCannotDoUnpublish() throws Exception
    {
    	JSONObject item = createPost("test", "test", null, false, 200);
    	String name = item.getString("name");
    	assertEquals(false, item.getBoolean("isDraft"));
    	
    	item = updatePost(name, "new title", "new content", null, true, 400); // should return bad request
    }
    
    public void testGetAll() throws Exception
    {
    	String url = URL_BLOG_POSTS;
    	MockHttpServletResponse response = getRequest(url, 200);
    	JSONObject result = new JSONObject(response.getContentAsString());
    	
    	// we should have posts.size + drafts.size together
    	assertEquals(this.posts.size() + this.drafts.size(), result.getInt("total"));
    }
    
    public void testGetNew() throws Exception
    {
    	String url = URL_BLOG_POSTS + "/new";
    	MockHttpServletResponse response = getRequest(url, 200);
    	JSONObject result = new JSONObject(response.getContentAsString());
    	
    	// we should have posts.size
    	assertEquals(this.posts.size(), result.getInt("total"));
    }
    
    public void _testGetDrafts() throws Exception
    {
    	String url = URL_BLOG_POSTS + "/mydrafts";
    	MockHttpServletResponse response = getRequest(URL_BLOG_POSTS, 200);
    	JSONObject result = new JSONObject(response.getContentAsString());
    	
    	// we should have drafts.size resultss
    	assertEquals(this.drafts.size(), result.getInt("total"));
    	
    	// the second user should have zero
    	this.authenticationComponent.setCurrentUser(USER_TWO);
    	response = getRequest(url, 200);
    	result = new JSONObject(response.getContentAsString());
    	assertEquals(0, result.getInt("total"));
    	this.authenticationComponent.setCurrentUser(USER_ONE);
    	
    }
    
    public void _testMyPublished() throws Exception
    {
    	String url = URL_BLOG_POSTS + "/mypublished";
    	MockHttpServletResponse response = getRequest(url, 200);
    	JSONObject result = new JSONObject(response.getContentAsString());
    	
    	// we should have posts.size results
    	assertEquals(this.drafts.size(), result.getInt("total"));
    	
    	// the second user should have zero
    	this.authenticationComponent.setCurrentUser(USER_TWO);
    	response = getRequest(url, 200);
    	result = new JSONObject(response.getContentAsString());
    	assertEquals(0, result.getInt("total"));
    	this.authenticationComponent.setCurrentUser(USER_ONE);
    }

    public void testComments() throws Exception
    {
    	JSONObject item = createPost("test", "test", null, false, 200);
    	String name = item.getString("name");
    	String nodeRef = item.getString("nodeRef");
    	
    	JSONObject commentOne = createComment(nodeRef, "comment", "content", 200);
    	JSONObject commentTwo = createComment(nodeRef, "comment", "content", 200);
    	
    	// fetch the comments
    	MockHttpServletResponse response = getRequest(getCommentsUrl(nodeRef), 200);
    	JSONObject result = new JSONObject(response.getContentAsString());
    	assertEquals(2, result.getInt("total"));
    	
    	// add another one
    	JSONObject commentThree = createComment(nodeRef, "comment", "content", 200);
    	
    	response = getRequest(getCommentsUrl(nodeRef), 200);
    	result = new JSONObject(response.getContentAsString());
    	assertEquals(3, result.getInt("total"));
    	
    	// delete the last comment
    	response = deleteRequest(getCommentUrl(commentThree.getString("nodeRef")), 200);
    	
    	response = getRequest(getCommentsUrl(nodeRef), 200);
    	result = new JSONObject(response.getContentAsString());
    	assertEquals(2, result.getInt("total"));
    	
    	JSONObject commentTwoUpdated = updateComment(commentTwo.getString("nodeRef"), "new title", "new content", 200);
    	assertEquals("new title", commentTwoUpdated.getString("title"));
    	assertEquals("new content", commentTwoUpdated.getString("content"));
    }
    
    public void _testPostTags() throws Exception
    {
    	String[] tags = { "First", "Test" };
    	JSONObject item = createPost("tagtest", "tagtest", tags, false, 200);
    	assertEquals(2, item.getJSONArray("tags").length());
    	assertEquals("First", item.getJSONArray("tags").get(0));
    	assertEquals("Test", item.getJSONArray("tags").get(0));
    	
    	item = updatePost(item.getString("name"), null, null, new String[] { "First", "Test", "Second" }, false, 200);
    	assertEquals(3, item.getJSONArray("tags").length());
    	assertEquals("First", item.getJSONArray("tags").get(0));
    	assertEquals("Test", item.getJSONArray("tags").get(0));
    	assertEquals("Second", item.getJSONArray("tags").get(0));
    }
    
    public void _testClearTags() throws Exception
    {
    	String[] tags = { "abc", "def"};
    	JSONObject item = createPost("tagtest", "tagtest", tags, false, 200);
    	assertEquals(2, item.getJSONArray("tags").length());
    	
    	item = updatePost(item.getString("name"), null, null, new String[0], false, 200);
    	assertEquals(0, item.getJSONArray("tags").length());
    }
}