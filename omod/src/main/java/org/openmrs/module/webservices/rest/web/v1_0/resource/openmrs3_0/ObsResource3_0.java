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

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs2_7.ObsResource2_7;

/**
 * Adds the provider who made the claim an obs records, and where the obs was taken from: its
 * capture, the span within it and the derivation that produced it. All are set when the obs is
 * created, by uuid for the capture and derivation; the server fills in the asserter when it is not
 * given; an edit keeps them all.
 *
 * @since 3.0.0
 */
@Resource(name = RestConstants.VERSION_1 + "/obs", supportedClass = Obs.class, supportedOpenmrsVersions = { "3.0.* - 9.*" })
public class ObsResource3_0 extends ObsResource2_7 {
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = super.getRepresentationDescription(rep);
		if (description != null) {
			description.addProperty("asserter", Representation.REF);
			description.addProperty("capture", Representation.REF);
			description.addProperty("span");
			description.addProperty("derivation", Representation.REF);
		}
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = super.getCreatableProperties();
		description.addProperty("asserter");
		description.addProperty("capture");
		description.addProperty("span");
		description.addProperty("derivation");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		DelegatingResourceDescription description = super.getUpdatableProperties();
		description.removeProperty("asserter");
		description.removeProperty("capture");
		description.removeProperty("span");
		description.removeProperty("derivation");
		return description;
	}
	
	@Override
	public Model getGETModel(Representation rep) {
		return ((ModelImpl) super.getGETModel(rep)).property("asserter", new RefProperty("#/definitions/ProviderGetRef"))
		        .property("capture", new RefProperty("#/definitions/CaptureGetRef")).property("span", new StringProperty())
		        .property("derivation", new RefProperty("#/definitions/DerivationGetRef"));
	}
	
	@Override
	public Model getCREATEModel(Representation rep) {
		return ((ModelImpl) super.getCREATEModel(rep)).property("asserter", new StringProperty().example("uuid"))
		        .property("capture", new StringProperty().example("uuid"))
		        .property("span", new StringProperty().example("xywh=412,880,60,24;field=chest_indrawing"))
		        .property("derivation", new StringProperty().example("uuid"));
	}
	
	@Override
	public Model getUPDATEModel(Representation rep) {
		// the create-only properties cannot be updated
		ModelImpl model = (ModelImpl) super.getUPDATEModel(rep);
		if (model.getProperties() != null) {
			model.getProperties().remove("asserter");
			model.getProperties().remove("capture");
			model.getProperties().remove("span");
			model.getProperties().remove("derivation");
		}
		return model;
	}
	
	@PropertySetter("capture")
	public static void setCapture(Obs obs, Object capture) {
		obs.setCapture(DerivationResource3_0.found(
		    Context.getCaptureService().getCaptureByUuid(DerivationResource3_0.uuid(capture, "capture")), capture));
	}
	
	@PropertySetter("derivation")
	public static void setDerivation(Obs obs, Object derivation) {
		obs.setDerivation(DerivationResource3_0.found(
		    Context.getCaptureService().getDerivationByUuid(DerivationResource3_0.uuid(derivation, "derivation")),
		    derivation));
	}
	
	@Override
	public String getResourceVersion() {
		return RestConstants3_0.RESOURCE_VERSION;
	}
}
