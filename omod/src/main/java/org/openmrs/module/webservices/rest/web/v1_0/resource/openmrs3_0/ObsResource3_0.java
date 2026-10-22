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
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs2_7.ObsResource2_7;

/**
 * Adds the provider who made the claim an obs records. It is set when the obs is created, and the
 * server fills it in when it is not given; an edit keeps it.
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
		}
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = super.getCreatableProperties();
		description.addProperty("asserter");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		DelegatingResourceDescription description = super.getUpdatableProperties();
		description.removeProperty("asserter");
		return description;
	}
	
	@Override
	public Model getGETModel(Representation rep) {
		return ((ModelImpl) super.getGETModel(rep)).property("asserter", new RefProperty("#/definitions/ProviderGetRef"));
	}
	
	@Override
	public Model getCREATEModel(Representation rep) {
		return ((ModelImpl) super.getCREATEModel(rep)).property("asserter", new StringProperty().example("uuid"));
	}
	
	@Override
	public Model getUPDATEModel(Representation rep) {
		// the create-only properties cannot be updated
		ModelImpl model = (ModelImpl) super.getUPDATEModel(rep);
		if (model.getProperties() != null) {
			model.getProperties().remove("asserter");
		}
		return model;
	}
	
	@Override
	public String getResourceVersion() {
		return RestConstants3_0.RESOURCE_VERSION;
	}
}
