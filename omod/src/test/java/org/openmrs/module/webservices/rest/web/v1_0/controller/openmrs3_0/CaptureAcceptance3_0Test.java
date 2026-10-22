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
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmrs.api.capture.CaptureRetentionTaskData;
import org.openmrs.api.capture.CaptureRetentionTaskHandler;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestTestConstants1_8;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceControllerTest.Parameter;
import org.openmrs.module.webservices.rest.web.v1_0.controller.RestControllerTestUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

/**
 * Phase 3 of the assertion model, end to end through REST: a scanned IMCI sheet is uploaded as a
 * capture of its encounter; an extractor's run over it is posted as a derivation; an obs is posted
 * with the capture, a span on the sheet and the derivation, and a GET shows them; the artifact
 * downloads as a file, never as a page; and the retention task deletes an expired audio recording's
 * artifact but never the scanned form's.
 */
public class CaptureAcceptance3_0Test extends RestControllerTestUtils {
	
	static final String ENCOUNTER_UUID = RestTestConstants1_8.ENCOUNTER_UUID;
	
	/** The patient of that encounter in the standard test data. */
	static final String PATIENT_UUID = "5946f880-b197-400b-9caa-a3c661d23041";
	
	static final byte[] IMCI_SHEET = "%PDF-1.4 an IMCI sheet".getBytes(StandardCharsets.UTF_8);
	
	@Test
	public void anExtractorsObsShouldPointIntoTheScannedSheetItCameFrom() throws Exception {
		SimpleObject sheet = upload(this, "imci.pdf", "application/pdf", IMCI_SHEET,
		    "{\"encounter\":\"" + ENCOUNTER_UUID + "\",\"kind\":\"SCANNED_FORM\",\"consentStatus\":\"GIVEN\"}");
		Assertions.assertEquals("SCANNED_FORM", sheet.get("kind"));
		Assertions.assertNull(sheet.get("expiresAt"));
		
		SimpleObject derivation = deserialize(handle(newPostRequest("derivation", "{\"capture\":\"" + sheet.get("uuid")
		        + "\",\"started\":\"2026-10-22T10:00:00.000+0000\",\"finished\":\"2026-10-22T10:00:05.000+0000\","
		        + "\"paramsHash\":\"sha256:abc\"}")));
		
		String span = "xywh=412,880,60,24;field=weight;page=1";
		SimpleObject obs = deserialize(handle(newPostRequest("obs", "{\"person\":\"" + PATIENT_UUID + "\",\"encounter\":\""
		        + ENCOUNTER_UUID + "\",\"concept\":\"SNOMED CT:2332523\",\"value\":\"12.5\","
		        + "\"obsDatetime\":\"2026-10-22T09:00:00.000+0000\",\"capture\":\"" + sheet.get("uuid") + "\",\"span\":\""
		        + span + "\",\"derivation\":\"" + derivation.get("uuid") + "\"}")));
		
		SimpleObject fetched = deserialize(handle(newGetRequest("obs/" + obs.get("uuid"))));
		Assertions.assertEquals(span, fetched.get("span"));
		Assertions.assertEquals(sheet.get("uuid"), ((Map<?, ?>) fetched.get("capture")).get("uuid"));
		Assertions.assertEquals(derivation.get("uuid"), ((Map<?, ?>) fetched.get("derivation")).get("uuid"));
		
		MockHttpServletResponse artifact = handle(newGetRequest("capture/" + sheet.get("uuid") + "/artifact"));
		Assertions.assertArrayEquals(IMCI_SHEET, artifact.getContentAsByteArray());
		Assertions.assertEquals("application/pdf", artifact.getContentType());
		Assertions.assertEquals("nosniff", artifact.getHeader("X-Content-Type-Options"));
		Assertions.assertTrue(artifact.getHeader("Content-Disposition").startsWith("attachment"));
	}
	
	@Test
	public void theRetentionTaskShouldDeleteExpiredAudioButNeverAScannedForm() throws Exception {
		SimpleObject sheet = upload(this, "imci.pdf", "application/pdf", IMCI_SHEET,
		    "{\"encounter\":\"" + ENCOUNTER_UUID + "\",\"kind\":\"SCANNED_FORM\"}");
		SimpleObject audio = upload(this, "visit.mp3", "audio/mpeg", "a recording".getBytes(StandardCharsets.UTF_8),
		    "{\"encounter\":\"" + ENCOUNTER_UUID + "\",\"kind\":\"AUDIO\"}");
		Assertions.assertNotNull(audio.get("expiresAt"));
		// the recording is long past its retention period
		Context.getAdministrationService().executeSQL("update capture set expires_at = '2000-01-01 00:00:00'"
		        + " where uuid = '" + audio.get("uuid") + "'", false);
		Context.clearSession();
		
		Context.getRegisteredComponents(CaptureRetentionTaskHandler.class).get(0).execute(new CaptureRetentionTaskData(),
		    null);
		
		Assertions.assertNotNull(capture(audio).get("artifactDeletedAt"));
		Assertions.assertEquals(404, status(newGetRequest("capture/" + audio.get("uuid") + "/artifact")));
		Assertions.assertNull(capture(sheet).get("artifactDeletedAt"));
		Assertions.assertArrayEquals(IMCI_SHEET,
		    handle(newGetRequest("capture/" + sheet.get("uuid") + "/artifact")).getContentAsByteArray());
		Assertions.assertEquals(2, ((java.util.List<?>) deserialize(
		    handle(newGetRequest("capture", new Parameter("encounter", ENCOUNTER_UUID)))).get("results")).size());
	}
	
	private SimpleObject capture(SimpleObject capture) throws Exception {
		return deserialize(handle(newGetRequest("capture/" + capture.get("uuid"))));
	}
	
	private int status(org.springframework.mock.web.MockHttpServletRequest request) throws Exception {
		try {
			return handle(request).getStatus();
		}
		catch (org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException e) {
			return 404;
		}
	}
	
	static SimpleObject upload(RestControllerTestUtils test, String filename, String mimeType, byte[] content, String json)
	        throws Exception {
		MockMultipartHttpServletRequest request = test.newUploadRequest("capture");
		request.addFile(new MockMultipartFile("file", filename, mimeType, content));
		request.addParameter("json", json);
		return test.deserialize(test.handle(request));
	}
}
