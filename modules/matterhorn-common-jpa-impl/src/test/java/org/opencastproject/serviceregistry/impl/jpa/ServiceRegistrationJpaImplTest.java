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
package org.opencastproject.serviceregistry.impl.jpa;

import static org.junit.Assert.assertEquals;

import org.opencastproject.job.jpa.JpaJob;
import org.opencastproject.security.impl.jpa.JpaOrganization;
import org.opencastproject.security.impl.jpa.JpaRole;
import org.opencastproject.security.impl.jpa.JpaUser;
import org.opencastproject.util.persistence.PersistenceUtil;
import org.opencastproject.util.persistencefn.PersistenceEnv;
import org.opencastproject.util.persistencefn.PersistenceEnvs;
import org.opencastproject.util.persistencefn.Queries;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.persistence.EntityManagerFactory;

public class ServiceRegistrationJpaImplTest {

  private EntityManagerFactory emf;
  private PersistenceEnv env;

  private JpaOrganization org;
  private JpaUser user;

  @Before
  public void setUp() throws Exception {
    emf = PersistenceUtil.newTestEntityManagerFactory("org.opencastproject.common");
    env = PersistenceEnvs.mk(emf);
    setUpOrganizationAndUsers();
  }

  private void setUpOrganizationAndUsers() {
    org = new JpaOrganization("test-org", "Test Organization", "http://testorg.edu", 80, "TEST_ORG_ADMIN",
            "TEST_ORG_ANON", new HashMap<String, String>());
    user = new JpaUser("producer1", "pw-producer1", org, "test", true, new HashSet<JpaRole>());

    org = env.tx(Queries.persistOrUpdate(org));
    user = env.tx(Queries.persistOrUpdate(user));
  }

  @After
  public void cleanUp() {
    env.close();
  }

  @Test
  public void testQueryStatistics() throws Exception {
    HostRegistrationJpaImpl host = new HostRegistrationJpaImpl("http://localhost:8081", "http://localhost:8081", 1024L,
            1, 1, true, false);
    ServiceRegistrationJpaImpl serviceReg = new ServiceRegistrationJpaImpl(host, "NOP", "/nop", false);
    JpaJob job = new JpaJob(user, org, serviceReg, "NOP", null, null, true, 1.0F);
    job.setProcessorServiceRegistration(serviceReg);
    job.setQueueTime(500L);
    job.setRunTime(1000L);

    host = env.tx(Queries.persistOrUpdate(host));
    serviceReg = env.tx(Queries.persistOrUpdate(serviceReg));
    job = env.tx(Queries.persistOrUpdate(job));

    List<Object> statistic = env.tx(Queries.named.findAll("ServiceRegistration.statistics"));
    Object[] stats = (Object[]) statistic.get(0);
    assertEquals(1, statistic.size());
    assertEquals(5, stats.length);
    assertEquals(serviceReg.getId().longValue(), ((Number) stats[0]).longValue());
    assertEquals(job.getStatus().ordinal(), ((Number) stats[1]).intValue());
    assertEquals(1, ((Number) stats[2]).intValue());
    assertEquals(500L, ((Number) stats[3]).longValue());
    assertEquals(1000L, ((Number) stats[4]).longValue());
  }

}