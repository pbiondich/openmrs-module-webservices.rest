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

import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestTestConstants1_8;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceControllerTest;
import org.openmrs.module.webservices.rest.web.v1_0.controller.RestControllerTestUtils;
import org.openmrs.util.PrivilegeConstants;

/**
 * Phase 1 of the assertion model, end to end through REST: an extraction model registers as a software
 * user with a digital provider, posts an obs as itself, and is recorded as that obs's asserter without
 * naming itself. Patient search never finds it.
 */
public class SoftwareAsserterAcceptance3_0Test extends RestControllerTestUtils {
	
	// what an extractor's role would grant it: post obs, and read what it posts them about
	private static final String[] EXTRACTOR_PRIVILEGES = { PrivilegeConstants.ADD_OBS, PrivilegeConstants.GET_OBS,
	        PrivilegeConstants.GET_CONCEPTS, PrivilegeConstants.GET_CONCEPT_DATATYPES, PrivilegeConstants.GET_CONCEPT_CLASSES,
	        PrivilegeConstants.GET_PERSONS, PrivilegeConstants.GET_PATIENTS, PrivilegeConstants.GET_ENCOUNTERS,
	        PrivilegeConstants.GET_LOCATIONS, PrivilegeConstants.GET_PROVIDERS, PrivilegeConstants.GET_USERS,
	        PrivilegeConstants.GET_GLOBAL_PROPERTIES };
	
	@Test
	public void anExtractorPostingAnObsShouldBeRecordedAsItsAsserter() throws Exception {
		String user = "{\"username\":\"extractor-0.1\",\"password\":\"Openmr5xy\",\"userType\":\"SOFTWARE\","
		        + "\"softwareAgent\":{\"name\":\"extractor\",\"version\":\"0.1\",\"modelIdentifier\":\"local-7b\"}}";
		String userUuid = (String) PropertyUtils.getProperty(deserialize(handle(newPostRequest("user", user))), "uuid");
		String provider = "{\"identifier\":\"extractor-0.1\",\"providerType\":\"DIGITAL\",\"user\":\"" + userUuid + "\"}";
		String providerUuid = (String) PropertyUtils
		        .getProperty(deserialize(handle(newPostRequest("provider", provider))), "uuid");
		
		Context.logout();
		Context.authenticate("extractor-0.1", "Openmr5xy");
		for (String privilege : EXTRACTOR_PRIVILEGES) {
			Context.addProxyPrivilege(privilege);
		}
		String obs = "{\"value\":\"10.0\",\"person\":\"" + RestTestConstants1_8.PERSON_UUID
		        + "\",\"concept\":\"SNOMED CT:2332523\",\"obsDatetime\":\"2013-12-09T00:00:00.000+0100\"}";
		SimpleObject created = deserialize(handle(newPostRequest("obs", obs)));
		
		Assertions.assertEquals(providerUuid, PropertyUtils.getProperty(created, "asserter.uuid"));
		for (String privilege : EXTRACTOR_PRIVILEGES) {
			Context.removeProxyPrivilege(privilege);
		}
		Context.logout();
		authenticate();
		SimpleObject patients = deserialize(handle(newGetRequest("patient", new MainResourceControllerTest.Parameter("q", "extractor"))));
		Assertions.assertEquals(0, ((List<?>) patients.get("results")).size());
	}
}
