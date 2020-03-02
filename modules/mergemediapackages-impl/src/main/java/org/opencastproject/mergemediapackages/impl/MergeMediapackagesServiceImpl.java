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

import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.ingest.api.IngestException;
import org.opencastproject.ingest.api.IngestService;
import org.opencastproject.kernel.mail.SmtpService;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mergemediapackages.api.MergeMediapackagesService;
import org.opencastproject.workflow.api.WorkflowInstance;

import com.entwinemedia.fn.data.Opt;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.mail.MessagingException;

public class MergeMediapackagesServiceImpl implements MergeMediapackagesService, ManagedService {

  private AssetManager assetmanger;
  private IngestService ingestService;
  /** The SMTP service */
  private SmtpService smptService;

  private static final Logger logger = LoggerFactory.getLogger(Cronjob.class);

  private  String mailto = "Anna.Saxer@uibk.ac.at";

  public void setAssetManager(AssetManager assetManager) {
    this.assetmanger = assetManager;
  }

  public void setIngestService(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  /**OSGi callback to add {@link SmtpService} instance. */
  void setSmtpService(SmtpService smtpService) {
    this.smptService = smtpService;
  }

  @Override
  public WorkflowInstance mergemediapackages(List<String> mediapackageIds, String workflowId) {

    WorkflowInstance workflowInstance;

    //get Mediapackages from Assetmanager
    List<MediaPackage> mediaPackageList = mediapackageIds.stream().map(n -> assetmanger.getMediaPackage(n))
            .filter(Opt::isSome).map(Opt::get).collect(Collectors.toList());

    try {
      MediaPackage newMediapackage = ingestService.createMediaPackage();
      newMediapackage = mergeMediapackageList(newMediapackage, mediaPackageList);
      workflowInstance = ingestService.ingest(newMediapackage, workflowId);
      return workflowInstance;
    } catch (MediaPackageException e) {
      e.printStackTrace();
      logger.error("Mediapackage Excecption {}",e.getMessage());
    } catch (IOException e) {
      e.printStackTrace();
      logger.error("IO-Exception {}", e.getMessage());
    } catch (IngestException e) {
      logger.error("Create Mediapackage in IngestService failed {}",e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
      logger.error(e.getMessage());
    }

    return null;
  }

  private MediaPackage mergeMediapackageList(MediaPackage finalMediapackage, List<MediaPackage> mediaPackageList)
          throws MediaPackageException, MessagingException {
    for (MediaPackage mediaPackage : mediaPackageList) {
      for (MediaPackageElement mediaPackageElement : mediaPackage.getElements()) {
        if (mediaPackageElement.getFlavor() != null) {
          if (finalMediapackage.getElementsByFlavor(mediaPackageElement.getFlavor()).length == 0) {
            finalMediapackage.add(mediaPackageElement);
          } else if (finalMediapackage.getTracks(mediaPackageElement.getFlavor()).length > 0) {
            String content = String.format("Mediapackages {} haben gleiche flavors.", mediaPackageList.toString());
            String subject = String.format("Mediapackage mit 2 gleichen flavors.");
            logger.info("Mediapackage flavors are not uniqe, sending mail {}", mediaPackageList.toString());
            smptService.send(this.mailto,subject,content);
            throw new MediaPackageException("Mediapackage contains same flavor more than once");
          }

        }
      }

    }
    return finalMediapackage;
  }

  @Override
  public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {

  }
}
