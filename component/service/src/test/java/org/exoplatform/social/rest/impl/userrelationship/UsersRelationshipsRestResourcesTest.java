package org.exoplatform.social.rest.impl.userrelationship;

import java.util.List;

import org.exoplatform.services.rest.impl.ContainerResponse;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.manager.RelationshipManager;
import org.exoplatform.social.core.relationship.model.Relationship;
import org.exoplatform.social.rest.api.RestProperties;
import org.exoplatform.social.rest.entity.CollectionEntity;
import org.exoplatform.social.rest.entity.DataEntity;
import org.exoplatform.social.service.rest.api.VersionResources;
import org.exoplatform.social.service.test.AbstractResourceTest;

public class UsersRelationshipsRestResourcesTest extends AbstractResourceTest {
  private IdentityManager identityManager;
  private RelationshipManager relationshipManager;
  
  private UsersRelationshipsRestResourcesV1 usersRelationshipsRestService;
  
  private Identity rootIdentity;
  private Identity johnIdentity;
  private Identity maryIdentity;
  private Identity demoIdentity;

  public void setUp() throws Exception {
    super.setUp();
    
    System.setProperty("gatein.email.domain.url", "localhost:8080");
    
    identityManager = (IdentityManager) getContainer().getComponentInstanceOfType(IdentityManager.class);
    relationshipManager = (RelationshipManager) getContainer().getComponentInstanceOfType(RelationshipManager.class);
    
    rootIdentity = identityManager.getOrCreateIdentity("organization", "root", true);
    johnIdentity = identityManager.getOrCreateIdentity("organization", "john", true);
    maryIdentity = identityManager.getOrCreateIdentity("organization", "mary", true);
    demoIdentity = identityManager.getOrCreateIdentity("organization", "demo", true);
    
    usersRelationshipsRestService = new UsersRelationshipsRestResourcesV1();
    registry(usersRelationshipsRestService);
  }

  public void tearDown() throws Exception {
    
    identityManager.deleteIdentity(rootIdentity);
    identityManager.deleteIdentity(johnIdentity);
    identityManager.deleteIdentity(maryIdentity);
    identityManager.deleteIdentity(demoIdentity);
    
    super.tearDown();
    removeResource(usersRelationshipsRestService.getClass());
  }
  
  public void testGetUserRelationships() throws Exception {
    Relationship relationship1 = new Relationship(rootIdentity, demoIdentity, Relationship.Type.CONFIRMED);
    relationshipManager.update(relationship1);
    Relationship relationship2 = new Relationship(rootIdentity, johnIdentity, Relationship.Type.PENDING);
    relationshipManager.update(relationship2);
    Relationship relationship3 = new Relationship(rootIdentity, maryIdentity, Relationship.Type.CONFIRMED);
    relationshipManager.update(relationship3);
    
    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("usersRelationships"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    List<? extends DataEntity> relationships = collections.getEntities();
    assertEquals(3, relationships.size());
    assertEquals("/rest/" + VersionResources.VERSION_ONE + "/social/users/root", relationships.get(0).get(RestProperties.SENDER));
    assertEquals("/rest/" + VersionResources.VERSION_ONE + "/social/users/mary", relationships.get(0).get(RestProperties.RECEIVER));
    
    //clean
    relationshipManager.delete(relationship1);
    relationshipManager.delete(relationship2);
    relationshipManager.delete(relationship3);
  }
  
  public void testCreateUserRelationship() throws Exception {
    startSessionAs("root");

    //
    String input = "{\"sender\":root, \"receiver\":demo, \"status\":CONFIRMED}";
    ContainerResponse response = getResponse("POST", getURLResource("usersRelationships/"), input);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    Relationship rootDemo = relationshipManager.get(rootIdentity, demoIdentity);
    assertNotNull(rootDemo);
    assertEquals("root", rootDemo.getSender().getRemoteId());
    assertEquals("demo", rootDemo.getReceiver().getRemoteId());
    assertEquals("CONFIRMED", rootDemo.getStatus().name());

    //
    input = "{\"sender\":mary, \"receiver\":root, \"status\":PENDING}";
    response = getResponse("POST", getURLResource("usersRelationships/"), input);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    Relationship maryRoot = relationshipManager.get(maryIdentity, rootIdentity);
    assertNotNull(maryRoot);
    assertEquals("mary", maryRoot.getSender().getRemoteId());
    assertEquals("root", maryRoot.getReceiver().getRemoteId());
    assertEquals("PENDING", maryRoot.getStatus().name());

    //
    input = "{\"sender\":root, \"receiver\":john, \"status\":IGNORED}";
    response = getResponse("POST", getURLResource("usersRelationships/"), input);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    Relationship rootJohn = relationshipManager.get(rootIdentity, johnIdentity);
    assertNotNull(rootJohn);
    assertEquals("root", rootJohn.getSender().getRemoteId());
    assertEquals("john", rootJohn.getReceiver().getRemoteId());
    assertEquals("IGNORED", rootJohn.getStatus().name());

    //clean
    relationshipManager.delete(rootDemo);
    relationshipManager.delete(maryRoot);
    relationshipManager.delete(rootJohn);
  }
  
  public void testGetUpdateDeleteUserRelationship() throws Exception {
    Relationship relationship = new Relationship(demoIdentity, rootIdentity, Relationship.Type.PENDING);
    relationshipManager.update(relationship);
    //
    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("usersRelationships/" + relationship.getId()), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    
    //update
    String input = "{\"status\":CONFIRMED}";
    response = getResponse("PUT", getURLResource("usersRelationships/" + relationship.getId()), input);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    
    relationship = relationshipManager.get(rootIdentity, demoIdentity);
    assertEquals("CONFIRMED", relationship.getStatus().name());
    
    //delete
    response = service("DELETE", getURLResource("usersRelationships/" + relationship.getId()), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    relationship = relationshipManager.get(rootIdentity, demoIdentity);
    assertNull(relationship);
  }

}
