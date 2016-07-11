/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.webui.profile;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormStringInput;
import org.exoplatform.webui.form.validator.MandatoryValidator;

@ComponentConfig(
  lifecycle = UIFormLifecycle.class,
  template = "war:/groovy/social/webui/profile/UIUserInvitation.gtmpl",
  events = {
      @EventConfig(listeners = UIUserInvitation.InviteActionListener.class)
  }
)
public class UIUserInvitation extends UIForm {
  private static final String USER = "user";

  public UIUserInvitation() throws Exception {
    addUIFormInput(new UIFormStringInput(USER, null, null).addValidator(MandatoryValidator.class));
  }

  /**
   * Triggers this action when user click on "invite" button.
   *
   * @author hoatle
   */
  static public class InviteActionListener extends EventListener<UIUserInvitation> {
    public void execute(Event<UIUserInvitation> event) throws Exception {
      UIUserInvitation uiComponent = event.getSource();
      WebuiRequestContext requestContext = event.getRequestContext();
      SpaceService spaceService = uiComponent.getApplicationComponent(SpaceService.class);
      UIFormStringInput input = uiComponent.getUIStringInput(USER);
      String invitedUserNames = input.getValue();
      Space space = spaceService.getSpaceById("currentSpace");
      
      if (invitedUserNames != null) {
        String[] invitedUsers = invitedUserNames.split(",");
        String name = null;
        List<String> usersForInviting = new ArrayList<String>();
        if (invitedUsers != null) {
          for (int idx = 0; idx < invitedUsers.length; idx++) {
            name = invitedUsers[idx].trim();
            
            UserACL userACL = uiComponent.getApplicationComponent(UserACL.class);
            if (name.equals(userACL.getSuperUser())) {
              spaceService.addMember(space, name);
              continue;
            }
            
            if ((name.length() > 0) &&
                !usersForInviting.contains(name) &&
                !ArrayUtils.contains(space.getPendingUsers(), name)) {
              usersForInviting.add(name);
            }
          }
        }
        for (String userName : usersForInviting) {
          // create Identity and Profile nodes if not exist
          ExoContainer container = ExoContainerContext.getCurrentContainer();
          IdentityManager idm = (IdentityManager) container.getComponentInstanceOfType(IdentityManager.class);
          Identity identity = idm.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userName, false);
          if (identity != null) {
            // add userName to InvitedUser list of the space
            spaceService.addInvitedUser(space, userName);
          }
        }
        input.setValue(StringUtils.EMPTY);
      }
      
      requestContext.addUIComponentToUpdateByAjax(uiComponent);
    }
  }
}
