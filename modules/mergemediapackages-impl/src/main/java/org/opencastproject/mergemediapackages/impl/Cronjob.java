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
import org.opencastproject.index.service.api.EventIndex;
import org.opencastproject.index.service.api.IndexService;
import org.opencastproject.index.service.impl.IndexServiceImpl;
import org.opencastproject.index.service.impl.index.event.Event;
import org.opencastproject.index.service.impl.index.event.EventSearchQuery;
import org.opencastproject.index.service.util.RestUtils;
import org.opencastproject.ingest.api.IngestException;
import org.opencastproject.ingest.api.IngestService;
import org.opencastproject.matterhorn.search.SearchIndexException;
import org.opencastproject.matterhorn.search.SearchResult;
import org.opencastproject.matterhorn.search.SearchResultItem;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.util.DateTimeSupport;
import org.opencastproject.util.data.Tuple;
import org.opencastproject.workflow.api.WorkflowInstance;
import com.entwinemedia.fn.data.Opt;

import org.joda.time.DateTime;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class Cronjob implements  ManagedService {

  private AssetManager assetmanger;
  private IngestService ingestService;
  private AdminUISearchIndex adminUISearchIndex;
  private SecurityService securityService;

  public void setAssetManager(AssetManager assetManager) {
    this.assetmanger = assetManager;
  }

  public void setEventIndex(AdminUISearchIndex adminUISearchIndex) { this.adminUISearchIndex = adminUISearchIndex; }

  void setSecurityService(SecurityService securityService) { this.securityService = securityService; }

  private final static long fONCE_PER_DAY = 1000 * 60 * 60 * 24;

  private final static int fONE_DAY = 1;
  private final static int fFOUR_AM = 4;
  private final static int fZERO_MINUTES = 0;




  @Override
  public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {

    startCronJob();

  }

  private void startCronJob() {
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
  List<String> mediaPackageIds = getEventsfrom(2);

  for (String mediaPackageId: mediaPackageIds) {

    Opt<MediaPackage> mediaPackage = assetmanger.getMediaPackage(mediaPackageId);
    mediaPackage.get().getCatalogs("")

  }


  }

  private List<String> getEventsfrom(Integer days) {
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

    return mediaPackageIds;
  }

  private static Date getTomorrowMorning4am() {
    Calendar tomorrow = new GregorianCalendar();
    tomorrow.add(Calendar.DATE, fONE_DAY);
    Calendar result = new GregorianCalendar(tomorrow.get(Calendar.YEAR),
            tomorrow.get(Calendar.MONTH), tomorrow.get(Calendar.DATE), fFOUR_AM,
            fZERO_MINUTES);
    return result.getTime();
  }


}
