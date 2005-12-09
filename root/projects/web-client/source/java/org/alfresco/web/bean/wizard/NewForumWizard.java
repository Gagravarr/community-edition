/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 * You may obtain a copy of the License at
 *
 *     http://www.gnu.org/licenses/lgpl.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.web.bean.wizard;

import java.util.List;

import javax.faces.context.FacesContext;

import org.alfresco.model.ForumModel;
import org.alfresco.web.ui.common.component.UIListItem;

/**
 * Wizard bean used for creating and editing forum spaces
 * 
 * @author gavinc
 */
public class NewForumWizard extends NewSpaceWizard
{
   protected String forumStatus;
   
   protected List<UIListItem> forumIcons;

   /**
    * Returns the status of the forum
    * 
    * @return The status of the forum
    */
   public String getForumStatus()
   {
      return this.forumStatus;
   }

   /**
    * Sets the status of the forum 
    * 
    * @param forumStatus The status
    */
   public void setForumStatus(String forumStatus)
   {
      this.forumStatus = forumStatus;
   }

   /**
    * @see org.alfresco.web.bean.wizard.AbstractWizardBean#init()
    */
   public void init()
   {
      super.init();
      
      this.spaceType = ForumModel.TYPE_FORUM.toString();
      this.forumStatus = "0";
   }

   /**
    * @see org.alfresco.web.bean.wizard.NewSpaceWizard#performCustomProcessing(javax.faces.context.FacesContext)
    */
   @Override
   protected void performCustomProcessing(FacesContext context)
   {
      // add or update the ForumModel.PROP_STATUS property depending on the editMode
   }
}
