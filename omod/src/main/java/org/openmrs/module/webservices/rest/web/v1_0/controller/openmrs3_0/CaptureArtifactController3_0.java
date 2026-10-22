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

import java.io.InputStream;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.openmrs.Capture;
import org.openmrs.api.APIException;
import org.openmrs.api.CaptureService;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Streams a capture's artifact. It is always sent as a download of its stored media type, which the
 * browser is told not to second-guess, so an artifact is never rendered as a page.
 *
 * @since 3.0
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/capture")
public class CaptureArtifactController3_0 extends BaseRestController {
	
	@RequestMapping(value = "/{uuid}/artifact", method = RequestMethod.GET)
	public void getArtifact(@PathVariable("uuid") String uuid, HttpServletResponse response) throws Exception {
		CaptureService captureService = Context.getCaptureService();
		Capture capture = captureService.getCaptureByUuid(uuid);
		if (capture == null) {
			throw new ObjectNotFoundException();
		}
		InputStream artifact;
		try {
			artifact = captureService.getArtifact(capture);
		}
		catch (APIException e) {
			throw new ObjectNotFoundException(e.getMessage());
		}
		try (InputStream in = artifact) {
			response.setContentType(capture.getMimeType());
			response.setHeader("X-Content-Type-Options", "nosniff");
			response.setHeader("Content-Disposition", "attachment; filename=\"" + capture.getUuid() + "\"");
			response.setHeader("Content-Security-Policy", "sandbox");
			if (capture.getSha256() != null) {
				response.setHeader("ETag", "\"" + capture.getSha256() + "\"");
			}
			IOUtils.copy(in, response.getOutputStream());
		}
		response.flushBuffer();
	}
}
