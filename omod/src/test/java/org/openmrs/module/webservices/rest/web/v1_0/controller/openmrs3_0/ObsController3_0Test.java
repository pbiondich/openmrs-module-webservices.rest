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
import org.junit.jupiter.api.Test;
import org.openmrs.Provider;
import org.openmrs.ProviderType;
import org.openmrs.SoftwareAgent;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestTestConstants1_8;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceControllerTest;

public class ObsController3_0Test extends MainResourceControllerTest {
	
	@Test
	public void createObs_shouldRecordTheAsserterTheCallerNames() throws Exception {
		User extractor = new User();
		extractor.setUsername("extractor-0.1");
		Context.getUserService().createSoftwareAgent(new SoftwareAgent(extractor, "extractor", "0.1"), "Openmr5xy");
		Provider provider = new Provider();
		provider.setProviderType(ProviderType.DIGITAL);
		provider.setUser(extractor);
		Context.getProviderService().saveProvider(provider);
		String json = "{\"value\":\"10.0\",\"person\":\"" + RestTestConstants1_8.PERSON_UUID
		        + "\",\"concept\":\"SNOMED CT:2332523\",\"obsDatetime\":\"2013-12-09T00:00:00.000+0100\",\"asserter\":\""
		        + provider.getUuid() + "\"}";
		
		SimpleObject created = deserialize(handle(newPostRequest(getURI(), json)));
		
		Assertions.assertEquals(provider.getUuid(), PropertyUtils.getProperty(created, "asserter.uuid"));
	}
	
	@Test
	public void getObs_shouldIncludeTheAsserter() throws Exception {
		SimpleObject result = deserialize(handle(newGetRequest(getURI() + "/" + getUuid())));
		
		Assertions.assertTrue(result.containsKey("asserter"));
	}
	
	@Override
	@Test
	public void shouldGetAll() {
		Assertions.assertThrows(ResourceDoesNotSupportOperationException.class, super::shouldGetAll);
	}
	
	@Override
	public String getURI() {
		return "obs";
	}
	
	@Override
	public String getUuid() {
		return RestTestConstants1_8.OBS_UUID;
	}
	
	@Override
	public long getAllCount() {
		return Context.getObsService().getObservationCount(null, true);
	}
}
