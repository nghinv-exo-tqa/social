/*
 * Copyright (C) 2003-2011 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.exoplatform.social.common.lifecycle;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.exoplatform.commons.chromattic.ChromatticLifeCycle;
import org.exoplatform.commons.chromattic.SessionContext;
import org.exoplatform.commons.chromattic.SynchronizationListener;
import org.exoplatform.commons.chromattic.SynchronizationStatus;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Process the callable request out of the http request.
 *
 * @author <a href="mailto:alain.defrance@exoplatform.com">Alain Defrance</a>
 * @version $Revision$
 */
public class LifeCycleCompletionService {

  private final String THREAD_NUMBER_KEY = "thread-number";

  private final String ASYNC_EXECUTION_KEY = "async-execution";

  private Executor executor;

  protected ExecutorCompletionService ecs;

  private final int DEFAULT_THREAD_NUMBER = 1;

  private final boolean DEFAULT_ASYNC_EXECUTION = true;

  private int configThreadNumber;

  private boolean configAsyncExecution;
  
  private static final Log LOG = ExoLogger.getLogger(LifeCycleCompletionService.class);

  public LifeCycleCompletionService(InitParams params) {

    //
    ValueParam threadNumber = params.getValueParam(THREAD_NUMBER_KEY);
    ValueParam asyncExecution = params.getValueParam(ASYNC_EXECUTION_KEY);

    //
    try {
      this.configThreadNumber = Integer.valueOf(threadNumber.getValue());
    }
    catch (Exception e) {
      this.configThreadNumber = DEFAULT_THREAD_NUMBER;
    }

    //
    try {
      this.configAsyncExecution = Boolean.valueOf(asyncExecution.getValue());
    }
    catch (Exception e) {
      this.configAsyncExecution = DEFAULT_ASYNC_EXECUTION;
    }

    //
    if (configAsyncExecution) {
      this.executor = Executors.newFixedThreadPool(this.configThreadNumber);
    }
    else {
      this.executor = new DirectExecutor();
    }

    //
    this.ecs = new ExecutorCompletionService(executor);

  }

  public void addTask(final Callable callable) {
    if (isAsync()) {
      SessionContext ctx = getLifecycle().getContext();
      
      ctx.addSynchronizationListener(new SynchronizationListener() {
        public void beforeSynchronization() {}
        
        @SuppressWarnings("unchecked")
        public void afterSynchronization(SynchronizationStatus status) {
          if (status == SynchronizationStatus.SAVED) {
            ecs.submit(callable);
          }
        }
      });
    } else {
      ecs.submit(callable);
    }
  }

  private ChromatticLifeCycle getLifecycle() {
    PortalContainer container = PortalContainer.getInstance();
    return container.getComponentInstanceOfType((ChromatticLifeCycle.class));
  }

  public void waitCompletionFinished() {
    try {
      if (executor instanceof ExecutorService) {
        ((ExecutorService) executor).awaitTermination(1, TimeUnit.SECONDS);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isAsync() {
    return this.configAsyncExecution;
  }

  private class DirectExecutor implements Executor {

    public void execute(final Runnable runnable) {
      if (Thread.interrupted()) throw new RuntimeException();

      runnable.run();
    }
  }
}
