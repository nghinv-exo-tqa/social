package org.exoplatform.social.notification.mock;

import java.util.List;

import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.services.organization.User;

public class MockUserSettingServiceImpl implements UserSettingService {

  @Override
  public void save(UserSetting notificationSetting) {
  }

  @Override
  public UserSetting get(String userId) {
    return null;
  }

  @Override
  public List<UserSetting> getDaily(int offset, int limit) {
    return null;
  }

  @Override
  public long getNumberOfDaily() {
    return 0;
  }

  @Override
  public List<UserSetting> getDefaultDaily() {
    return null;
  }

  @Override
  public List<String> getUserSettingByPlugin(String pluginId) {
    return null;
  }

  @Override
  public void addMixin(String userId) {
  }

  @Override
  public void addMixin(User[] users) {
  }

}