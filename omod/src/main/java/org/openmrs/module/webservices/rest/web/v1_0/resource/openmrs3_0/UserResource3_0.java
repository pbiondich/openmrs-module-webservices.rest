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
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.StringProperty;
import org.openmrs.DeviceAgent;
import org.openmrs.Location;
import org.openmrs.SoftwareAgent;
import org.openmrs.User;
import org.openmrs.UserType;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs2_2.UserResource2_2;
import org.openmrs.module.webservices.rest.web.v1_0.wrapper.openmrs1_8.UserAndPassword1_8;

/**
 * Adds the kind of principal a user is (HUMAN, DEVICE or SOFTWARE) and, for software and device users,
 * what describes them. A user's type is set when it is created and never changes. A software user is
 * created with its softwareAgent (name, version and optionally modelIdentifier and configHash) and a
 * device user with its deviceAgent (identifier and optionally the uuid of its location), so neither
 * exists without one. Only a human user needs a person.
 *
 * @since 3.0.0
 */
@Resource(name = RestConstants.VERSION_1 + "/user", supportedClass = UserAndPassword1_8.class, supportedOpenmrsVersions = {
        "3.0.* - 9.*" })
public class UserResource3_0 extends UserResource2_2 {
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		DelegatingResourceDescription description = super.getRepresentationDescription(rep);
		if (description != null && (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation)) {
			description.addProperty("userType");
		}
		if (description != null && rep instanceof FullRepresentation) {
			description.addProperty("softwareAgent");
			description.addProperty("deviceAgent");
		}
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = super.getCreatableProperties();
		// software and device users have no person; save() still requires one for a human user
		description.removeProperty("person");
		description.addProperty("person");
		description.addProperty("userType");
		description.addProperty("softwareAgent");
		description.addProperty("deviceAgent");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		DelegatingResourceDescription description = super.getUpdatableProperties();
		description.removeProperty("userType");
		description.removeProperty("softwareAgent");
		description.removeProperty("deviceAgent");
		return description;
	}
	
	@Override
	public Model getGETModel(Representation rep) {
		ModelImpl model = (ModelImpl) super.getGETModel(rep);
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			model.property("userType", userTypeProperty());
		}
		if (rep instanceof FullRepresentation) {
			model.property("softwareAgent", new ObjectProperty()).property("deviceAgent", new ObjectProperty());
		}
		return model;
	}
	
	@Override
	public Model getCREATEModel(Representation rep) {
		ModelImpl model = ((ModelImpl) super.getCREATEModel(rep)).property("userType", userTypeProperty())
		        .property("softwareAgent", new ObjectProperty()).property("deviceAgent", new ObjectProperty());
		List<String> required = new ArrayList<>(model.getRequired());
		required.remove("person");
		model.setRequired(required);
		return model;
	}
	
	@Override
	public UserAndPassword1_8 newDelegate() {
		return new NewUser();
	}
	
	@Override
	public void setProperty(Object instance, String propertyName, Object value) throws ConversionException {
		if (instance instanceof NewUser && ("softwareAgent".equals(propertyName) || "deviceAgent".equals(propertyName))) {
			if (value != null && !(value instanceof Map)) {
				throw new ConversionException(propertyName + " must be an object");
			}
			if ("softwareAgent".equals(propertyName)) {
				((NewUser) instance).softwareAgent = (Map<?, ?>) value;
			} else {
				((NewUser) instance).deviceAgent = (Map<?, ?>) value;
			}
		} else {
			super.setProperty(instance, propertyName, value);
		}
	}
	
	@Override
	public UserAndPassword1_8 save(UserAndPassword1_8 delegate) {
		User user = delegate.getUser();
		Map<?, ?> software = delegate instanceof NewUser ? ((NewUser) delegate).softwareAgent : null;
		Map<?, ?> device = delegate instanceof NewUser ? ((NewUser) delegate).deviceAgent : null;
		if (user.getUserId() != null || user.getUserType() == UserType.HUMAN) {
			if (software != null || device != null) {
				throw new IllegalRequestException("Only a new software or device user has a softwareAgent or deviceAgent");
			}
			if (user.getUserId() == null && user.getPerson() == null) {
				throw new IllegalRequestException("A human user needs a person");
			}
			return super.save(delegate);
		}
		
		try {
			if (user.getUserType() == UserType.SOFTWARE) {
				if (software == null || device != null) {
					throw new IllegalRequestException("A software user is created with its softwareAgent: name and version");
				}
				SoftwareAgent agent = new SoftwareAgent(user, text(software, "name"), text(software, "version"));
				agent.setModelIdentifier(text(software, "modelIdentifier"));
				agent.setConfigHash(text(software, "configHash"));
				Context.getUserService().createSoftwareAgent(agent, delegate.getPassword());
			} else {
				if (device == null || software != null) {
					throw new IllegalRequestException("A device user is created with its deviceAgent: identifier");
				}
				DeviceAgent agent = new DeviceAgent(user, text(device, "identifier"));
				String locationUuid = text(device, "location");
				if (locationUuid != null) {
					Location location = Context.getLocationService().getLocationByUuid(locationUuid);
					if (location == null) {
						throw new IllegalRequestException("There is no location " + locationUuid);
					}
					agent.setLocation(location);
				}
				Context.getUserService().createDeviceAgent(agent, delegate.getPassword());
			}
		}
		catch (APIException e) {
			throw new IllegalRequestException(e.getMessage(), e);
		}
		return new UserAndPassword1_8(user);
	}
	
	/**
	 * @param user a user
	 * @return the name, version, model identifier and config hash of a software user, or null
	 */
	@PropertyGetter("softwareAgent")
	public SimpleObject getSoftwareAgent(UserAndPassword1_8 user) {
		if (user.getUser().getUserId() == null || user.getUser().getUserType() != UserType.SOFTWARE) {
			return null;
		}
		SoftwareAgent agent = Context.getUserService().getSoftwareAgent(user.getUser());
		if (agent == null) {
			return null;
		}
		return new SimpleObject().add("name", agent.getName()).add("version", agent.getVersion())
		        .add("modelIdentifier", agent.getModelIdentifier()).add("configHash", agent.getConfigHash());
	}
	
	/**
	 * @param user a user
	 * @return the identifier and current location of a device user, or null
	 */
	@PropertyGetter("deviceAgent")
	public SimpleObject getDeviceAgent(UserAndPassword1_8 user) {
		if (user.getUser().getUserId() == null || user.getUser().getUserType() != UserType.DEVICE) {
			return null;
		}
		DeviceAgent agent = Context.getUserService().getDeviceAgent(user.getUser());
		if (agent == null) {
			return null;
		}
		return new SimpleObject().add("identifier", agent.getIdentifier()).add("location",
		    ConversionUtil.convertToRepresentation(agent.getLocation(), Representation.REF));
	}
	
	@Override
	public Model getUPDATEModel(Representation rep) {
		// the create-only properties cannot be updated
		ModelImpl model = (ModelImpl) super.getUPDATEModel(rep);
		if (model.getProperties() != null) {
			model.getProperties().remove("userType");
			model.getProperties().remove("softwareAgent");
			model.getProperties().remove("deviceAgent");
		}
		return model;
	}
	
	@Override
	public String getResourceVersion() {
		return RestConstants3_0.RESOURCE_VERSION;
	}
	
	private static StringProperty userTypeProperty() {
		return new StringProperty()._enum("HUMAN")._enum("DEVICE")._enum("SOFTWARE");
	}
	
	private static String text(Map<?, ?> values, String key) {
		Object value = values.get(key);
		if (value instanceof Map || value instanceof Iterable) {
			throw new IllegalRequestException(key + " must be text");
		}
		return value == null ? null : value.toString();
	}
	
	/**
	 * A user being created, holding the agent that describes a software or device user until it is
	 * saved.
	 */
	static class NewUser extends UserAndPassword1_8 {
		
		private static final long serialVersionUID = 1L;
		
		private Map<?, ?> softwareAgent;
		
		private Map<?, ?> deviceAgent;
	}
}
