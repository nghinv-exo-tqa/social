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
package org.exoplatform.social.portlet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.SpaceMemberFilterListAccess.Type;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileFilter;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.webui.Utils;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.web.application.RequestContext;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormStringInput;
import org.exoplatform.webui.form.validator.MandatoryValidator;

@ComponentConfig(
  lifecycle = UIFormLifecycle.class,
  template = "app:/groovy/social/portlet/UIUserInvitation.gtmpl",
  events = {
      @EventConfig(listeners = UIUserInvitation.InviteActionListener.class)
  }
)
public class UIUserInvitation extends UIForm {
  private static final String USER = "user";
  private SpaceService spaceService;
  private String spaceUrl;

  public UIUserInvitation() throws Exception {
    addUIFormInput(new UIFormStringInput(USER, null, null).addValidator(MandatoryValidator.class));

    spaceUrl = org.exoplatform.social.core.space.SpaceUtils.getSpaceUrlByContext();
  }

  /**
   * Gets spaceService
   *
   * @return spaceService
   * @see SpaceService
   */
  private SpaceService getSpaceService() {
    if (spaceService == null) {
      spaceService = getApplicationComponent(SpaceService.class);
    }
    return spaceService;
  }

  public String getRestURL() {
    StringBuilder builder = new StringBuilder();
    builder.append("/").append(PortalContainer.getCurrentRestContextName()).append("/social/people/suggest.json?");
    builder.append("currentUser=").append(RequestContext.getCurrentInstance().getRemoteUser());
    builder.append("&spaceURL=").append(spaceUrl);
    builder.append("&typeOfRelation=").append("user_to_invite");
    return builder.toString();
  }

  public void addMessage(String msg) {
    UIMembersPortlet parent = getAncestorOfType(UIMembersPortlet.class);
    parent.addFlashMessage(msg);
  }

  /**
   * Validates invited users for checking if any error happens.
   *
   * @throws Exception
   */
  private String validateInvitedUser(String userNameForInvite) throws Exception {
    WebuiRequestContext requestContext = WebuiRequestContext.getCurrentInstance();
    UIApplication uiApp = requestContext.getUIApplication();
    String[] invitedUserList = userNameForInvite.split(",");
    String invitedUser = null;
    ApplicationMessage appMsg = null;
    String invitedUserNames = null;
    Set<String> validUsers = new HashSet<String>();
    Set<String> invitedUsers = new HashSet<String>();
    List<String> memberUsers = new ArrayList<String>();
    List<String> notExistUsers = new ArrayList<String>();
    SpaceService spaceService = getSpaceService();
    for (String userStr : invitedUserList) {
      // If it's a space
      if (userStr.startsWith("space::")) {
        String spaceName = userStr.substring("space::".length());
        Space space = spaceService.getSpaceByPrettyName(spaceName);
        ProfileFilter filter = new ProfileFilter();
        filter.getExcludedIdentityList().add(Utils.getViewerIdentity());
        ListAccess<Identity> loader = Utils.getIdentityManager().getSpaceIdentityByProfileFilter(space, filter, Type.MEMBER, true);
        Identity[] identities = loader.load(0, loader.getSize());
        for (Identity i : identities) {
          invitedUser = i.getRemoteId();
          if (isMember(invitedUser)) {
            memberUsers.add(invitedUser);
          } else if (isNotExisted(invitedUser)){
            notExistUsers.add(invitedUser);
          } else if (hasInvited(invitedUser)) {
            invitedUsers.add(invitedUser);
          } else {
            validUsers.add(invitedUser);
          }
        }
      } else { // Otherwise, it's an user
        invitedUser = userStr.trim();
        
        if (invitedUser.length() == 0) {
          continue;
        }
        
        if (isMember(invitedUser)) {
          memberUsers.add(invitedUser);
        } else if (isNotExisted(invitedUser)){
          notExistUsers.add(invitedUser);
        } else if (hasInvited(invitedUser)) {
          invitedUsers.add(invitedUser);
        } else {
          validUsers.add(invitedUser);
        }
      }
    }
    
    if (validUsers.size() > 0) {
      getUIStringInput(USER).setValue(StringUtils.join(validUsers, ','));
      invitedUserNames = StringUtils.join(validUsers, ',');
    }
    
    if (notExistUsers.size() > 0) {
//      appMsg = new ApplicationMessage("UISpaceMember.msg.user-not-exist",
//                                      notExistUsers.toArray(new String[notExistUsers.size()]),
//                                      ApplicationMessage.WARNING);
      appMsg.setArgsLocalized(false);
      uiApp.addMessage(appMsg);

      addMessage("UISpaceMember.msg.user-not-exist");
    }
    
    if (invitedUsers.size() > 0) {
      appMsg = new ApplicationMessage("UISpaceMember.msg.user-is-invited",
                                      invitedUsers.toArray(new String[invitedUsers.size()]),
                                      ApplicationMessage.WARNING);
      appMsg.setArgsLocalized(false);
      uiApp.addMessage(appMsg);

      addMessage("UISpaceMember.msg.user-is-invited");
    }
    
    if (memberUsers.size() > 0) {
      appMsg = new ApplicationMessage("UISpaceMember.msg.user-is-member",
                                      memberUsers.toArray(new String[memberUsers.size()]),
                                      ApplicationMessage.WARNING);
      appMsg.setArgsLocalized(false);
      uiApp.addMessage(appMsg);

      addMessage("UISpaceMember.msg.user-is-member");
    }
    return invitedUserNames;
  }

  protected boolean isMember(String userId) {
    Space space = org.exoplatform.social.webui.Utils.getSpaceByContext();
    try {
      if (ArrayUtils.contains(space.getMembers(), userId)) {
        return true;
      }
    } catch (Exception e) {
      return false;
    }
    return false;
  }
  
  private boolean hasInvited(String userId) {
    SpaceService spaceService = getSpaceService();
    Space space = org.exoplatform.social.webui.Utils.getSpaceByContext();
    try {
      if (spaceService.isInvitedUser(space, userId)) {
        return true;
      }
    } catch (Exception e) {
      return false;
    }
    return false;
  }
  
  private boolean isNotExisted(String userId) {
    OrganizationService orgService = getApplicationComponent(OrganizationService.class);
    try {
      User user = orgService.getUserHandler().findUserByName(userId);
      
      if (user != null) {
        return false;
      }
    } catch (Exception e) {
      return true;
    }
    return true;
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
      String invitedUserNames = uiComponent.validateInvitedUser(input.getValue());
      Space space = org.exoplatform.social.webui.Utils.getSpaceByContext();
      
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
