/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.serveraction.upgrades;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.actionmanager.HostRoleStatus;
import org.apache.ambari.server.agent.CommandReport;
import org.apache.ambari.server.serveraction.AbstractServerAction;
import org.apache.ambari.server.state.Cluster;
import org.apache.ambari.server.state.Clusters;
import org.apache.ambari.server.state.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Replaces references to the old stack ("iop" and "IOP") with the new
 * stack ("hdp" and "HDP") during upgrade from BigInsights to HDP.
 */
public class ChangeStackReferencesAction extends AbstractServerAction {

  private static final Logger LOG = LoggerFactory.getLogger(ChangeStackReferencesAction.class);
  private static final Set<String> SKIP_PROPERTIES = ImmutableSet.of("cluster-env/stack_root");
  private static final Set<Map.Entry<String, String>> REPLACEMENTS = Maps.asMap(
    Sets.newHashSet("/usr/iop", "iop/apps", "iop.version", "IOP_VERSION"),
    new Function<String, String>() {
      @Nullable
      @Override
      public String apply(@Nullable String input) {
        return input != null
          ? input.replace("iop", "hdp").replace("IOP", "HDP")
          : null;
      }
    }
  ).entrySet();

  @Inject
  private Clusters clusters;

  @Override
  public CommandReport execute(ConcurrentMap<String, Object> requestSharedDataContext) throws AmbariException, InterruptedException {
    StringBuilder out = new StringBuilder();

    String msg = "Changing stack-specific references from IOP to HDP";
    LOG.info(msg);
    out.append(msg).append(System.lineSeparator());

    String clusterName = getExecutionCommand().getClusterName();
    Cluster cluster = clusters.getCluster(clusterName);

    for (String configType: cluster.getDesiredConfigs().keySet()) {
      Config config = cluster.getDesiredConfigByType(configType);
      String typeMsg = String.format("Checking config type=%s version=%s tag=%s", config.getType(), config.getVersion(), config.getTag());
      LOG.debug(typeMsg);
      out.append(typeMsg).append(System.lineSeparator());
      Map<String, String> properties = config.getProperties();
      if (!properties.isEmpty()) {
        Map<String, String> changedProperties = Maps.newHashMap();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
          String key = entry.getKey();
          String original = entry.getValue();
          if (original != null && !SKIP_PROPERTIES.contains(configType + "/" + key)) {
            String replaced = original;
            for (Map.Entry<String, String> replacement : REPLACEMENTS) {
              replaced = replaced.replace(replacement.getKey(), replacement.getValue());
            }
            if (!replaced.equals(original)) {
              changedProperties.put(key, replaced);
              String itemMsg = String.format("Changing %s", key);
              LOG.debug(itemMsg);
              out.append(itemMsg).append(System.lineSeparator());
            }
          }
        }
        if (!changedProperties.isEmpty()) {
          config.updateProperties(changedProperties);
          config.save();
        }
      }
    }

    return createCommandReport(0, HostRoleStatus.COMPLETED, "{}", out.toString(), "");
  }
}
