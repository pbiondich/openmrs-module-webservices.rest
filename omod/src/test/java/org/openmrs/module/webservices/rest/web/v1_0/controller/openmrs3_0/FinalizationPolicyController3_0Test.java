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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmrs.ObsFinalizationPolicy;
import org.openmrs.UserType;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.v1_0.controller.RestControllerTestUtils;

public class FinalizationPolicyController3_0Test extends RestControllerTestUtils {
	
	@Test
	public void shouldCreateAndListPolicies() throws Exception {
		SimpleObject created = deserialize(handle(newPostRequest("finalizationpolicy",
		    "{\"name\":\"Devices finalize\",\"agentType\":\"DEVICE\",\"action\":\"AUTO_FINAL\"}")));
		
		Assertions.assertEquals("DEVICE", created.get("agentType"));
		Assertions.assertEquals("AUTO_FINAL", created.get("action"));
		ObsFinalizationPolicy saved = Context.getObsService().getObsFinalizationPolicyByUuid((String) created.get("uuid"));
		Assertions.assertEquals(UserType.DEVICE, saved.getAgentType());
		List<?> all = (List<?>) deserialize(handle(newGetRequest("finalizationpolicy"))).get("results");
		Assertions.assertEquals(1, all.size());
	}
	
	@Test
	public void shouldRefuseASecondPolicyForTheSameAgentTypeAndConceptClass() throws Exception {
		handle(newPostRequest("finalizationpolicy",
		    "{\"name\":\"Software waits\",\"agentType\":\"SOFTWARE\",\"action\":\"REQUIRE_ATTESTATION\"}"));
		
		Assertions.assertThrows(Exception.class, () -> handle(newPostRequest("finalizationpolicy",
		    "{\"name\":\"Software is final\",\"agentType\":\"SOFTWARE\",\"action\":\"AUTO_FINAL\"}")));
	}
}
