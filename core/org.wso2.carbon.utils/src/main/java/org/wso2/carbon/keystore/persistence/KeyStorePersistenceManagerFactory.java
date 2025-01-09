/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.keystore.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.keystore.persistence.impl.HybridKeyStorePersistenceManager;
import org.wso2.carbon.keystore.persistence.impl.JDBCKeyStorePersistenceManager;
import org.wso2.carbon.keystore.persistence.impl.RegistryKeyStorePersistenceManager;
import org.wso2.carbon.utils.CarbonUtils;

/**
 * Factory class to get the KeyStorePersistenceManager based on the configuration.
 */
public class KeyStorePersistenceManagerFactory {

    private static final Log LOG = LogFactory.getLog(KeyStorePersistenceManagerFactory.class);
    private static String KEYSTORE_STORAGE_TYPE =
            CarbonUtils.getServerConfiguration().getFirstProperty("KeyStoreDataPersistenceManager.DataStorageType");
    private static final String REGISTRY = "registry";
    private static final String HYBRID = "hybrid";
    private static final String DATABASE = "database";

    private KeyStorePersistenceManagerFactory() {

    }

    public static KeyStorePersistenceManager getKeyStorePersistenceManager() {

        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyStore storage type is set to: " + KEYSTORE_STORAGE_TYPE);
        }

        KeyStorePersistenceManager keyStorePersistenceManager;
        if (REGISTRY.equals(KEYSTORE_STORAGE_TYPE)) {
            LOG.warn("Registry based KeyStore persistence manager was initialized");
            keyStorePersistenceManager = new RegistryKeyStorePersistenceManager();
        } else if (HYBRID.equals(KEYSTORE_STORAGE_TYPE)) {
            LOG.info("Hybrid KeyStore persistence manager was initialized");
            keyStorePersistenceManager = new HybridKeyStorePersistenceManager();
        } else {
            keyStorePersistenceManager = new JDBCKeyStorePersistenceManager();
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyStore Persistence Manager initialized with the type: " +
                    keyStorePersistenceManager.getClass());
        }
        return keyStorePersistenceManager;
    }
}
