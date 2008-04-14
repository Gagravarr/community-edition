<%@ page import="org.alfresco.tools.*" %>
<%@ page import="org.alfresco.web.site.*" %>
<%@ page import="org.alfresco.web.site.model.*" %>
<%@ page import="org.alfresco.web.site.config.*" %>
<%@ page buffer="0kb" contentType="text/html;charset=UTF-8" %>
<%@ page isELIgnored="false" %>
<%
	// get the request context
	RequestContext context = RequestUtil.getRequestContext(request);
	
	// get the configuration
	RuntimeConfig configuration = (RuntimeConfig) request.getAttribute("component-configuration");
	
        // config values
        String markupData = (String) configuration.get("markupData");

        // shimmy the data a bit
        if (markupData != null)
        {
            String data = EncodingUtil.decode(markupData);

            // print out
            out.println(data);
        }
        else
        {
            String currentThemeId = ThemeUtil.getCurrentThemeId(context);
            String unconfiguredImageUrl = URLUtil.toBrowserUrl("/ui/themes/builder/images/" + currentThemeId + "/icons/unconfigured_component_large.gif");
            String renderString = "<img src='" + unconfiguredImageUrl + "' border='0' alt='Unconfigured Google Gadget Component'/>";   
            out.println(renderString);
        }
%>