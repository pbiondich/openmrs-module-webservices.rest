/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs3_0;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Capture;
import org.openmrs.Encounter;
import org.openmrs.api.APIException;
import org.openmrs.api.CaptureService;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.api.Uploadable;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.EmptySearchResult;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.web.multipart.MultipartFile;

/**
 * A capture: the scanned form, recording or other artifact an encounter was captured in. Create one
 * with a multipart POST of the artifact as <code>file</code> and a <code>json</code> parameter with
 * the encounter's uuid, the kind and optionally the media type and consent; the artifact itself is
 * read from <code>capture/{uuid}/artifact</code>. A capture is never changed, except to record
 * withdrawn consent by POSTing <code>{"consentStatus": "WITHDRAWN"}</code>; DELETE voids it.
 *
 * @since 3.0
 */
@Resource(name = RestConstants.VERSION_1 + "/capture", supportedClass = Capture.class, supportedOpenmrsVersions = {
        "3.0.* - 9.*" })
public class CaptureResource3_0 extends DelegatingCrudResource<Capture> implements Uploadable {
	
	private static final Set<String> UPLOAD_PROPERTIES = Set.of("encounter", "kind", "mimeType", "consentStatus");
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("uuid");
		description.addProperty("display");
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			description.addProperty("encounter", Representation.REF);
			description.addProperty("kind");
			description.addProperty("mimeType");
			description.addProperty("sha256");
			description.addProperty("consentStatus");
			description.addProperty("expiresAt");
			description.addProperty("artifactDeletedAt");
			description.addProperty("voided");
		} else if (!(rep instanceof RefRepresentation)) {
			return null;
		}
		description.addSelfLink();
		return description;
	}
	
	/**
	 * Takes the artifact and the capture's fields from a multipart request; existing records are taken
	 * by uuid only.
	 */
	@Override
	public Object upload(MultipartFile file, RequestContext context) throws ResponseException, IOException {
		String json = context.getParameter("json");
		if (json == null) {
			throw new IllegalRequestException("The capture's fields must be in a request parameter named 'json'");
		}
		SimpleObject fields = SimpleObject.parseJson(json);
		for (String key : fields.keySet()) {
			if (!UPLOAD_PROPERTIES.contains(key)) {
				throw new IllegalRequestException("A capture takes only " + UPLOAD_PROPERTIES + ", not " + key);
			}
		}
		if (!(fields.get("encounter") instanceof String) || fields.get("kind") == null) {
			throw new IllegalRequestException("A capture needs its encounter's uuid and its kind");
		}
		Encounter encounter = Context.getEncounterService().getEncounterByUuid((String) fields.get("encounter"));
		if (encounter == null) {
			throw new IllegalRequestException("No encounter with uuid " + fields.get("encounter"));
		}
		String mimeType = fields.get("mimeType") != null ? String.valueOf(fields.get("mimeType")) : file.getContentType();
		Capture capture = new Capture(encounter, enumValue(Capture.Kind.class, fields.get("kind")), mimeType);
		if (fields.get("consentStatus") != null) {
			capture.setConsentStatus(enumValue(Capture.ConsentStatus.class, fields.get("consentStatus")));
		}
		try (InputStream artifact = file.getInputStream()) {
			capture = service().createCapture(capture, artifact);
		}
		catch (APIException e) {
			throw new IllegalRequestException(e.getMessage());
		}
		return ConversionUtil.convertToRepresentation(capture, Representation.DEFAULT);
	}
	
	@Override
	public Capture save(Capture capture) {
		throw new ResourceDoesNotSupportOperationException("Create a capture with a multipart upload of its artifact");
	}
	
	/**
	 * The only change a capture takes is withdrawn consent.
	 */
	@Override
	public Object update(String uuid, SimpleObject propertiesToUpdate, RequestContext context) throws ResponseException {
		if (propertiesToUpdate.size() != 1 || propertiesToUpdate.get("consentStatus") == null
		        || enumValue(Capture.ConsentStatus.class,
		            propertiesToUpdate.get("consentStatus")) != Capture.ConsentStatus.WITHDRAWN) {
			throw new IllegalRequestException("A capture takes only {\"consentStatus\": \"WITHDRAWN\"}");
		}
		Capture capture = getByUniqueId(uuid);
		if (capture == null) {
			throw new ObjectNotFoundException();
		}
		try {
			capture = service().withdrawConsent(capture);
		}
		catch (APIException e) {
			throw new IllegalRequestException(e.getMessage());
		}
		return ConversionUtil.convertToRepresentation(capture, context.getRepresentation());
	}
	
	/**
	 * <code>?encounter=uuid</code> lists an encounter's captures.
	 */
	@Override
	protected PageableResult doSearch(RequestContext context) {
		String encounterUuid = context.getParameter("encounter");
		if (StringUtils.isBlank(encounterUuid)) {
			throw new IllegalRequestException("Search captures by encounter");
		}
		Encounter encounter = Context.getEncounterService().getEncounterByUuid(encounterUuid);
		if (encounter == null) {
			return new EmptySearchResult();
		}
		return new NeedsPaging<Capture>(service().getCapturesByEncounter(encounter), context);
	}
	
	@PropertyGetter("display")
	public static String getDisplay(Capture capture) {
		return capture.getKind() + " " + capture.getMimeType();
	}
	
	@Override
	public Capture getByUniqueId(String uuid) {
		return service().getCaptureByUuid(uuid);
	}
	
	@Override
	public Capture newDelegate() {
		return new Capture();
	}
	
	@Override
	protected void delete(Capture capture, String reason, RequestContext context) throws ResponseException {
		service().voidCapture(capture, reason);
	}
	
	@Override
	public void purge(Capture capture, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("A capture is voided, never purged");
	}
	
	@Override
	public String getResourceVersion() {
		return RestConstants3_0.RESOURCE_VERSION;
	}
	
	static <E extends Enum<E>> E enumValue(Class<E> type, Object value) {
		try {
			return Enum.valueOf(type, String.valueOf(value).toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw new IllegalRequestException("Unknown " + type.getSimpleName() + " " + value + "; use one of "
			        + java.util.Arrays.toString(type.getEnumConstants()));
		}
	}
	
	private static CaptureService service() {
		return Context.getCaptureService();
	}
}
