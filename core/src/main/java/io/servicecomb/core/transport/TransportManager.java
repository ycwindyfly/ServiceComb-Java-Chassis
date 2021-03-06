/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.core.transport;

import java.util.*;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.servicecomb.core.Endpoint;
import io.servicecomb.core.Transport;
import io.servicecomb.foundation.common.exceptions.ServiceCombException;
import io.servicecomb.serviceregistry.RegistryUtils;
import io.servicecomb.serviceregistry.api.registry.Microservice;

@Component
public class TransportManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(TransportManager.class);

  @Inject
  private List<Transport> transports;

  private Map<String, Transport> transportMap = new HashMap<>();

  public void setTransports(List<Transport> transports) {
    this.transports = transports;
  }

  public void init() throws Exception {
    buildTransportMap();

    for (Transport transport : transportMap.values()) {
      if (transport.init()) {
        Endpoint endpoint = transport.getPublishEndpoint();
        if (endpoint != null && endpoint.getEndpoint() != null) {
          LOGGER.info("endpoint to publish: {}", endpoint.getEndpoint());
          Microservice microservice = RegistryUtils.getMicroservice();
          microservice.getIntance().getEndpoints().add(endpoint.getEndpoint());
        }
        continue;
      }
    }
  }

  protected void buildTransportMap() {
    Map<String, List<Transport>> groups = groupByName();

    for (Entry<String, List<Transport>> entry : groups.entrySet()) {
      List<Transport> group = entry.getValue();

      checkTransportGroup(group);
      Transport transport = chooseOneTransport(group);
      transportMap.put(transport.getName(), transport);
    }
  }

  protected Transport chooseOneTransport(List<Transport> group) {
    group.sort(Comparator.comparingInt(Transport::getOrder));

    for (Transport transport : group) {
      if (transport.canInit()) {
        LOGGER.info("choose {} for {}.", transport.getClass().getName(), transport.getName());
        return transport;
      }
    }

    throw new ServiceCombException(
        String.format("all transport named %s refused to init.", group.get(0).getName()));
  }

  protected void checkTransportGroup(List<Transport> group) {
    // order value must be different, otherwise, maybe will choose a random transport
    Map<Integer, Transport> orderMap = new HashMap<>();
    for (Transport transport : group) {
      Transport existTransport = orderMap.putIfAbsent(transport.getOrder(), transport);
      if (existTransport != null) {
        throw new ServiceCombException(String.format("%s and %s have the same order %d",
            existTransport.getClass().getName(),
            transport.getClass().getName(),
            transport.getOrder()));
      }
    }
  }

  protected Map<String, List<Transport>> groupByName() {
    Map<String, List<Transport>> groups = new HashMap<>();
    for (Transport transport : transports) {
      List<Transport> list = groups.computeIfAbsent(transport.getName(), name -> {
        return new ArrayList<>();
      });
      list.add(transport);
    }
    return groups;
  }

  public Transport findTransport(String transportName) {
    return transportMap.get(transportName);
  }
}
