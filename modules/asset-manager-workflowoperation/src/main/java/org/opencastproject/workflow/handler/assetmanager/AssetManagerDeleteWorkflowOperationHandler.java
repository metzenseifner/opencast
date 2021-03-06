/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.workflow.handler.assetmanager;

import static java.lang.String.format;
import static org.opencastproject.assetmanager.api.AssetManager.DEFAULT_OWNER;

import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.assetmanager.api.query.AQueryBuilder;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Workflow operation for deleting an episode from the asset manager.
 *
 * @see AssetManager
 */
public class AssetManagerDeleteWorkflowOperationHandler extends AbstractWorkflowOperationHandler {
  private static final Logger logger = LoggerFactory.getLogger(AssetManagerDeleteWorkflowOperationHandler.class);

  /** The archive */
  private AssetManager assetManager;

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS;

  static {
    CONFIG_OPTIONS = new TreeMap<>();
  }

  @Override
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /** OSGi DI */
  public void setAssetManager(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    final MediaPackage mediaPackage = workflowInstance.getMediaPackage();
    final String mpId = mediaPackage.getIdentifier().toString();

    try {
      final AQueryBuilder q = assetManager.createQuery();
      final long deleted = q.delete(DEFAULT_OWNER, q.snapshot()).where(q.mediaPackageId(mpId)).run();
      if (deleted == 0) {
        logger.info(format("The asset manager does not contain episode %s", mpId));
      } else {
        logger.info(format("Successfully deleted %d version/s episode %s from the asset manager", deleted, mpId));
      }
    } catch (Exception e) {
      logger.warn(format("Error deleting episode %s from the asset manager: %s", mpId, e));
      throw new WorkflowOperationException("Unable to delete episode from the asset manager", e);
    }
    return createResult(mediaPackage, Action.CONTINUE);
  }

}
