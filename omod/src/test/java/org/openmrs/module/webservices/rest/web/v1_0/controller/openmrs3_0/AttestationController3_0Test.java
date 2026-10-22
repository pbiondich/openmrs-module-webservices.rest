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

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Obs;
import org.openmrs.ObsFinalizationPolicy;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.ProviderType;
import org.openmrs.SoftwareAgent;
import org.openmrs.User;
import org.openmrs.UserType;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.v1_0.controller.RestControllerTestUtils;

public class AttestationController3_0Test extends RestControllerTestUtils {
	
	private static final String NURSE_PROVIDER_UUID = "c2299800-cca9-11e0-9572-0800200c9a66";
	
	private Obs obs;
	
	@BeforeEach
	public void savePreliminaryObs() {
		Context.getObsService().saveObsFinalizationPolicy(new ObsFinalizationPolicy("software waits", UserType.SOFTWARE,
		        null, ObsFinalizationPolicy.Action.REQUIRE_ATTESTATION));
		User user = new User();
		user.setUsername("extractor-0.1");
		Context.getUserService().createSoftwareAgent(new SoftwareAgent(user, "extractor", "0.1"), "Openmr5xy");
		Provider extractor = new Provider();
		extractor.setProviderType(ProviderType.DIGITAL);
		extractor.setUser(user);
		Context.getProviderService().saveProvider(extractor);
		obs = new Obs();
		obs.setPerson(new Person(7));
		obs.setConcept(Context.getConceptService().getConcept(5089));
		obs.setObsDatetime(new Date());
		obs.setValueNumeric(70d);
		obs.setAsserter(extractor);
		Context.getObsService().saveObs(obs, null);
		Assertions.assertEquals(Obs.Status.PRELIMINARY, obs.getStatus());
	}
	
	@Test
	public void shouldAmendAnObsWithACorrectedValue() throws Exception {
		SimpleObject attestation = deserialize(handle(newPostRequest("attestation", "{\"attester\":\"" + NURSE_PROVIDER_UUID
		        + "\",\"items\":[{\"obs\":\"" + obs.getUuid() + "\",\"outcome\":\"amended\","
		        + "\"supersedingObs\":{\"value\":72,\"comment\":\"re-weighed\"}}]}")));
		
		Obs replaced = Context.getObsService().getObsByUuid(obs.getUuid());
		Assertions.assertTrue(replaced.getVoided());
		Obs amended = Context.getObsService().getRevisionObs(replaced);
		Assertions.assertEquals(72d, amended.getValueNumeric());
		Assertions.assertEquals(Obs.Status.FINAL, amended.getStatus());
		Assertions.assertEquals(NURSE_PROVIDER_UUID, amended.getAsserter().getUuid());
		Assertions.assertNotNull(attestation.get("uuid"));
	}
	
	@Test
	public void shouldRefuseACorrectionCarryingMoreThanAValue() {
		Assertions.assertThrows(Exception.class, () -> handle(newPostRequest("attestation", "{\"attester\":\""
		        + NURSE_PROVIDER_UUID + "\",\"items\":[{\"obs\":\"" + obs.getUuid() + "\",\"outcome\":\"AMENDED\","
		        + "\"supersedingObs\":{\"value\":72,\"groupMembers\":[{\"value\":1}]}}]}")));
		
		Assertions.assertFalse(Context.getObsService().getObsByUuid(obs.getUuid()).getVoided());
	}
	
	@Test
	public void shouldRefuseAnObsOrEncounterGivenAsAnObject() {
		Assertions.assertThrows(Exception.class, () -> handle(newPostRequest("attestation", "{\"attester\":\""
		        + NURSE_PROVIDER_UUID + "\",\"items\":[{\"obs\":{\"uuid\":\"" + obs.getUuid()
		        + "\",\"person\":{\"gender\":\"M\"}},\"outcome\":\"LEFT_PRELIMINARY\"}]}")));
		
		Assertions.assertEquals("F", Context.getPersonService().getPerson(7).getGender());
	}
	
	@Test
	public void shouldNotChangeAnAttestation() throws Exception {
		SimpleObject attestation = deserialize(handle(newPostRequest("attestation", "{\"attester\":\""
		        + NURSE_PROVIDER_UUID + "\",\"items\":[{\"obs\":\"" + obs.getUuid() + "\",\"outcome\":\"REJECTED\"}]}")));
		
		Assertions.assertThrows(Exception.class, () -> handle(newPostRequest("attestation/" + attestation.get("uuid"),
		    "{\"items\":[{\"obs\":\"" + obs.getUuid() + "\",\"outcome\":\"ACCEPTED\"}]}")));
		Assertions.assertEquals(Obs.Status.PRELIMINARY, Context.getObsService().getObsByUuid(obs.getUuid()).getStatus());
	}
}
