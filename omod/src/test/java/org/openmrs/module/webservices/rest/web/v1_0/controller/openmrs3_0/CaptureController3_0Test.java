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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmrs.api.ValidationException;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceControllerTest.Parameter;
import org.openmrs.module.webservices.rest.web.v1_0.controller.RestControllerTestUtils;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;

/**
 * Tests for the capture and derivation resources, and for capture and span on obs.
 */
public class CaptureController3_0Test extends RestControllerTestUtils {
	
	private static final String JSON = "{\"encounter\":\"" + CaptureAcceptance3_0Test.ENCOUNTER_UUID
	        + "\",\"kind\":\"TYPED_TEXT\"}";
	
	private static final byte[] NOTE = "a typed note".getBytes(StandardCharsets.UTF_8);
	
	@Test
	public void upload_shouldRefuseAMediaTypeTheKindDoesNotAllow() {
		Assertions.assertThrows(IllegalRequestException.class,
		    () -> CaptureAcceptance3_0Test.upload(this, "note.html", "text/html", NOTE, JSON));
	}
	
	@Test
	public void upload_shouldRefuseFieldsOtherThanTheCapturesOwn() {
		Assertions.assertThrows(IllegalRequestException.class, () -> CaptureAcceptance3_0Test.upload(this, "note.txt",
		    "text/plain", NOTE, JSON.replace("}", ",\"artifactKey\":\"captures/x\"}")));
		Assertions.assertThrows(IllegalRequestException.class, () -> CaptureAcceptance3_0Test.upload(this, "note.txt",
		    "text/plain", NOTE, JSON.replace("\"" + CaptureAcceptance3_0Test.ENCOUNTER_UUID + "\"", "{\"uuid\":\"x\"}")));
	}
	
	@Test
	public void post_shouldRefuseACaptureWithoutItsArtifact() {
		Assertions.assertThrows(ResourceDoesNotSupportOperationException.class, () -> handle(newPostRequest("capture", JSON)));
	}
	
	@Test
	public void update_shouldTakeOnlyWithdrawnConsent() throws Exception {
		SimpleObject note = CaptureAcceptance3_0Test.upload(this, "note.txt", "text/plain", NOTE, JSON);
		
		Assertions.assertThrows(IllegalRequestException.class,
		    () -> handle(newPostRequest("capture/" + note.get("uuid"), "{\"kind\":\"AUDIO\"}")));
		SimpleObject withdrawn = deserialize(
		    handle(newPostRequest("capture/" + note.get("uuid"), "{\"consentStatus\":\"WITHDRAWN\"}")));
		
		Assertions.assertEquals("WITHDRAWN", withdrawn.get("consentStatus"));
	}
	
	@Test
	public void delete_shouldVoidAndPurgeShouldBeRefused() throws Exception {
		SimpleObject note = CaptureAcceptance3_0Test.upload(this, "note.txt", "text/plain", NOTE, JSON);
		
		Assertions.assertThrows(ResourceDoesNotSupportOperationException.class,
		    () -> handle(newDeleteRequest("capture/" + note.get("uuid"), new Parameter("purge", "true"))));
		handle(newDeleteRequest("capture/" + note.get("uuid"), new Parameter("reason", "wrong patient")));
		
		Assertions.assertEquals(true, deserialize(handle(newGetRequest("capture/" + note.get("uuid")))).get("voided"));
	}
	
	@Test
	public void derivation_shouldTakeItsCaptureByUuidOnly() throws Exception {
		SimpleObject note = CaptureAcceptance3_0Test.upload(this, "note.txt", "text/plain", NOTE, JSON);
		
		// refused as a conversion error, a 400, before anything is looked up
		Assertions.assertThrows(ConversionException.class, () -> handle(newPostRequest("derivation",
		    "{\"capture\":{\"uuid\":\"" + note.get("uuid") + "\"},\"started\":\"2026-10-22T10:00:00.000+0000\","
		            + "\"finished\":\"2026-10-22T10:00:05.000+0000\"}")));
	}
	
	@Test
	public void obs_shouldRefuseASpanThatDoesNotFitItsCapture() throws Exception {
		SimpleObject note = CaptureAcceptance3_0Test.upload(this, "note.txt", "text/plain", NOTE, JSON);
		
		Assertions.assertThrows(ValidationException.class, () -> handle(newPostRequest("obs", "{\"person\":\""
		        + CaptureAcceptance3_0Test.PATIENT_UUID + "\",\"encounter\":\"" + CaptureAcceptance3_0Test.ENCOUNTER_UUID
		        + "\",\"concept\":\"SNOMED CT:2332523\",\"value\":\"12.5\",\"obsDatetime\":\"2026-10-22T09:00:00.000+0000\","
		        + "\"capture\":\"" + note.get("uuid") + "\",\"span\":\"t=1.0-2.0\"}")));
	}
	
	@Test
	public void obs_shouldNotTakeACaptureOrSpanOnUpdate() throws Exception {
		SimpleObject note = CaptureAcceptance3_0Test.upload(this, "note.txt", "text/plain", NOTE, JSON);
		SimpleObject obs = deserialize(handle(newPostRequest("obs", "{\"person\":\"" + CaptureAcceptance3_0Test.PATIENT_UUID
		        + "\",\"encounter\":\"" + CaptureAcceptance3_0Test.ENCOUNTER_UUID
		        + "\",\"concept\":\"SNOMED CT:2332523\",\"value\":\"12.5\",\"obsDatetime\":\"2026-10-22T09:00:00.000+0000\"}")));
		
		Assertions.assertThrows(ConversionException.class, () -> handle(
		    newPostRequest("obs/" + obs.get("uuid"), "{\"capture\":\"" + note.get("uuid") + "\",\"span\":\"c=1-2\"}")));
	}
	
	@Test
	public void derivation_shouldBeListedByCaptureNeverChangedAndVoidedOnDelete() throws Exception {
		SimpleObject note = CaptureAcceptance3_0Test.upload(this, "note.txt", "text/plain", NOTE, JSON);
		SimpleObject derivation = deserialize(handle(newPostRequest("derivation", "{\"capture\":\"" + note.get("uuid")
		        + "\",\"started\":\"2026-10-22T10:00:00.000+0000\",\"finished\":\"2026-10-22T10:00:05.000+0000\"}")));
		
		Assertions.assertEquals(1, ((java.util.List<?>) deserialize(
		    handle(newGetRequest("derivation", new Parameter("capture", (String) note.get("uuid"))))).get("results")).size());
		Assertions.assertThrows(ResourceDoesNotSupportOperationException.class,
		    () -> handle(newPostRequest("derivation/" + derivation.get("uuid"), "{\"paramsHash\":\"x\"}")));
		handle(newDeleteRequest("derivation/" + derivation.get("uuid"), new Parameter("reason", "rerun")));
		Assertions.assertEquals(true, deserialize(handle(newGetRequest("derivation/" + derivation.get("uuid")))).get("voided"));
		Assertions.assertTrue(((java.util.List<?>) deserialize(
		    handle(newGetRequest("derivation", new Parameter("capture", "no-such-capture")))).get("results")).isEmpty());
	}
}
