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

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs2_8.ProviderResource2_8;

/**
 * Adds whether a provider is a person (BIOLOGIC) or software or a device (DIGITAL), and the software or
 * device user behind a digital provider. Both are set when the provider is created and never change.
 * Only a biologic provider needs a person.
 *
 * @since 3.0.0
 */
@Resource(name = RestConstants.VERSION_1 + "/provider", supportedClass = Provider.class, supportedOpenmrsVersions = {
        "3.0.* - 9.*" })
public class ProviderResource3_0 extends ProviderResource2_8 {
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = super.getRepresentationDescription(rep);
		if (description != null && (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation)) {
			description.addProperty("providerType");
			description.addProperty("user", Representation.REF);
		}
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = super.getCreatableProperties();
		// a digital provider has no person; the API still requires one for a biologic provider
		description.removeProperty("person");
		description.addProperty("person");
		description.addProperty("providerType");
		description.addProperty("user");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		DelegatingResourceDescription description = super.getUpdatableProperties();
		description.removeProperty("providerType");
		description.removeProperty("user");
		return description;
	}
	
	@Override
	public Model getGETModel(Representation rep) {
		ModelImpl model = (ModelImpl) super.getGETModel(rep);
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			model.property("providerType", providerTypeProperty())
			        .property("user", new RefProperty("#/definitions/UserGetRef"));
		}
		return model;
	}
	
	@Override
	public Model getCREATEModel(Representation rep) {
		ModelImpl model = ((ModelImpl) super.getCREATEModel(rep)).property("providerType", providerTypeProperty())
		        .property("user", new StringProperty().example("uuid"));
		List<String> required = new ArrayList<>(model.getRequired());
		required.remove("person");
		model.setRequired(required);
		return model;
	}
	
	/**
	 * Takes only the uuid of the user behind a digital provider. Anything more, such as a nested user
	 * with roles, would be applied to that user without the checks UserService makes.
	 *
	 * @param provider the provider being created
	 * @param value a user uuid, or an object whose only field is uuid
	 */
	@PropertySetter("user")
	public static void setUser(Provider provider, Object value) {
		Object uuid = value;
		if (value instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) value;
			if (map.size() != 1 || !map.containsKey("uuid")) {
				throw new ConversionException("user takes only the uuid of a user");
			}
			uuid = map.get("uuid");
		}
		if (!(uuid instanceof String)) {
			throw new ConversionException("user takes only the uuid of a user");
		}
		User user = Context.getUserService().getUserByUuid((String) uuid);
		if (user == null) {
			throw new ConversionException("There is no user " + uuid);
		}
		provider.setUser(user);
	}
	
	@Override
	public Model getUPDATEModel(Representation rep) {
		// the create-only properties cannot be updated
		ModelImpl model = (ModelImpl) super.getUPDATEModel(rep);
		if (model.getProperties() != null) {
			model.getProperties().remove("providerType");
			model.getProperties().remove("user");
		}
		return model;
	}
	
	@Override
	public String getResourceVersion() {
		return RestConstants3_0.RESOURCE_VERSION;
	}
	
	private static StringProperty providerTypeProperty() {
		return new StringProperty()._enum("BIOLOGIC")._enum("DIGITAL");
	}
}
