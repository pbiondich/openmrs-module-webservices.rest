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

import org.openmrs.ObsFinalizationPolicy;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.MetadataDelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * The policies that say whether the obs a kind of agent asserts are final or wait for attestation.
 *
 * @since 3.0
 */
@Resource(name = RestConstants.VERSION_1 + "/finalizationpolicy", supportedClass = ObsFinalizationPolicy.class, supportedOpenmrsVersions = {
        "3.0.* - 9.*" })
public class FinalizationPolicyResource3_0 extends MetadataDelegatingCrudResource<ObsFinalizationPolicy> {
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
			DelegatingResourceDescription description = new DelegatingResourceDescription();
			description.addProperty("uuid");
			description.addProperty("display", findMethod("getDisplayString"));
			description.addProperty("name");
			description.addProperty("description");
			description.addProperty("agentType");
			description.addProperty("conceptClass", Representation.REF);
			description.addProperty("action");
			description.addProperty("retired");
			if (rep instanceof FullRepresentation) {
				description.addProperty("auditInfo", findMethod("getAuditInfo"));
			}
			description.addSelfLink();
			return description;
		}
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addRequiredProperty("name");
		description.addProperty("description");
		description.addRequiredProperty("agentType");
		description.addProperty("conceptClass");
		description.addRequiredProperty("action");
		return description;
	}
	
	@Override
	public ObsFinalizationPolicy getByUniqueId(String uuid) {
		return Context.getObsService().getObsFinalizationPolicyByUuid(uuid);
	}
	
	@Override
	public ObsFinalizationPolicy newDelegate() {
		return new ObsFinalizationPolicy();
	}
	
	@Override
	public ObsFinalizationPolicy save(ObsFinalizationPolicy policy) {
		return Context.getObsService().saveObsFinalizationPolicy(policy);
	}
	
	@Override
	public void purge(ObsFinalizationPolicy policy, RequestContext context) throws ResponseException {
		// a policy that applied to obs is kept, retired, so what applied stays known
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	protected NeedsPaging<ObsFinalizationPolicy> doGetAll(RequestContext context) throws ResponseException {
		return new NeedsPaging<ObsFinalizationPolicy>(
		        Context.getObsService().getAllObsFinalizationPolicies(context.getIncludeAll()), context);
	}
	
	@Override
	public String getResourceVersion() {
		return RestConstants3_0.RESOURCE_VERSION;
	}
}
