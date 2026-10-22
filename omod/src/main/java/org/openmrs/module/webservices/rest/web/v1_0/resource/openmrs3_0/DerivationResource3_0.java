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

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Capture;
import org.openmrs.Derivation;
import org.openmrs.DictionaryVersion;
import org.openmrs.api.APIException;
import org.openmrs.api.CaptureService;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * A derivation: one finished run of an agent, such as an extraction model, over a capture. POST it
 * once the run is done, with the capture's uuid, when it started and finished, and optionally the
 * agent user's and dictionary version's uuids (by default the caller and the current version) and a
 * hash of the run's parameters; then POST the obs it produced with this derivation. It is never
 * changed; DELETE voids it.
 *
 * @since 3.0
 */
@Resource(name = RestConstants.VERSION_1 + "/derivation", supportedClass = Derivation.class, supportedOpenmrsVersions = {
        "3.0.* - 9.*" })
public class DerivationResource3_0 extends DelegatingCrudResource<Derivation> {
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("uuid");
		description.addProperty("display");
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			description.addProperty("capture", Representation.REF);
			description.addProperty("agent", Representation.REF);
			description.addProperty("dictionaryVersion");
			description.addProperty("started");
			description.addProperty("finished");
			description.addProperty("paramsHash");
			description.addProperty("voided");
		} else if (!(rep instanceof RefRepresentation)) {
			return null;
		}
		description.addSelfLink();
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addRequiredProperty("capture");
		description.addRequiredProperty("started");
		description.addRequiredProperty("finished");
		description.addProperty("agent");
		description.addProperty("dictionaryVersion");
		description.addProperty("paramsHash");
		return description;
	}
	
	@PropertySetter("capture")
	public static void setCapture(Derivation derivation, Object capture) {
		derivation.setCapture(found(Context.getCaptureService().getCaptureByUuid(uuid(capture, "capture")), capture));
	}
	
	@PropertySetter("agent")
	public static void setAgent(Derivation derivation, Object agent) {
		derivation.setAgent(found(Context.getUserService().getUserByUuid(uuid(agent, "agent")), agent));
	}
	
	@PropertySetter("dictionaryVersion")
	public static void setDictionaryVersion(Derivation derivation, Object version) {
		derivation.setDictionaryVersion(
		    found(Context.getConceptService().getDictionaryVersionByUuid(uuid(version, "dictionaryVersion")), version));
	}
	
	@PropertyGetter("dictionaryVersion")
	public static SimpleObject getDictionaryVersion(Derivation derivation) {
		DictionaryVersion version = derivation.getDictionaryVersion();
		return version == null ? null : new SimpleObject().add("uuid", version.getUuid()).add("label", version.getLabel());
	}
	
	@PropertyGetter("display")
	public static String getDisplay(Derivation derivation) {
		return derivation.getAgent() + " - " + derivation.getFinished();
	}
	
	/**
	 * <code>?capture=uuid</code> lists a capture's derivations.
	 */
	@Override
	protected PageableResult doSearch(RequestContext context) {
		String captureUuid = context.getParameter("capture");
		if (StringUtils.isBlank(captureUuid)) {
			throw new IllegalRequestException("Search derivations by capture");
		}
		Capture capture = found(service().getCaptureByUuid(captureUuid), captureUuid);
		return new NeedsPaging<Derivation>(service().getDerivationsByCapture(capture), context);
	}
	
	@Override
	public Derivation save(Derivation derivation) {
		try {
			return service().createDerivation(derivation);
		}
		catch (APIException e) {
			throw new IllegalRequestException(e.getMessage());
		}
	}
	
	@Override
	public Object update(String uuid, SimpleObject propertiesToUpdate, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("A derivation cannot be changed");
	}
	
	@Override
	public Derivation getByUniqueId(String uuid) {
		return service().getDerivationByUuid(uuid);
	}
	
	@Override
	public Derivation newDelegate() {
		return new Derivation();
	}
	
	@Override
	protected void delete(Derivation derivation, String reason, RequestContext context) throws ResponseException {
		service().voidDerivation(derivation, reason);
	}
	
	@Override
	public void purge(Derivation derivation, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("A derivation is voided, never purged");
	}
	
	@Override
	public String getResourceVersion() {
		return RestConstants3_0.RESOURCE_VERSION;
	}
	
	static String uuid(Object value, String property) {
		if (!(value instanceof String)) {
			throw new IllegalRequestException(property + " must be given by uuid");
		}
		return (String) value;
	}
	
	static <T> T found(T object, Object uuid) {
		if (object == null) {
			throw new IllegalRequestException("Nothing found with uuid " + uuid);
		}
		return object;
	}
	
	private static CaptureService service() {
		return Context.getCaptureService();
	}
}
