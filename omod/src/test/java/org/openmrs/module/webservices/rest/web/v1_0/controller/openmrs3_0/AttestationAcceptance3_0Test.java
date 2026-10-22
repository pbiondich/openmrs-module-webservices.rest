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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestTestConstants1_8;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceControllerTest.Parameter;
import org.openmrs.module.webservices.rest.web.v1_0.controller.RestControllerTestUtils;
import org.openmrs.util.PrivilegeConstants;

/**
 * Phase 2 of the assertion model, end to end through REST: an extractor posts five obs, which wait
 * preliminary under the software policy; a nurse attests four accepted and one rejected; the four are
 * final, the rejected one stays preliminary with its outcome on record; editing an accepted obs makes
 * an amended version, and voiding one marks it entered in error. The plan's "default GET returns
 * none" waits for the read path decision on the attestation PR.
 */
public class AttestationAcceptance3_0Test extends RestControllerTestUtils {
	
	private static final String[] EXTRACTOR_PRIVILEGES = { PrivilegeConstants.ADD_OBS, PrivilegeConstants.GET_OBS,
	        PrivilegeConstants.GET_CONCEPTS, PrivilegeConstants.GET_CONCEPT_DATATYPES, PrivilegeConstants.GET_CONCEPT_CLASSES,
	        PrivilegeConstants.GET_PERSONS, PrivilegeConstants.GET_PATIENTS, PrivilegeConstants.GET_ENCOUNTERS,
	        PrivilegeConstants.GET_LOCATIONS, PrivilegeConstants.GET_PROVIDERS, PrivilegeConstants.GET_USERS,
	        PrivilegeConstants.GET_GLOBAL_PROPERTIES };
	
	/** The admin user's own provider, a person, in the standard test data. */
	private static final String NURSE_PROVIDER_UUID = "c2299800-cca9-11e0-9572-0800200c9a66";
	
	@Test
	public void aNurseShouldAttestWhatAnExtractorPosted() throws Exception {
		handle(newPostRequest("finalizationpolicy",
		    "{\"name\":\"Software obs need attestation\",\"agentType\":\"SOFTWARE\",\"action\":\"REQUIRE_ATTESTATION\"}"));
		registerExtractor();
		
		List<String> posted = postAsExtractor(5);
		for (String uuid : posted) {
			Assertions.assertEquals("PRELIMINARY", status(uuid));
		}
		Assertions.assertEquals(5, pending().size());
		
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < posted.size(); i++) {
			items.append(i == 0 ? "" : ",").append("{\"obs\":\"").append(posted.get(i)).append("\",\"outcome\":\"")
			        .append(i < 4 ? "ACCEPTED" : "REJECTED").append("\"}");
		}
		SimpleObject attestation = deserialize(handle(newPostRequest("attestation",
		    "{\"attester\":\"" + NURSE_PROVIDER_UUID + "\",\"items\":[" + items + "]}")));
		
		for (int i = 0; i < 4; i++) {
			Assertions.assertEquals("FINAL", status(posted.get(i)));
		}
		Assertions.assertEquals("PRELIMINARY", status(posted.get(4)));
		SimpleObject saved = deserialize(handle(newGetRequest("attestation/" + attestation.get("uuid"))));
		Map<?, ?> rejected = (Map<?, ?>) ((List<?>) saved.get("items")).get(4);
		Assertions.assertEquals("REJECTED", rejected.get("outcome"));
		Assertions.assertEquals(posted.get(4), ((Map<?, ?>) rejected.get("obs")).get("uuid"));
		Assertions.assertEquals(0, pending().size());
		
		SimpleObject amended = deserialize(handle(newPostRequest("obs/" + posted.get(0), "{\"value\":\"11.0\"}")));
		Assertions.assertEquals("AMENDED", amended.get("status"));
		
		handle(newDeleteRequest("obs/" + posted.get(1), new Parameter("reason", "wrong patient")));
		Assertions.assertEquals("ENTERED_IN_ERROR", status(posted.get(1)));
	}
	
	private void registerExtractor() throws Exception {
		String user = "{\"username\":\"extractor-0.1\",\"password\":\"Openmr5xy\",\"userType\":\"SOFTWARE\","
		        + "\"softwareAgent\":{\"name\":\"extractor\",\"version\":\"0.1\"}}";
		String userUuid = (String) PropertyUtils.getProperty(deserialize(handle(newPostRequest("user", user))), "uuid");
		handle(newPostRequest("provider", "{\"identifier\":\"extractor-0.1\",\"providerType\":\"DIGITAL\",\"user\":\""
		        + userUuid + "\"}"));
	}
	
	private List<String> postAsExtractor(int count) throws Exception {
		Context.logout();
		Context.authenticate("extractor-0.1", "Openmr5xy");
		for (String privilege : EXTRACTOR_PRIVILEGES) {
			Context.addProxyPrivilege(privilege);
		}
		List<String> uuids = new ArrayList<String>();
		try {
			for (int i = 0; i < count; i++) {
				String obs = "{\"value\":\"" + (10 + i) + ".0\",\"person\":\"" + RestTestConstants1_8.PATIENT_UUID
				        + "\",\"concept\":\"SNOMED CT:2332523\",\"obsDatetime\":\"2013-12-09T00:00:00.000+0100\","
				        + "\"status\":\"FINAL\"}";
				uuids.add((String) deserialize(handle(newPostRequest("obs", obs))).get("uuid"));
			}
		}
		finally {
			for (String privilege : EXTRACTOR_PRIVILEGES) {
				Context.removeProxyPrivilege(privilege);
			}
			Context.logout();
			authenticate();
		}
		return uuids;
	}
	
	private String status(String obsUuid) throws Exception {
		return (String) deserialize(handle(newGetRequest("obs/" + obsUuid, new Parameter("v", "full")))).get("status");
	}
	
	private List<?> pending() throws Exception {
		return (List<?>) deserialize(handle(newGetRequest("obs", new Parameter("patient", RestTestConstants1_8.PATIENT_UUID),
		    new Parameter("pendingAttestation", "true")))).get("results");
	}
}
