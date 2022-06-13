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

package org.opencastproject.ingest.endpoint;

import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public final class AsynchronousIngestThread extends Thread {

    private static Logger logger = LoggerFactory.getLogger(AsynchronousIngestThread.class.getName());

    private static AtomicInteger asynchronousChildThreadCount = new AtomicInteger(0);
    private final ThreadLocal<Organization> organization;
    private final ThreadLocal<User> user;

    private final Runnable executable;
    public AsynchronousIngestThread(Runnable executable, Organization orgUnit, User user) {
        super(String.format("AsynchronousIngestThread-%s", asynchronousChildThreadCount));
        this.executable = executable;
        this.organization = ThreadLocal.withInitial(() -> orgUnit);
        this.user = ThreadLocal.withInitial(() -> user);
        AsynchronousIngestThread.asynchronousChildThreadCount.incrementAndGet();
    }

    @Override
    public void run() {
        logger.debug("{} Starting.", this);
        this.executable.run();
        logger.debug("{} Stopping.", this);
    }

     @Override
    public String toString() {
        return this.getName();
     }

}
