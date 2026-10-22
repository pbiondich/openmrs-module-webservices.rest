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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openmrs.Obs;
import org.openmrs.ObsAttestation;
import org.openmrs.ObsAttestationItem;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs1_8.ObsResource1_8;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * A person's attestation of preliminary obs. It is created once and never changed: POST it with the
 * encounter, the attester (the caller's own provider) and an item per obs, each with an outcome of
 * ACCEPTED, REJECTED, AMENDED or LEFT_PRELIMINARY; an AMENDED item carries its correction as a new
 * obs in supersedingObs.
 *
 * @since 3.0
 */
@Resource(name = RestConstants.VERSION_1 + "/attestation", supportedClass = ObsAttestation.class, supportedOpenmrsVersions = {
        "3.0.* - 9.*" })
public class AttestationResource3_0 extends DelegatingCrudResource<ObsAttestation> {
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		if (rep instanceof RefRepresentation) {
			DelegatingResourceDescription description = new DelegatingResourceDescription();
			description.addProperty("uuid");
			description.addProperty("display");
			description.addSelfLink();
			return description;
		}
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			DelegatingResourceDescription description = new DelegatingResourceDescription();
			description.addProperty("uuid");
			description.addProperty("display");
			description.addProperty("encounter", Representation.REF);
			description.addProperty("attester", Representation.REF);
			description.addProperty("attestedAt");
			description.addProperty("items");
			description.addSelfLink();
			return description;
		}
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("encounter");
		description.addRequiredProperty("attester");
		description.addRequiredProperty("items");
		return description;
	}
	
	/**
	 * Each item is the obs's uuid, its outcome and, for an amended obs, the correction: a value, and
	 * optionally a comment, for the obs's own concept. Only uuids are taken for existing records, so
	 * an attestation cannot change anything but the obs it covers.
	 */
	@PropertySetter("items")
	public static void setItems(ObsAttestation attestation, List<Map<String, Object>> items) throws Exception {
		List<ObsAttestationItem> converted = new ArrayList<ObsAttestationItem>();
		for (Map<String, Object> item : items) {
			Obs obs = Context.getObsService().getObsByUuid(uuid(item.get("obs"), "obs"));
			if (obs == null || item.get("outcome") == null) {
				throw new IllegalRequestException("Each item needs an existing obs and an outcome");
			}
			ObsAttestationItem attestationItem = new ObsAttestationItem(obs, outcome(item.get("outcome")));
			if (item.get("supersedingObs") != null) {
				attestationItem.setSupersedingObs(correction(obs, item.get("supersedingObs")));
			}
			converted.add(attestationItem);
		}
		attestation.setItems(converted);
	}
	
	@PropertySetter("encounter")
	public static void setEncounter(ObsAttestation attestation, Object encounter) {
		attestation.setEncounter(Context.getEncounterService().getEncounterByUuid(uuid(encounter, "encounter")));
	}
	
	@PropertySetter("attester")
	public static void setAttester(ObsAttestation attestation, Object attester) {
		attestation.setAttester(Context.getProviderService().getProviderByUuid(uuid(attester, "attester")));
	}
	
	private static String uuid(Object value, String property) {
		if (!(value instanceof String)) {
			throw new IllegalRequestException(property + " must be given by uuid");
		}
		return (String) value;
	}
	
	private static ObsAttestationItem.Outcome outcome(Object value) {
		try {
			return ObsAttestationItem.Outcome.valueOf(String.valueOf(value).toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalRequestException("Unknown outcome " + value + "; use one of "
			        + java.util.Arrays.toString(ObsAttestationItem.Outcome.values()));
		}
	}
	
	/**
	 * The correction is a new obs for the original's concept, carrying only a value and a comment;
	 * the service puts it in the original's place for patient, encounter and group.
	 */
	private static Obs correction(Obs original, Object value) throws Exception {
		if (!(value instanceof Map)) {
			throw new IllegalRequestException("supersedingObs must be an object with a value");
		}
		Map<?, ?> fields = (Map<?, ?>) value;
		for (Object key : fields.keySet()) {
			if (!"value".equals(key) && !"comment".equals(key)) {
				throw new IllegalRequestException("supersedingObs may carry only a value and a comment, not " + key);
			}
		}
		Obs correction = new Obs();
		correction.setConcept(original.getConcept());
		ObsResource1_8.setValue(correction, fields.get("value"));
		if (fields.get("comment") != null) {
			correction.setComment(String.valueOf(fields.get("comment")));
		}
		return correction;
	}
	
	@PropertyGetter("items")
	public static List<SimpleObject> getItems(ObsAttestation attestation) {
		List<SimpleObject> items = new ArrayList<SimpleObject>();
		for (ObsAttestationItem item : attestation.getItems()) {
			SimpleObject converted = new SimpleObject();
			converted.add("uuid", item.getUuid());
			converted.add("obs", ConversionUtil.convertToRepresentation(item.getObs(), Representation.REF));
			converted.add("outcome", item.getOutcome().name());
			if (item.getSupersedingObs() != null) {
				converted.add("supersedingObs",
				    ConversionUtil.convertToRepresentation(item.getSupersedingObs(), Representation.REF));
			}
			items.add(converted);
		}
		return items;
	}
	
	@Override
	public ObsAttestation getByUniqueId(String uuid) {
		return Context.getAttestationService().getObsAttestationByUuid(uuid);
	}
	
	@Override
	public ObsAttestation newDelegate() {
		return new ObsAttestation();
	}
	
	@Override
	public ObsAttestation save(ObsAttestation attestation) {
		try {
			return Context.getAttestationService().attest(attestation.getEncounter(), attestation.getAttester(),
			    attestation.getItems());
		}
		catch (APIException e) {
			throw new IllegalRequestException(e.getMessage());
		}
	}
	
	@Override
	public Object update(String uuid, SimpleObject propertiesToUpdate, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("An attestation cannot be changed");
	}
	
	@PropertyGetter("display")
	public static String getDisplay(ObsAttestation attestation) {
		return attestation.getAttester() + " - " + attestation.getAttestedAt();
	}
	
	@Override
	protected void delete(ObsAttestation attestation, String reason, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	public void purge(ObsAttestation attestation, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	public String getResourceVersion() {
		return RestConstants3_0.RESOURCE_VERSION;
	}
}
