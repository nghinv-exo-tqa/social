package org.exoplatform.social.core.storage.mongodb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.chromattic.api.ChromatticException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.ActivityProcessor;
import org.exoplatform.social.core.activity.filter.ActivityFilter;
import org.exoplatform.social.core.activity.filter.ActivityUpdateFilter;
import org.exoplatform.social.core.activity.model.ActivityStream;
import org.exoplatform.social.core.activity.model.ActivityStreamImpl;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.chromattic.entity.ActivityEntity;
import org.exoplatform.social.core.chromattic.entity.ActivityListEntity;
import org.exoplatform.social.core.chromattic.entity.HidableEntity;
import org.exoplatform.social.core.chromattic.entity.IdentityEntity;
import org.exoplatform.social.core.chromattic.entity.LockableEntity;
import org.exoplatform.social.core.chromattic.utils.ActivityList;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.storage.ActivityStorageException;
import org.exoplatform.social.core.storage.api.ActivityStorage;
import org.exoplatform.social.core.storage.api.ActivityStreamStorage;
import org.exoplatform.social.core.storage.api.IdentityStorage;
import org.exoplatform.social.core.storage.api.RelationshipStorage;
import org.exoplatform.social.core.storage.api.SpaceStorage;
import org.exoplatform.social.core.storage.exception.NodeNotFoundException;
import org.exoplatform.social.core.storage.impl.ActivityBuilderWhere;
import org.exoplatform.social.core.storage.impl.StorageUtils;
import org.exoplatform.social.core.storage.streams.StreamInvocationHelper;

public class ActivityMongoStorageImpl extends AbstractMongoStorage implements ActivityStorage {
  
  /** .. */
  private static final Log LOG = ExoLogger.getLogger(ActivityMongoStorageImpl.class);
  /** .. */
  private static final Pattern MENTION_PATTERN = Pattern.compile("@([^\\s]+)|@([^\\s]+)$");
  /** .. */
  public static final Pattern USER_NAME_VALIDATOR_REGEX = Pattern.compile("^[\\p{L}][\\p{L}._\\-\\d]+$");
  /** .. */
  private static final String ACTIVITY_COLLECTION_NAME = "activity";
  /** .. */
  private static final String COMMENT_COLLECTION_NAME = "comment";
  /** .. */
  private static final String ALL_STREAM_COLLECTION_NAME = "all";
  /** .. */
  private static final String CONNECTION_STREAM_COLLECTION_NAME = "connection";
  /** .. */
  private static final String OWNER_STREAM_COLLECTION_NAME = "owner";
  /** .. */
  private static final String SPACE_STREAM_COLLECTION_NAME = "space";
  
  private ActivityStorage activityStorage;

  private final SortedSet<ActivityProcessor> activityProcessors;

  private final RelationshipStorage relationshipStorage;
  private final IdentityStorage identityStorage;
  private final SpaceStorage spaceStorage;
  private final ActivityStreamStorage streamStorage;
  //sets value to tell this storage to inject Streams or not

  public ActivityMongoStorageImpl(
      final RelationshipStorage relationshipStorage,
      final IdentityStorage identityStorage,
      final SpaceStorage spaceStorage,
      final ActivityStreamStorage streamStorage,
      final MongoStorage mongoStorage) {
    
    super(mongoStorage);
    this.relationshipStorage = relationshipStorage;
    this.identityStorage = identityStorage;
    this.spaceStorage = spaceStorage;
    this.streamStorage = streamStorage;
    this.activityProcessors = new TreeSet<ActivityProcessor>(processorComparator());
  }
  
  
  private static Comparator<ActivityProcessor> processorComparator() {
    return new Comparator<ActivityProcessor>() {

      public int compare(ActivityProcessor p1, ActivityProcessor p2) {
        if (p1 == null || p2 == null) {
          throw new IllegalArgumentException("Cannot compare null ActivityProcessor");
        }
        return p1.getPriority() - p2.getPriority();
      }
    };
  }

	@Override
	public void setInjectStreams(boolean mustInject) {
		
	}

	@Override
  public ExoSocialActivity getActivity(String activityId) throws ActivityStorageException {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getUserActivities(Identity owner) throws ActivityStorageException {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getUserActivities(Identity owner, long offset, long limit) throws ActivityStorageException {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getUserActivitiesForUpgrade(Identity owner, long offset, long limit) throws ActivityStorageException {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getActivities(Identity owner,
                                               Identity viewer,
                                               long offset,
                                               long limit) throws ActivityStorageException {
    return null;
  }

	@Override
	public void saveComment(ExoSocialActivity activity,
			ExoSocialActivity comment) throws ActivityStorageException {
		
	}

	@Override
	public ExoSocialActivity saveActivity(Identity owner, ExoSocialActivity activity) throws ActivityStorageException {
	  try {
      Validate.notNull(owner, "owner must not be null.");
      Validate.notNull(activity, "activity must not be null.");
      Validate.notNull(activity.getUpdated(), "Activity.getUpdated() must not be null.");
      Validate.notNull(activity.getPostedTime(), "Activity.getPostedTime() must not be null.");
      Validate.notNull(activity.getTitle(), "Activity.getTitle() must not be null.");
    } catch (IllegalArgumentException e) {
      throw new ActivityStorageException(ActivityStorageException.Type.ILLEGAL_ARGUMENTS, e.getMessage(), e);
    }

    try {

      if (activity.getId() == null) {

        String[] mentioners = _createActivity(owner, activity);
      }
      else {
        _saveActivity(activity);
      }

      StorageUtils.persist();

      //
      LOG.debug(String.format(
          "Activity %s by %s (%s) saved",
          activity.getTitle(),
          activity.getUserId(),
          activity.getId()
      ));

      //
      return activity;

    }
    catch (NodeNotFoundException e) {
      throw new ActivityStorageException(ActivityStorageException.Type.FAILED_TO_SAVE_ACTIVITY, e.getMessage(), e);
    } catch (ChromatticException ex) {
      Throwable throwable = ex.getCause();
      if (throwable instanceof ItemExistsException || 
          throwable instanceof InvalidItemStateException) {
        LOG.warn("Probably was inserted activity by another session");
        LOG.debug(ex.getMessage(), ex);
        return activity;
      } else {
        throw new ActivityStorageException(ActivityStorageException.Type.FAILED_TO_SAVE_ACTIVITY, ex.getMessage());
      }
    }
	}
	
	protected void _saveActivity(ExoSocialActivity activity) throws NodeNotFoundException {

    ActivityEntity activityEntity = _findById(ActivityEntity.class, activity.getId());
    
    //
    String[] removedLikes = StorageUtils.sub(activityEntity.getLikes(), activity.getLikeIdentityIds());
    String[] addedLikes = StorageUtils.sub(activity.getLikeIdentityIds(), activityEntity.getLikes());
    //Don't update the last modified for activity.
    //Don't apply What's hot for like/dislike case.
    
    if (removedLikes.length > 0 || addedLikes.length > 0) {
      //process likes activity
      //manageActivityLikes(addedLikes, removedLikes, activity);
    }
    //
    fillActivityEntityFromActivity(activity, activityEntity);
    
  }
	
	/*
   * Private
   */
  private void fillActivityEntityFromActivity(ExoSocialActivity activity, ActivityEntity activityEntity) {

    activityEntity.setTitle(activity.getTitle());
    activityEntity.setTitleId(activity.getTitleId());
    activityEntity.setBody(activity.getBody());
    activityEntity.setBodyId(activity.getBodyId());
    activityEntity.setLikes(activity.getLikeIdentityIds());
    activityEntity.setType(activity.getType());
    activityEntity.setAppId(activity.getAppId());
    activityEntity.setExternalId(activity.getExternalId());
    activityEntity.setUrl(activity.getUrl());
    activityEntity.setPriority(activity.getPriority());
    activityEntity.setLastUpdated(activity.getUpdated().getTime());
    //
    HidableEntity hidable = _getMixin(activityEntity, HidableEntity.class, true);
    hidable.setHidden(activity.isHidden());
    LockableEntity lockable = _getMixin(activityEntity, LockableEntity.class, true);
    lockable.setLocked(activity.isLocked());
    activityEntity.setMentioners(activity.getMentionedIds());
    activityEntity.setCommenters(activity.getCommentedIds());

    //
    Map<String, String> params = activity.getTemplateParams();
    if (params != null) {
      activityEntity.putParams(params);
    }

    //
    fillStream(activityEntity, activity);
    
  }
  
  private void fillStream(ActivityEntity activityEntity, ExoSocialActivity activity) {

    //
    ActivityStream stream = new ActivityStreamImpl();
    
    IdentityEntity identityEntity = null;

    //update new stream owner
    try {
      Identity streamOwnerIdentity = identityStorage.findIdentity(SpaceIdentityProvider.NAME, activity.getStreamOwner());
      if (streamOwnerIdentity == null) {
        streamOwnerIdentity = identityStorage.findIdentity(OrganizationIdentityProvider.NAME, activity.getStreamOwner());
      }
      IdentityEntity streamOwnerEntity = _findById(IdentityEntity.class, streamOwnerIdentity.getId());
      identityEntity = streamOwnerEntity;
      activityEntity.setIdentity(streamOwnerEntity);
    } catch (Exception e) {
      identityEntity = activityEntity.getIdentity();
    }
    //
    stream.setId(identityEntity.getId());
    stream.setPrettyId(identityEntity.getRemoteId());
    stream.setType(identityEntity.getProviderId());
    
    //Identity identity = identityStorage.findIdentityById(identityEntity.getId());
    if (identityEntity != null && SpaceIdentityProvider.NAME.equals(identityEntity.getProviderId())) {
      Space space = spaceStorage.getSpaceByPrettyName(identityEntity.getRemoteId());
      //work-around for SOC-2366 when rename space's display name.
      if (space != null) {
        String groupId = space.getGroupId().split("/")[2];
        stream.setPermaLink(LinkProvider.getActivityUriForSpace(identityEntity.getRemoteId(), groupId));
      }
    } else {
      stream.setPermaLink(LinkProvider.getActivityUri(identityEntity.getProviderId(), identityEntity.getRemoteId()));
    }
    //
    activity.setActivityStream(stream);
    activity.setStreamId(stream.getId());
    activity.setStreamOwner(stream.getPrettyId());

  }
	
	/*
   * Internal
   */
  protected String[] _createActivity(Identity owner, ExoSocialActivity activity) throws NodeNotFoundException {

    IdentityEntity identityEntity = _findById(IdentityEntity.class, owner.getId());

    IdentityEntity posterIdentityEntity;
    if (activity.getUserId() != null) {
      posterIdentityEntity = _findById(IdentityEntity.class, activity.getUserId());
    }
    else {
      posterIdentityEntity = identityEntity;
    }

    // Get ActivityList
    ActivityListEntity activityListEntity = identityEntity.getActivityList();

    //
    Collection<ActivityEntity> entities = new ActivityList(activityListEntity);

    // Create activity
    long currentMillis = System.currentTimeMillis();
    long activityMillis = (activity.getPostedTime() != null ? activity.getPostedTime() : currentMillis);
    ActivityEntity activityEntity = activityListEntity.createActivity(String.valueOf(activityMillis));
    entities.add(activityEntity);
    activityEntity.setIdentity(identityEntity);
    activityEntity.setComment(Boolean.FALSE);
    activityEntity.setPostedTime(activityMillis);
    activityEntity.setLastUpdated(activityMillis);
    activityEntity.setPosterIdentity(posterIdentityEntity);
    

    // Fill activity model
    activity.setId(activityEntity.getId());
    activity.setStreamOwner(identityEntity.getRemoteId());
    activity.setPostedTime(activityMillis);
    activity.setReplyToId(new String[]{});
    activity.setUpdated(activityMillis);
    
    //records activity for mention case.
    
    List<String> mentioners = new ArrayList<String>();
    activity.setMentionedIds(processMentions(activity.getMentionedIds(), activity.getTitle(), mentioners, true));
    
    //
    activity.setPosterId(activity.getUserId() != null ? activity.getUserId() : owner.getId());
      
    //
    fillActivityEntityFromActivity(activity, activityEntity);
    return mentioners.toArray(new String[0]);
  }
  
  /**
   * Processes Mentioners who has been mentioned via the Activity.
   * 
   * @param mentionerIds
   * @param title
   * @param isAdded
   * @return list of added IdentityIds who mentioned
   */
  private String[] processMentions(String[] mentionerIds, String title, List<String> addedOrRemovedIds, boolean isAdded) {
    if (title == null || title.length() == 0) {
      return ArrayUtils.EMPTY_STRING_ARRAY;
    }
    
    Matcher matcher = MENTION_PATTERN.matcher(title);
    while (matcher.find()) {
      String remoteId = matcher.group().substring(1);
      if (!USER_NAME_VALIDATOR_REGEX.matcher(remoteId).matches()) {
        continue;
      }
      Identity identity = identityStorage.findIdentity(OrganizationIdentityProvider.NAME, remoteId);
      // if not the right mention then ignore
      if (identity != null) { 
        String mentionStr = identity.getId() + MENTION_CHAR; // identityId@
        mentionerIds = isAdded ? add(mentionerIds, mentionStr, addedOrRemovedIds) : remove(mentionerIds, mentionStr, addedOrRemovedIds);
      }
    }
    return mentionerIds;
  }
  
  private String[] add(String[] mentionerIds, String mentionStr, List<String> addedOrRemovedIds) {
    if (ArrayUtils.toString(mentionerIds).indexOf(mentionStr) == -1) { // the first mention
      addedOrRemovedIds.add(mentionStr.replace(MENTION_CHAR, ""));
      return (String[]) ArrayUtils.add(mentionerIds, mentionStr + 1);
    }
    
    String storedId = null;
    for (String mentionerId : mentionerIds) {
      if (mentionerId.indexOf(mentionStr) != -1) {
        mentionerIds = (String[]) ArrayUtils.removeElement(mentionerIds, mentionerId);
        storedId = mentionStr + (Integer.parseInt(mentionerId.split(MENTION_CHAR)[1]) + 1);
        break;
      }
    }
    

    addedOrRemovedIds.add(mentionStr.replace(MENTION_CHAR, ""));
    mentionerIds = (String[]) ArrayUtils.add(mentionerIds, storedId);
    return mentionerIds;
  }

  private String[] remove(String[] mentionerIds, String mentionStr, List<String> addedOrRemovedIds) {
    for (String mentionerId : mentionerIds) {
      if (mentionerId.indexOf(mentionStr) != -1) {
        int numStored = Integer.parseInt(mentionerId.split(MENTION_CHAR)[1]) - 1;
        
        if (numStored == 0) {
          addedOrRemovedIds.add(mentionStr.replace(MENTION_CHAR, ""));
          return (String[]) ArrayUtils.removeElement(mentionerIds, mentionerId);
        }

        mentionerIds = (String[]) ArrayUtils.removeElement(mentionerIds, mentionerId);
        mentionerIds = (String[]) ArrayUtils.add(mentionerIds, mentionStr + numStored);
        addedOrRemovedIds.add(mentionStr.replace(MENTION_CHAR, ""));
        break;
      }
    }
    return mentionerIds;
  }

  @Override
  public ExoSocialActivity getParentActivity(ExoSocialActivity comment) throws ActivityStorageException {
    return null;
  }

  @Override
  public void deleteActivity(String activityId) throws ActivityStorageException {

  }

  @Override
  public void deleteComment(String activityId, String commentId) throws ActivityStorageException {
		
	}

	@Override
	public List<ExoSocialActivity> getActivitiesOfIdentities(
			List<Identity> connectionList, long offset, long limit)
			throws ActivityStorageException {
		return null;
	}

	@Override
	public List<ExoSocialActivity> getActivitiesOfIdentities(
			List<Identity> connectionList, TimestampType type, long offset,
			long limit) throws ActivityStorageException {
		return null;
	}

	@Override
	public int getNumberOfUserActivities(Identity owner)
			throws ActivityStorageException {
		return 0;
	}

	@Override
	public int getNumberOfUserActivitiesForUpgrade(Identity owner)
			throws ActivityStorageException {
		return 0;
	}

	@Override
	public int getNumberOfNewerOnUserActivities(Identity ownerIdentity,
			ExoSocialActivity baseActivity) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<ExoSocialActivity> getNewerOnUserActivities(
			Identity ownerIdentity, ExoSocialActivity baseActivity, int limit) {
		return null;
	}

  @Override
  public int getNumberOfOlderOnUserActivities(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getOlderOnUserActivities(Identity ownerIdentity,
                                                          ExoSocialActivity baseActivity,
                                                          int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getActivityFeed(Identity ownerIdentity, int offset, int limit) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<ExoSocialActivity> getActivityFeedForUpgrade(Identity ownerIdentity,
                                                           int offset,
                                                           int limit) {
    return null;
  }

  @Override
  public int getNumberOfActivitesOnActivityFeed(Identity ownerIdentity) {
    return 0;
  }

  @Override
  public int getNumberOfActivitesOnActivityFeedForUpgrade(Identity ownerIdentity) {
    return 0;
  }

  @Override
  public int getNumberOfNewerOnActivityFeed(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getNewerOnActivityFeed(Identity ownerIdentity,
                                                        ExoSocialActivity baseActivity,
                                                        int limit) {
    return null;
  }

  @Override
  public int getNumberOfOlderOnActivityFeed(Identity ownerIdentity, ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getOlderOnActivityFeed(Identity ownerIdentity,
                                                        ExoSocialActivity baseActivity,
                                                        int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getActivitiesOfConnections(Identity ownerIdentity,
                                                            int offset,
                                                            int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getActivitiesOfConnectionsForUpgrade(Identity ownerIdentity,
                                                                      int offset,
                                                                      int limit) {
    return null;
  }

  @Override
  public int getNumberOfActivitiesOfConnections(Identity ownerIdentity) {
    return 0;
  }

  @Override
  public int getNumberOfActivitiesOfConnectionsForUpgrade(Identity ownerIdentity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getActivitiesOfIdentity(Identity ownerIdentity,
                                                         long offset,
                                                         long limit) {
    return null;
  }

  @Override
  public int getNumberOfNewerOnActivitiesOfConnections(Identity ownerIdentity,
                                                       ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getNewerOnActivitiesOfConnections(Identity ownerIdentity,
                                                                   ExoSocialActivity baseActivity,
                                                                   long limit) {
    return null;
  }

  @Override
  public int getNumberOfOlderOnActivitiesOfConnections(Identity ownerIdentity,
                                                       ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getOlderOnActivitiesOfConnections(Identity ownerIdentity,
                                                                   ExoSocialActivity baseActivity,
                                                                   int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getUserSpacesActivities(Identity ownerIdentity,
                                                         int offset,
                                                         int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getUserSpacesActivitiesForUpgrade(Identity ownerIdentity,
                                                                   int offset,
                                                                   int limit) {
    return null;
  }

  @Override
  public int getNumberOfUserSpacesActivities(Identity ownerIdentity) {
    return 0;
  }

  @Override
  public int getNumberOfUserSpacesActivitiesForUpgrade(Identity ownerIdentity) {
    return 0;
  }

  @Override
  public int getNumberOfNewerOnUserSpacesActivities(Identity ownerIdentity,
                                                    ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getNewerOnUserSpacesActivities(Identity ownerIdentity,
                                                                ExoSocialActivity baseActivity,
                                                                int limit) {
    return null;
  }

  @Override
  public int getNumberOfOlderOnUserSpacesActivities(Identity ownerIdentity,
                                                    ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getOlderOnUserSpacesActivities(Identity ownerIdentity,
                                                                ExoSocialActivity baseActivity,
                                                                int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getComments(ExoSocialActivity existingActivity,
                                             int offset,
                                             int limit) {
    return null;
  }

  @Override
  public int getNumberOfComments(ExoSocialActivity existingActivity) {
    return 0;
  }

  @Override
  public int getNumberOfNewerComments(ExoSocialActivity existingActivity,
                                      ExoSocialActivity baseComment) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getNewerComments(ExoSocialActivity existingActivity,
                                                  ExoSocialActivity baseComment,
                                                  int limit) {
    return null;
  }

  @Override
  public int getNumberOfOlderComments(ExoSocialActivity existingActivity,
                                      ExoSocialActivity baseComment) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getOlderComments(ExoSocialActivity existingActivity,
                                                  ExoSocialActivity baseComment,
                                                  int limit) {
    return null;
  }

	@Override
	public SortedSet<ActivityProcessor> getActivityProcessors() {
		return null;
	}

	@Override
	public void updateActivity(ExoSocialActivity existingActivity)
			throws ActivityStorageException {
		
	}

	@Override
	public int getNumberOfNewerOnActivityFeed(Identity ownerIdentity,
			Long sinceTime) {
		return 0;
	}

	@Override
	public int getNumberOfNewerOnUserActivities(Identity ownerIdentity,
			Long sinceTime) {
		return 0;
	}

	@Override
	public int getNumberOfNewerOnActivitiesOfConnections(
			Identity ownerIdentity, Long sinceTime) {
		return 0;
	}

	@Override
	public int getNumberOfNewerOnUserSpacesActivities(Identity ownerIdentity,
			Long sinceTime) {
		return 0;
	}

  @Override
  public List<ExoSocialActivity> getActivitiesOfIdentities(ActivityBuilderWhere where,
                                                           ActivityFilter filter,
                                                           long offset,
                                                           long limit) throws ActivityStorageException {
    return null;
  }

  @Override
  public int getNumberOfSpaceActivities(Identity spaceIdentity) {
    return 0;
  }

  @Override
  public int getNumberOfSpaceActivitiesForUpgrade(Identity spaceIdentity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getSpaceActivities(Identity spaceIdentity, int index, int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getSpaceActivitiesForUpgrade(Identity spaceIdentity,
                                                              int index,
                                                              int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getActivitiesByPoster(Identity posterIdentity,
                                                       int offset,
                                                       int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getActivitiesByPoster(Identity posterIdentity,
                                                       int offset,
                                                       int limit,
                                                       String... activityTypes) {
    return null;
  }

  @Override
  public int getNumberOfActivitiesByPoster(Identity posterIdentity) {
    return 0;
  }

  @Override
  public int getNumberOfActivitiesByPoster(Identity ownerIdentity, Identity viewerIdentity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getNewerOnSpaceActivities(Identity spaceIdentity,
                                                           ExoSocialActivity baseActivity,
                                                           int limit) {
    return null;
  }

  @Override
  public int getNumberOfNewerOnSpaceActivities(Identity spaceIdentity,
                                               ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getOlderOnSpaceActivities(Identity spaceIdentity,
                                                           ExoSocialActivity baseActivity,
                                                           int limit) {
    return null;
  }

  @Override
  public int getNumberOfOlderOnSpaceActivities(Identity spaceIdentity,
                                               ExoSocialActivity baseActivity) {
    return 0;
  }

  @Override
  public int getNumberOfNewerOnSpaceActivities(Identity spaceIdentity, Long sinceTime) {
    return 0;
  }

  @Override
  public int getNumberOfUpdatedOnActivityFeed(Identity owner, ActivityUpdateFilter filter) {
    return 0;
  }

  @Override
  public int getNumberOfUpdatedOnUserActivities(Identity owner, ActivityUpdateFilter filter) {
    return 0;
  }

  @Override
  public int getNumberOfUpdatedOnActivitiesOfConnections(Identity owner, ActivityUpdateFilter filter) {
    return 0;
  }

  @Override
  public int getNumberOfUpdatedOnUserSpacesActivities(Identity owner, ActivityUpdateFilter filter) {
    return 0;
  }

  @Override
  public int getNumberOfUpdatedOnSpaceActivities(Identity owner, ActivityUpdateFilter filter) {
    return 0;
  }

  @Override
  public int getNumberOfMultiUpdated(Identity owner, Map<String, Long> sinceTimes) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getNewerFeedActivities(Identity owner, Long sinceTime, int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getNewerUserActivities(Identity owner, Long sinceTime, int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getNewerUserSpacesActivities(Identity owner,
                                                              Long sinceTime,
                                                              int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getNewerActivitiesOfConnections(Identity owner,
                                                                 Long sinceTime,
                                                                 int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getNewerSpaceActivities(Identity owner, Long sinceTime, int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getOlderFeedActivities(Identity owner, Long sinceTime, int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getOlderUserActivities(Identity owner, Long sinceTime, int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getOlderUserSpacesActivities(Identity owner,
                                                              Long sinceTime,
                                                              int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getOlderActivitiesOfConnections(Identity owner,
                                                                 Long sinceTime,
                                                                 int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getOlderSpaceActivities(Identity owner, Long sinceTime, int limit) {
    return null;
  }

  @Override
  public int getNumberOfOlderOnActivityFeed(Identity ownerIdentity, Long sinceTime) {
    return 0;
  }

  @Override
  public int getNumberOfOlderOnUserActivities(Identity ownerIdentity, Long sinceTime) {
    return 0;
  }

  @Override
  public int getNumberOfOlderOnActivitiesOfConnections(Identity ownerIdentity, Long sinceTime) {
    return 0;
  }

  @Override
  public int getNumberOfOlderOnUserSpacesActivities(Identity ownerIdentity, Long sinceTime) {
    return 0;
  }

  @Override
  public int getNumberOfOlderOnSpaceActivities(Identity ownerIdentity, Long sinceTime) {
    return 0;
  }

  @Override
  public List<ExoSocialActivity> getNewerComments(ExoSocialActivity existingActivity,
                                                  Long sinceTime,
                                                  int limit) {
    return null;
  }

  @Override
  public List<ExoSocialActivity> getOlderComments(ExoSocialActivity existingActivity,
                                                  Long sinceTime,
                                                  int limit) {
    return null;
  }

  @Override
  public int getNumberOfNewerComments(ExoSocialActivity existingActivity, Long sinceTime) {
    return 0;
  }

  @Override
  public int getNumberOfOlderComments(ExoSocialActivity existingActivity, Long sinceTime) {
    return 0;
  }

}