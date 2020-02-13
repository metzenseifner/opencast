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
import org.opencastproject.security.api.SecurityService;

import com.entwinemedia.fn.data.Opt;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final long fONCE_PER_DAY = 1000 * 60 * 60 * 24;
  private static final int fONE_DAY = 1;
  private static final int fFOUR_AM = 4;
  private static final int fZERO_MINUTES = 0;

  private static final Logger logger = LoggerFactory.getLogger(Cronjob.class);

  private AssetManager assetManager;
  private AdminUISearchIndex adminUISearchIndex;
  private SecurityService securityService;

  public void setAssetManager(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  public void setAdminUISearchIndex(AdminUISearchIndex adminUISearchIndex) { this.adminUISearchIndex = adminUISearchIndex; }

  void setSecurityService(SecurityService securityService) { this.securityService = securityService; }


  @Override
  public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {

    startCronJob();

  }

  private void startCronJob() {
    logger.info("Initialising Cronjob");
    // perform the task once a day at 4 a.m., starting tomorrow morning
    Timer timer = new Timer();
    TimerTask repeatedTask = new TimerTask() {
      @Override
      public void run() {
        startmerge();
      }
    };
    timer.scheduleAtFixedRate(repeatedTask, getTomorrowMorning4am(), fONCE_PER_DAY);
  }

  private void startmerge() {
    logger.info("Search for mediapackages to merge.");
    List<String> mediaPackageIds = getEventsfromLastDays(2);
    Map<String, ArrayList<String>> idsAndRelation = new HashMap<String, ArrayList<String>>();

    for (String mediaPackageId : mediaPackageIds) {
      String relation = "";

      Opt<MediaPackage> mediaPackage = assetManager.getMediaPackage(mediaPackageId);

      Catalog[] catalog = mediaPackage.get()
              .getCatalogs(MediaPackageElementFlavor.parseFlavor("technical/extron-smp-351"));

      for (Catalog smpCatalog : catalog) {
        try {

          String targetFileStr = IOUtils.toString((smpCatalog.getURI()), "UTF-8")
                  .replaceAll("\\r\\n|\\r|\\n|\\t}", " ");

          Map<String, Object> mapObj = new Gson().fromJson(targetFileStr, new TypeToken<HashMap<String, Object>>() {
          }.getType());
          Map<String, Object> packageObj = (Map<String, Object>) mapObj.get("package");
          Map<String, Object> metadataObj = (Map<String, Object>) packageObj.get("metadata");
          relation = (String) metadataObj.get("dc:relation");
          if (!idsAndRelation.get(relation).isEmpty()) {
            ArrayList mediapackageList = idsAndRelation.get(relation);
            mediapackageList.add(mediaPackageId);
            idsAndRelation.put(relation, mediapackageList);
          } else {
            ArrayList<String> mediaPackageList = new ArrayList<String>();
            mediaPackageList.add(mediaPackageId);
            idsAndRelation.put(relation, mediaPackageList);
          }

        } catch (Exception e) {
          logger.error("Could not read Json File from SMP %s", e);
        }

      }
      logger.info("start merging mediapackages.");
      logger.debug("merging: %s.", idsAndRelation.toString());
      MergeMediapackagesService mergeMediapackagesService = new MergeMediapackagesServiceImpl();
      idsAndRelation.forEach((k, v) -> mergeMediapackagesService.mergemediapackages(v, "smp-process"));
    }

  }

  private List<String> getEventsfromLastDays(Integer days) {
    logger.debug("Getting Events from the last %s days", days.toString());
    EventSearchQuery query;
    SearchResult<Event> result = null;
    List<String> mediaPackageIds = new ArrayList();

    query = new EventSearchQuery(securityService.getOrganization().getId(), securityService.getUser());
    try {
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
    tomorrow.add(Calendar.DATE, fONE_DAY);
    Calendar result = new GregorianCalendar(tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH),
            tomorrow.get(Calendar.DATE), fFOUR_AM, fZERO_MINUTES);
    return result.getTime();
  }


}
