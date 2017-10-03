/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.carbon.tomcat.ext.internal;

import org.junit.Assert;
import org.junit.Test;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.user.core.service.RealmService;

import java.util.logging.Logger;

import static org.mockito.Mockito.mock;

public class CarbonRealmServiceHolderTest {

    private static final Logger log = Logger.getLogger("CarbonRealmServiceHolderTest");

    /**
     * Testing getters and setters for Realm Service.
     */
    @Test
    public void testRealmService () {
        // mocking inputs
        RealmService realmService = mock(RealmService.class);
        // calling set method
        CarbonRealmServiceHolder.setRealmService(realmService);
        // checking retrieved values
        log.info("Testing getters and setters for realmService");
        Assert.assertEquals("retrieved value did not match with set value for realmService",
                realmService, CarbonRealmServiceHolder.getRealmService());
    }

    /**
     * Testing getters and setters for Registry Service.
     */
    @Test
    public void testRegistryService () {
        // mocking inputs
        RegistryService registryService = mock(RegistryService.class);
        // calling set method
        CarbonRealmServiceHolder.setRegistryService(registryService);
        // checking retrieved values
        log.info("Testing getters and setters for registryService");
        Assert.assertEquals("retrieved value did not match with set value for registryService",
                registryService, CarbonRealmServiceHolder.getRegistryService());
    }
}
