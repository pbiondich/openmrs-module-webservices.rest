/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.webservices.rest.web.v1_0.controller.openmrs3_0;

import org.apache.commons.beanutils.PropertyUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.SoftwareAgent;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestTestConstants1_9;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceControllerTest;

public class ProviderController3_0Test extends MainResourceControllerTest {
	
	@BeforeEach
	public void before() throws Exception {
		executeDataSet(RestTestConstants1_9.TEST_DATASET);
	}
	
	@Test
	public void getProvider_shouldIncludeTheProviderType() throws Exception {
		SimpleObject result = deserialize(handle(newGetRequest(getURI() + "/" + getUuid())));
		
		Assertions.assertEquals("BIOLOGIC", PropertyUtils.getProperty(result, "providerType"));
	}
	
	@Test
	public void createProvider_shouldCreateADigitalProviderForASoftwareUser() throws Exception {
		User extractor = new User();
		extractor.setUsername("extractor-0.1");
		Context.getUserService().createSoftwareAgent(new SoftwareAgent(extractor, "extractor", "0.1"), "Openmr5xy");
		String json = "{\"identifier\":\"extractor-0.1\",\"providerType\":\"DIGITAL\",\"user\":\""
		        + extractor.getUuid() + "\"}";
		
		SimpleObject created = deserialize(handle(newPostRequest(getURI(), json)));
		
		Assertions.assertEquals("DIGITAL", PropertyUtils.getProperty(created, "providerType"));
		Assertions.assertEquals(extractor.getUuid(), PropertyUtils.getProperty(created, "user.uuid"));
	}
	
	@Test
	public void createProvider_shouldTakeOnlyTheUuidOfTheUser() throws Exception {
		User extractor = new User();
		extractor.setUsername("extractor-0.1");
		Context.getUserService().createSoftwareAgent(new SoftwareAgent(extractor, "extractor", "0.1"), "Openmr5xy");
		String json = "{\"identifier\":\"extractor-0.1\",\"providerType\":\"DIGITAL\",\"user\":{\"uuid\":\""
		        + extractor.getUuid() + "\",\"roles\":[\"System Developer\"]}}";
		
		Assertions.assertThrows(ConversionException.class, () -> handle(newPostRequest(getURI(), json)));
		Assertions.assertFalse(Context.getUserService().getUserByUuid(extractor.getUuid()).isSuperUser());
	}
	
	@Test
	public void updateProvider_shouldNotChangeTheProviderType() {
		String json = "{\"providerType\":\"DIGITAL\"}";
		
		Assertions.assertThrows(ConversionException.class, () -> handle(newPostRequest(getURI() + "/" + getUuid(), json)));
	}
	
	@Override
	public String getURI() {
		return "provider";
	}
	
	@Override
	public String getUuid() {
		return RestTestConstants1_9.PROVIDER_UUID;
	}
	
	@Override
	public long getAllCount() {
		return Context.getProviderService().getAllProviders(false).size();
	}
}
