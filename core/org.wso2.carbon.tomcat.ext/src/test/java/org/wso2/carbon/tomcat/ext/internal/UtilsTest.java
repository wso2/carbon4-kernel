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

import org.apache.catalina.connector.Request;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wso2.carbon.utils.CarbonUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UtilsTest {

    private static final Logger log = Logger.getLogger("UtilsTest");

    /**
     * Checks getTenantDomain with Case 1.
     * Case 1: Checks if the method returns supper tenant domain when a supper tenant
     * user (here it's admin) accesses the carbon console after a successful login.
     */
    @Test
    public void testGetTenantDomainWithCase1 () {
        // mocking inputs
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/carbon/tenant-dashboard/index.jsp");
        // expected output
        String expected = "carbon.super";
        // received output
        String received = Utils.getTenantDomain(httpServletRequest);
        // check for case 1
        log.info("Testing getTenantDomain () with case 1");
        Assert.assertTrue("Super tenant request URI does not return correct domain 'carbon.super'",
                expected.equals(received));
    }

    /**
     * Checks getTenantDomain with Case 2.
     * Case 2: Checks if the method returns correct tenant domain when a
     * tenant user (here it's tenant admin) accesses the carbon console after a successful login.
     */
    @Test
    public void testGetTenantDomainWithCase2 () {
        // mocking inputs
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/t/abc.com/carbon/admin/index.jsp");
        // expected output
        String expected = "abc.com";
        // received output
        String received = Utils.getTenantDomain(httpServletRequest);
        // check for case 2
        log.info("Testing getTenantDomain () with case 2");
        Assert.assertTrue("Tenant specific request URI does not return correct domain",
                expected.equals(received));
    }

    /**
     * Checks getServiceName with Case 1.
     * Case 1: Checks if the method returns correct service name when
     * a specific super tenant service request URI is given.
     */
    @Test
    public void testGetServiceNameWithCase1 () {
        // mocking inputs
        String sampleRequestURI = "/services/echo";
        // expected output
        String expected = "echo";
        // received output
        String received = Utils.getServiceName(sampleRequestURI);
        // check for case 1
        log.info("Testing getServiceName () with case 1");
        Assert.assertTrue("getServiceName () does not extract correct service name",
                expected.equals(received));
    }

    /**
     * Checks getServiceName with Case 2.
     * Case 2: Checks if the method returns correct service name when
     * a tenant specific service request URI is given.
     */
    @Test
    public void testGetServiceNameWithCase2 () {
        // mocking inputs
        String sampleRequestURI = "/services/t/abc.com/echo";
        // expected output
        String expected = "echo";
        // received output
        String received = Utils.getServiceName(sampleRequestURI);
        // check for case 2
        log.info("Testing getServiceName () with case 2");
        Assert.assertTrue("getServiceName () does not extract correct service name",
                expected.equals(received));
    }

    /**
     * Checks getAppNameFromRequest with Case 1.
     * Case 1: Checks if the method returns correct app name when
     * a service request URI is given.
     */
    @Test
    public void testGetAppNameFromRequestWithCase1 () {
        // mocking inputs
        Request request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/services/t/abc.com/echo");
        // expected output
        String expected = "echo";
        // received output
        String received = Utils.getAppNameFromRequest(request);
        // check for case 1
        log.info("Testing getAppNameFromRequest () with case 1: service requests");
        Assert.assertTrue("getAppNameFromRequest () does not extract correct app name for services",
                expected.equals(received));
    }

    /**
     * Checks getAppNameFromRequest with Case 2.
     * Case 2: Checks if the method returns correct app name when
     * a web-app request URI is given.
     */
    @Test
    public void testGetAppNameFromRequestWithCase2 () {
        // mocking inputs
        Request request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/t/abc.com/carbon/webapps/echo");
        // expected output
        String expected = "echo";
        // received output
        String received = Utils.getAppNameFromRequest(request);
        // check for case 2
        log.info("Testing getAppNameFromRequest () with case 2: webapp requests");
        Assert.assertTrue("getAppNameFromRequest () does not extract correct app name for web-apps",
                expected.equals(received));
    }

    /**
     * Checks getAppNameFromRequest with Case 3.
     * Case 3: Checks if the method returns correct app name when
     * a jaggery-app request URI is given.
     */
    @Test
    public void testGetAppNameFromRequestWithCase3 () {
        // mocking inputs
        Request request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/t/abc.com/carbon/jaggeryapps/echo");
        // expected output
        String expected = "echo";
        // received output
        String received = Utils.getAppNameFromRequest(request);
        // check for case 3
        log.info("Testing getAppNameFromRequest () with case 3: jaggery-app requests");
        Assert.assertTrue("getAppNameFromRequest () does not extract correct app name for jaggery-apps",
                expected.equals(received));
    }

    /**
     * Checks getAppNameFromRequest with Case 4.
     * Case 4: Checks if the method returns correct app name when
     * a jax-web-app request URI is given.
     */
    @Test
    public void testGetAppNameFromRequestWithCase4 () {
        // mocking inputs
        Request request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/t/abc.com/carbon/jaxwebapps/echo");
        // expected output
        String expected = "echo";
        // received output
        String received = Utils.getAppNameFromRequest(request);
        // check for case 4
        log.info("Testing getAppNameFromRequest () with case 4: jax-web-app requests");
        Assert.assertTrue("getAppNameFromRequest () does not extract correct app name for jax-web-apps",
                expected.equals(received));
    }

    /**
     * Checks createDummyTenantContextDir with Case 1.
     * Case 1: Checks if the method can successfully create a directory, given a valid folder location
     */
    @Test
    public void testCreateDummyTenantContextDirWithCase1 () {
        // mocking inputs
        when(CarbonUtils.getTmpDir()).thenReturn(System.getProperty("java.io.tmpdir"));
        // received output
        File created = Utils.createDummyTenantContextDir();
        // check for case 1
        log.info("Testing createDummyTenantContextDir () with case 1");
        Assert.assertTrue("createDummyTenantContextDir () does not create a directory," +
                        " given a valid folder path",created != null && created.exists());
    }

    /**
     * Checks getTenantDomainFromURLMapping with Case 1.
     * Case 1: Checks if the method returns correct tenant domain for a given request call for super tenant.
     */
    @Test
    public void testGetTenantDomainFromURLMappingWithCase1 () {
        // mocking inputs
        Request request = mock(Request.class, Mockito.RETURNS_DEEP_STUBS);
        when(request.getRequestURI()).thenReturn("/carbon/tenant-dashboard/index.jsp");
        when(request.getHost().getName()).thenReturn("localhost");
        when(request.getSession(false)).thenReturn(null);
        // expected output
        String expected = "carbon.super";
        // received output
        String received = Utils.getTenantDomainFromURLMapping(request);
        // check for case 1
        log.info("Testing getTenantDomainFromURLMapping () with case 1");
        Assert.assertTrue("getTenantDomainFromURLMapping () does not extract correct domain for super tenant",
                expected.equals(received));
    }

    /**
     * Checks getTenantDomainFromURLMapping with Case 2.
     * Case 2: Checks if the method returns correct tenant domain for a given request call for tenant.
     */
    @Test
    public void testGetTenantDomainFromURLMappingWithCase2 () {
        // mocking inputs
        Request request = mock(Request.class, Mockito.RETURNS_DEEP_STUBS);
        when(request.getRequestURI()).thenReturn("/t/abc.com/carbon/admin/index.jsp");
        when(request.getHost().getName()).thenReturn("localhost");
        when(request.getSession(false)).thenReturn(null);
        // expected output
        String expected = "abc.com";
        // received output
        String received = Utils.getTenantDomainFromURLMapping(request);
        // check for case 2
        log.info("Testing getTenantDomainFromURLMapping () with case 2");
        Assert.assertTrue("getTenantDomainFromURLMapping () does not extract correct domain for tenant",
                expected.equals(received));
    }
}
