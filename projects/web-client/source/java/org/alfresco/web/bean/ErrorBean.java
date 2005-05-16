package org.alfresco.web.bean;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Bean used by the error page, holds the last exception to be thrown by the system
 * 
 * @author gavinc
 */
public class ErrorBean
{
   public static final String ERROR_BEAN_NAME = "alfresco.ErrorBean";
   
   private String returnPage;
   private Throwable lastError;

   /**
    * @return Returns the page to go back to after the error has been displayed
    */
   public String getReturnPage()
   {
      return returnPage;
   }

   /**
    * @param returnPage The page to return to after showing the error
    */
   public void setReturnPage(String returnPage)
   {
      this.returnPage = returnPage;
   }

   /**
    * @return Returns the lastError.
    */
   public Throwable getLastError()
   {
      return lastError;
   }

   /**
    * @param lastError The lastError to set.
    */
   public void setLastError(Throwable lastError)
   {
      this.lastError = lastError;
   }
   
   /**
    * @return Returns the last error to occur in string form
    */
   public String getLastErrorMessage()
   {
      String message = "No error currently stored";
      
      if (this.lastError != null)
      {
         StringBuilder builder = new StringBuilder(this.lastError.toString());
         Throwable cause = this.lastError.getCause();
         while (cause != null)
         {
            builder.append("<br/><br/>caused by:<br/>");
            builder.append(cause.toString());
            cause = cause.getCause();
         }
         
         message = builder.toString();
      }
      
      return message;
   }
   
   /**
    * @return Returns the stack trace for the last error
    */
   public String getStackTrace()
   {
      StringWriter stringWriter = new StringWriter();
      PrintWriter writer = new PrintWriter(stringWriter);
      this.lastError.printStackTrace(writer);
      String stackTrace = stringWriter.toString().replaceAll("\r\n", "<br/>");
      return stackTrace;
   }
}
