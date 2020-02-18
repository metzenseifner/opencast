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

package org.opencastproject.mergemediapackages.impl;

import org.opencastproject.adminui.index.AdminUISearchIndex;
import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.index.service.impl.index.event.Event;
import org.opencastproject.index.service.impl.index.event.EventSearchQuery;
import org.opencastproject.matterhorn.search.SearchIndexException;
import org.opencastproject.matterhorn.search.SearchResult;
import org.opencastproject.matterhorn.search.SearchResultItem;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mergemediapackages.api.MergeMediapackagesService;
import org.opencastproject.security.api.DefaultOrganization;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.User;
import org.opencastproject.security.util.SecurityUtil;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workspace.api.Workspace;

import com.entwinemedia.fn.data.Opt;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.joda.time.DateTime;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class Cronjob implements  ManagedService {

  private static final long ONCE_PER_DAY = 1000 * 60 * 60 * 24;
  private static final int ONE_DAY = 1;
  private static final int FOUR_AM = 4;
  private static final int ZERO_MINUTES = 0;

  private static final Logger logger = LoggerFactory.getLogger(Cronjob.class);

  private Timer timer;
  private TimerTask repeatedTask;

  private AssetManager assetManager;
  private AdminUISearchIndex adminUISearchIndex;
  private SecurityService securityService;
  private MergeMediapackagesService mergeMediapackagesService;
  private WorkflowInstance workflowInstance;
  private Workspace workspace;

  public void setAssetManager(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  public void setAdminUISearchIndex(AdminUISearchIndex adminUISearchIndex) {
    this.adminUISearchIndex = adminUISearchIndex;
  }

  void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  void setMergeMediapackagesService(MergeMediapackagesService mergeMediapackagesService) {
    this.mergeMediapackagesService = mergeMediapackagesService;
  }

  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * deactivate timer
   */
  public void deactivate() {
    repeatedTask.cancel();
    timer.cancel();
  }

  /**
   * Activation callback to be executed once all dependencies are set
   */
  public void activate() {
    logger.info("activate()");
    timer = new Timer();
    repeatedTask = new TimerTask() {
      @Override
      public void run() {
        try {
          startmerge();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    };
    startCronJob();

  }

  @Override
  public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
  }

  private void startCronJob() {
    logger.info("Initialising Cronjob");
    // perform the task once a day at 4 a.m., starting tomorrow morning
    timer.scheduleAtFixedRate(repeatedTask, getTomorrowMorning4am(), ONCE_PER_DAY);
    //Testing Timer
    //timer.scheduleAtFixedRate(repeatedTask, DateTime.now().toDate(),60000);
  }

  private void startmerge() throws IOException {
    logger.info("Search for mediapackages to merge.");
    securityService.setOrganization(new DefaultOrganization());
    User user = SecurityUtil.createSystemUser("admin", securityService.getOrganization());
    securityService.setUser(user);
    List<String> mediaPackageIds = getEventsfromLastDays(2);
    Map<String, ArrayList<String>> idsAndRelation = new HashMap<String, ArrayList<String>>();

    for (String mediaPackageId : mediaPackageIds) {
      String relation = "";

      Opt<MediaPackage> mediaPackage = assetManager.getMediaPackage(mediaPackageId);

      Catalog[] catalog = mediaPackage.get()
              .getCatalogs(MediaPackageElementFlavor.parseFlavor("technical/extron-smp-351"));

      for (Catalog smpCatalog : catalog) {
        try {
          File smpJsonFile = workspace.get(smpCatalog.getURI());
          String smpString = FileUtils.readFileToString(smpJsonFile);

          Map<String, Object> mapObj = new Gson().fromJson(smpString, new TypeToken<HashMap<String, Object>>() {
          }.getType());
          Map<String, Object> packageObj = (Map<String, Object>) mapObj.get("package");
          Map<String, Object> metadataObj = (Map<String, Object>) packageObj.get("metadata");
          relation = (String) metadataObj.get("dc:relation");
          String merged = (String) metadataObj.get("dc:merged");
          if (!"true".equals(merged)) {
            logger.info("Mediapackage {} not merged, adding for processing", mediaPackageId);
            if (idsAndRelation.get(relation) != null) {
              ArrayList mediapackageList = idsAndRelation.get(relation);
              mediapackageList.add(mediaPackageId);
              idsAndRelation.put(relation, mediapackageList);
            } else {
              ArrayList<String> mediaPackageList = new ArrayList<String>();
              mediaPackageList.add(mediaPackageId);
              idsAndRelation.put(relation, mediaPackageList);
            }

          }
        } catch (Exception e) {
          logger.error("Could not read Json File from SMP %s", e);
        }
      }
    }


    logger.debug("merging: %s.", idsAndRelation.toString());

    for (Map.Entry<String, ArrayList<String>> entry : idsAndRelation.entrySet()) {
      ArrayList<String> mpIdsList = entry.getValue();
      if (mpIdsList.size() > 1) {
        logger.info("start merging mediapackages:- {} - with relation: {}.", mpIdsList.toString(), entry.getKey());
        SecurityUtil.runAs(securityService, securityService.getOrganization(), securityService.getUser(), () -> {
          workflowInstance = mergeMediapackagesService.mergemediapackages(mpIdsList, "smp-process");
        });
        if (workflowInstance.isActive()) {
          for (String id : mpIdsList) {
            markMedipackageAsMerged(id);
          }
        }

      } else {
        logger.info("Only One mediapackage found for merging skipping: id: {} , releation: {}", mpIdsList.toString(), entry.getKey());
      }
    }
  }


  private void markMedipackageAsMerged(String id) throws IOException {

    Opt<MediaPackage> assetMediaPackage = assetManager.getMediaPackage(id);
    Catalog[] assetcatalog = assetMediaPackage.get()
            .getCatalogs(MediaPackageElementFlavor.parseFlavor("technical/extron-smp-351"));

    File smpJsonFile = null;
    try {
      smpJsonFile = workspace.get(assetcatalog[0].getURI());

      String smpString = FileUtils.readFileToString(smpJsonFile);

      Map<String, Object> mapObj = new Gson().fromJson(smpString, new TypeToken<HashMap<String, Object>>() {
      }.getType());
      Map<String, Object> packageObj = (Map<String, Object>) mapObj.get("package");
      Map<String, Object> metadataObj = (Map<String, Object>) packageObj.get("metadata");
      metadataObj.put("dc:merged", "true");

      InputStream jsonStream = new ByteArrayInputStream(new Gson().toJson(mapObj).getBytes());

      //file as changed set checksum for assetmanager!
      assetcatalog[0].setChecksum(null);
      //write new Json File
      assetcatalog[0].setURI(workspace
              .put(id, assetcatalog[0].getIdentifier(), FilenameUtils.getName(assetcatalog[0].getURI().getPath()),
                      jsonStream));
      assetManager.takeSnapshot(assetMediaPackage.get());
    } catch (NotFoundException e) {
      e.printStackTrace();
    }
  }

  private List<String> getEventsfromLastDays(Integer days) {
    logger.debug("Getting Events from the last %s days", days.toString());
    EventSearchQuery query;
    SearchResult<Event> result = null;
    List<String> mediaPackageIds = new ArrayList();
    Organization organization = new DefaultOrganization();
    User user = SecurityUtil.createSystemUser("admin", organization);
    query = new EventSearchQuery(organization.getId(), user);

    try {
      logger.debug("Getting Events from the last %s days", days.toString());

      query.withStartFrom(DateTime.now().minusDays(days).toDate());
      query.withStartTo(DateTime.now().toDate());
    } catch (Exception e) {
      e.printStackTrace();
    }
    try {
      result = adminUISearchIndex.getByQuery(query);
    } catch (SearchIndexException e) {
      e.printStackTrace();
    }

    for (SearchResultItem<Event> r : result.getItems()) {
      Event event = r.getSource();
      mediaPackageIds.add(event.getIdentifier());
    }
    logger.debug("Mediapackages from the last %s days: %s", days.toString(), mediaPackageIds.toString());
    return mediaPackageIds;
  }

  private static Date getTomorrowMorning4am() {
    Calendar tomorrow = new GregorianCalendar();
    tomorrow.add(Calendar.DATE, ONE_DAY);
    Calendar result = new GregorianCalendar(tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH),
            tomorrow.get(Calendar.DATE), FOUR_AM, ZERO_MINUTES);
    return result.getTime();
  }


}
