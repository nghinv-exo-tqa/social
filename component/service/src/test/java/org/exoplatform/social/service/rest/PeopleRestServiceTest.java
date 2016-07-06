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
package org.exoplatform.social.service.rest;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import junit.framework.AssertionFailedError;

import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.impl.UserImpl;
import org.exoplatform.services.rest.impl.ContainerResponse;
import org.exoplatform.services.rest.impl.MultivaluedMapImpl;
import org.exoplatform.services.rest.tools.ByteArrayContainerResponseWriter;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.manager.RelationshipManager;
import org.exoplatform.social.core.relationship.model.Relationship;
import org.exoplatform.social.core.space.impl.DefaultSpaceApplicationHandler;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.service.test.AbstractResourceTest;

import java.util.LinkedList;
import java.util.List;

public class PeopleRestServiceTest  extends AbstractResourceTest {
  static private PeopleRestService peopleRestService;

  private OrganizationService orgService;
  private IdentityManager identityManager;
  private SpaceService spaceService;
  private RelationshipManager relationshipManager;
  private ActivityManager activityManager;

  private List<ExoSocialActivity> activitiesToDelete = new LinkedList<>();
  private List<Relationship> relationshipToDelete = new LinkedList<>();
  private List<String> userToDelete = new LinkedList<>();
  private List<Identity> identityToDelete = new LinkedList<>();
  private List<Space> spaceToDelete = new LinkedList<>();

  Identity root;

  public void setUp() throws Exception {
    super.setUp();
    peopleRestService = new PeopleRestService();
    registry(peopleRestService);

    activityManager = getService(ActivityManager.class);
    orgService = getService(OrganizationService.class);
    identityManager = getService(IdentityManager.class);
    spaceService = getService(SpaceService.class);
    relationshipManager = getService(RelationshipManager.class);

    activitiesToDelete = new LinkedList<>();
    relationshipToDelete = new LinkedList<>();
    userToDelete = new LinkedList<>();
    identityToDelete = new LinkedList<>();
    spaceToDelete = new LinkedList<>();

    root = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "root", true);
    startSessionAs("root");
  }

  public void tearDown() throws Exception {
    endSession();
    for (ExoSocialActivity activity : activitiesToDelete) {
      activityManager.deleteActivity(activity);
    }
    for (Relationship r : relationshipToDelete) {
      relationshipManager.delete(r);
    }
    for (String user : userToDelete) {
      orgService.getUserHandler().removeUser(user, true);
    }
    for (Identity id : identityToDelete) {
      identityManager.deleteIdentity(id);
    }
    for (Space space : spaceToDelete) {
      spaceService.deleteSpace(space);
    }

    super.tearDown();
    unregistry(peopleRestService);
  }

  public void testSuggestUsernames() throws Exception {
    MultivaluedMap<String, String> h = new MultivaluedMapImpl();
    String username = "root";
    h.putSingle("username", username);
    ByteArrayContainerResponseWriter writer = new ByteArrayContainerResponseWriter();
    ContainerResponse response = service("GET", "/social/people/suggest.json?nameToSearch=R&currentUser=root", "", h, null, writer);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    assertEquals("application/json;charset=utf-8", response.getContentType().toString());
    if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode())
      throw new AssertionFailedError("Service not found");
  }

  public void testSuggestInviteToSpace() throws Exception {
    Identity user1 = createUser("user1", "space1", "User1");
    Identity user2 = createUser("user2", "space1", "User2");
    Identity user3 = createUser("user3", "space1", "User3");
    Identity user4 = createUser("user4", "space1", "User4");
    Identity user5 = createUser("user5", "space1", "User5");
    Identity user6 = createUser("user6", "space1", "User6");
    Identity user7 = createUser("user7", "space1", "User7");
    Identity user8 = createUser("user8", "space1", "User8");
    Identity user9 = createUser("user9", "space1", "User9");

    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user2));
    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user5));
    relationshipManager.confirm(root, user2);
    relationshipManager.confirm(root, user5);

    Space space = this.createSpace("Space 1");

    spaceService.addMember(space, user4.getRemoteId());
    spaceService.addMember(space, user6.getRemoteId());

    MultivaluedMap<String, String> h = new MultivaluedMapImpl();
    ByteArrayContainerResponseWriter writer = new ByteArrayContainerResponseWriter();
    ContainerResponse response = service("GET", "/social/people/suggest.json?nameToSearch=space1&currentUser=root&typeOfRelation=user_to_invite&spaceURL=" + space.getUrl(), "", h, null, writer);
    assertEquals(200, response.getStatus());
    PeopleRestService.UserNameList userNameList = (PeopleRestService.UserNameList) response.getEntity();
    assertEquals(7, userNameList.getNames().size());

    // Found in relationship first
    assertIndex(userNameList, "user2", 0, 1);
    assertIndex(userNameList, "user5", 0, 1);

    // And others
    assertIndex(userNameList, "user1", 2, 7);
    assertIndex(userNameList, "user3", 2, 7);
    assertIndex(userNameList, "user7", 2, 7);
    assertIndex(userNameList, "user8", 2, 7);
    assertIndex(userNameList, "user9", 2, 7);
  }

  public void testGetSuggestionWithRelationship() throws Exception {
    Identity user1 = createUser("user1", "space1", "User1");
    Identity user2 = createUser("user2", "space1", "User2");
    Identity user3 = createUser("user3", "space1", "User3");
    Identity user4 = createUser("user4", "space1", "User4");
    Identity user5 = createUser("user5", "space1", "User5");

    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user2));
    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user5));
    relationshipManager.confirm(root, user2);
    relationshipManager.confirm(root, user5);

    MultivaluedMap<String, String> h = new MultivaluedMapImpl();
    ByteArrayContainerResponseWriter writer = new ByteArrayContainerResponseWriter();
    ContainerResponse response = service("GET", "/social/people/getprofile/data.json?search=space1", "", h, null, writer);
    assertEquals(200, response.getStatus());
    List<PeopleRestService.UserInfo> userInfos = (List<PeopleRestService.UserInfo>) response.getEntity();
    assertEquals(5, userInfos.size());
    // Found in relationship first
    assertIndex(userInfos, "@user2", 0, 1);
    assertIndex(userInfos, "@user5", 0, 1);

    // And others
    assertIndex(userInfos, "@user1", 2, 4);
    assertIndex(userInfos, "@user3", 2, 4);
    assertIndex(userInfos, "@user4", 2, 4);
  }

  public void testGetSuggestionInSpace() throws Exception {
    Space space1 = createSpace("space1");

    Identity user1 = createUser("user1", "User1", "space1");
    Identity user2 = createUser("user2", "User2", "space1");
    Identity user3 = createUser("user3", "User3", "space1");
    Identity user4 = createUser("user4", "User4", "space1");
    Identity user5 = createUser("user5", "User5", "space1");

    spaceService.addMember(space1, user4.getRemoteId());
    spaceService.addMember(space1, user5.getRemoteId());

    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user2));
    relationshipManager.confirm(root, user2);

    MultivaluedMap<String, String> h = new MultivaluedMapImpl();
    ByteArrayContainerResponseWriter writer = new ByteArrayContainerResponseWriter();
    ContainerResponse response = service("GET", "/social/people/getprofile/data.json?search=space1&space=" + space1.getId(), "", h, null, writer);
    assertEquals(200, response.getStatus());
    List<PeopleRestService.UserInfo> userInfos = (List<PeopleRestService.UserInfo>) response.getEntity();
    assertEquals(5, userInfos.size());
    // Found in space member first
    assertEquals("@user4", userInfos.get(0).getId());
    assertEquals("@user5", userInfos.get(1).getId());
    // Then to connections
    assertEquals("@user2", userInfos.get(2).getId());
    // And others
    assertEquals("@user1", userInfos.get(3).getId());
    assertEquals("@user3", userInfos.get(4).getId());
  }

  public void testGetSuggestionInUserActivity() throws Exception {
    Identity user1 = createUser("user1", "space1", "User1");
    Identity user2 = createUser("user2", "space1", "User2");
    Identity user3 = createUser("user3", "space1", "User3");
    Identity user4 = createUser("user4", "space1", "User4");
    Identity user5 = createUser("user5", "space1", "User5");
    Identity user6 = createUser("user6", "space1", "User6");
    Identity user7 = createUser("user7", "space1", "User7");
    Identity user8 = createUser("user8", "space1", "User8");
    Identity user9 = createUser("user9", "space1", "User9");
    Identity user10 = createUser("user10", "space1", "User10");

    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user2));
    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user5));
    relationshipManager.confirm(root, user2);
    relationshipManager.confirm(root, user5);

    ExoSocialActivityImpl activity = new ExoSocialActivityImpl();
    activity.setTitle("Test activity");
    // Mentioned
    activity.setMentionedIds(new String[] {user8.getId(), user10.getId()});
    activityManager.saveActivityNoReturn(root, activity);
    activitiesToDelete.add(activity);

    // Liker
    activity.setLikeIdentityIds(new String[] {user3.getId(), user7.getId()});
    activityManager.updateActivity(activity);

    // Comment on activity
    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("activity comment");
    comment.setUserId(user1.getId());
    activityManager.saveComment(activity, comment);


    MultivaluedMap<String, String> h = new MultivaluedMapImpl();
    ByteArrayContainerResponseWriter writer = new ByteArrayContainerResponseWriter();
    ContainerResponse response = service("GET", "/social/people/getprofile/data.json?search=space1&activity=" + activity.getId(), "", h, null, writer);
    assertEquals(200, response.getStatus());
    List<PeopleRestService.UserInfo> userInfos = (List<PeopleRestService.UserInfo>) response.getEntity();
    assertEquals(10, userInfos.size());
    // Who commented
    assertIndex(userInfos, "@user1", 0, 0);

    // Who mentioned
    assertIndex(userInfos, "@user8", 1, 2);
    assertIndex(userInfos, "@user10", 1, 2);

    // Who liked the activity
    assertIndex(userInfos, "@user3", 3, 4);
    assertIndex(userInfos, "@user7", 3, 4);

    // Then in connections
    assertIndex(userInfos, "@user2", 5, 6);
    assertIndex(userInfos, "@user5", 5, 6);

    // And others
    assertIndex(userInfos, "@user6", 7, 9);
    assertIndex(userInfos, "@user9", 7, 9);
    assertIndex(userInfos, "@user4", 7, 9);
  }

  public void testGetSuggestionInSpaceActivity() throws Exception {
    Identity user1 = createUser("user1", "space1", "User1");
    Identity user2 = createUser("user2", "space1", "User2");
    Identity user3 = createUser("user3", "space1", "User3");
    Identity user4 = createUser("user4", "space1", "User4");
    Identity user5 = createUser("user5", "space1", "User5");
    Identity user6 = createUser("user6", "space1", "User6");
    Identity user7 = createUser("user7", "space1", "User7");
    Identity user8 = createUser("user8", "space1", "User8");
    Identity user9 = createUser("user9", "space1", "User9");
    Identity user10 = createUser("user10", "space1", "User10");

    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user2));
    relationshipToDelete.add(relationshipManager.inviteToConnect(root, user5));
    relationshipManager.confirm(root, user2);
    relationshipManager.confirm(root, user5);

    Space space = this.createSpace("Space 1");

    spaceService.addMember(space, user4.getRemoteId());
    spaceService.addMember(space, user6.getRemoteId());

    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), true);
    identityToDelete.add(spaceIdentity);

    ExoSocialActivityImpl activity = new ExoSocialActivityImpl();
    activity.setTitle("Test activity");
    activity.setUserId(root.getId());

    // Mentioned
    activity.setMentionedIds(new String[] {user8.getId(), user10.getId()});
    activityManager.saveActivityNoReturn(spaceIdentity, activity);
    activitiesToDelete.add(activity);

    // Liker
    activity.setLikeIdentityIds(new String[] {user3.getId(), user7.getId()});
    activityManager.updateActivity(activity);

    // Comment on activity
    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("activity comment");
    comment.setUserId(user1.getId());
    activityManager.saveComment(activity, comment);
    activitiesToDelete.add(0, comment);

    MultivaluedMap<String, String> h = new MultivaluedMapImpl();
    ByteArrayContainerResponseWriter writer = new ByteArrayContainerResponseWriter();
    ContainerResponse response = service("GET", "/social/people/getprofile/data.json?search=space1&activity=" + activity.getId(), "", h, null, writer);
    assertEquals(200, response.getStatus());
    List<PeopleRestService.UserInfo> userInfos = (List<PeopleRestService.UserInfo>) response.getEntity();
    assertEquals(10, userInfos.size());
    // Who commented
    assertIndex(userInfos, "@user1", 0, 0);

    // Who mentioned
    assertIndex(userInfos, "@user8", 1, 2);
    assertIndex(userInfos, "@user10", 1, 2);

    // Who liked the activity
    assertIndex(userInfos, "@user3", 3, 4);
    assertIndex(userInfos, "@user7", 3, 4);

    // Space member
    assertIndex(userInfos, "@user4", 5, 6);
    assertIndex(userInfos, "@user6", 5, 6);

    // Then in connections
    assertIndex(userInfos, "@user2", 7, 8);
    assertIndex(userInfos, "@user5", 8, 8);

    // And others
    assertIndex(userInfos, "@user9", 9, 9);
  }

  private void assertIndex(PeopleRestService.UserNameList list, String item, int min, int max) {
    if (list.getNames() == null) {
      fail("List should not be null");
    }
    List<String> names = list.getNames();
    int index = names.indexOf(item);
    if (index == -1) {
      fail("User with name " + item + " should be found int name list");
    }
    assertTrue(index >= min);
    assertTrue(index <= max);
  }
  private void assertIndex(List<PeopleRestService.UserInfo> list, String id, int begin, int end) {
    int index = -1;
    for (int i = 0; i < list.size(); i++) {
      PeopleRestService.UserInfo u = list.get(i);
      if (u.getId().equals(id)) {
        index = i;
        break;
      }
    }
    if (index == -1) {
      fail("User with ID=" + id + " need to be found in list");
    }
    assertTrue("Identity id=" + id + " mus be found at index between " + begin + " and " + end, index <= end);
    assertTrue("Identity id=" + id + " mus be found at index between " + begin + " and " + end, index >= begin);
  }

  private Space createSpace(String name) {
    Space space = new Space();
    space.setDisplayName(name);
    space.setPrettyName(name);
    space.setType(DefaultSpaceApplicationHandler.NAME);
    space.setVisibility(Space.PUBLIC);
    space.setRegistration(Space.OPEN);
    space = spaceService.createSpace(space, "root");
    spaceToDelete.add(space);
    return space;
  }

  private Identity createUser(String username, String firstName, String lastName) throws Exception {
    User user = new UserImpl(username);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setDisplayName(firstName + " " + lastName);
    orgService.getUserHandler().createUser(user, true);
    userToDelete.add(username);


    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, username, true);
    identityToDelete.add(identity);

    return identity;
  }

  private <T> T getService(Class<T> clazz) {
    return getContainer().getComponentInstanceOfType(clazz);
  }
}
