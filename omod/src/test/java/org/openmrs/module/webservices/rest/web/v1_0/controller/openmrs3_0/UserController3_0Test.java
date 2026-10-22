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

import org.apache.commons.beanutils.PropertyUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmrs.User;
import org.openmrs.UserType;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestTestConstants1_8;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceControllerTest;

public class UserController3_0Test extends MainResourceControllerTest {
	
	@Test
	public void getUser_shouldIncludeTheUserType() throws Exception {
		SimpleObject result = deserialize(handle(newGetRequest(getURI() + "/" + getUuid())));
		
		Assertions.assertEquals("HUMAN", PropertyUtils.getProperty(result, "userType"));
	}
	
	@Test
	public void createUser_shouldCreateASoftwareUserWithItsAgent() throws Exception {
		String json = "{\"username\":\"extractor-0.1\",\"password\":\"Openmr5xy\",\"userType\":\"SOFTWARE\","
		        + "\"softwareAgent\":{\"name\":\"extractor\",\"version\":\"0.1\",\"modelIdentifier\":\"local-7b\"}}";
		
		SimpleObject created = deserialize(handle(newPostRequest(getURI(), json)));
		String uuid = (String) PropertyUtils.getProperty(created, "uuid");
		SimpleObject full = deserialize(handle(newGetRequest(getURI() + "/" + uuid, new Parameter("v", "full"))));
		
		Assertions.assertEquals("SOFTWARE", PropertyUtils.getProperty(full, "userType"));
		Assertions.assertEquals("0.1", PropertyUtils.getProperty(full, "softwareAgent.version"));
		Assertions.assertEquals("local-7b", PropertyUtils.getProperty(full, "softwareAgent.modelIdentifier"));
		User user = Context.getUserService().getUserByUuid(uuid);
		Assertions.assertNull(user.getPerson());
	}
	
	@Test
	public void createUser_shouldCreateADeviceUserWithItsAgent() throws Exception {
		String json = "{\"username\":\"analyser-1\",\"password\":\"Openmr5xy\",\"userType\":\"DEVICE\","
		        + "\"deviceAgent\":{\"identifier\":\"SN-1001\"}}";
		
		SimpleObject created = deserialize(handle(newPostRequest(getURI(), json)));
		String uuid = (String) PropertyUtils.getProperty(created, "uuid");
		
		User user = Context.getUserService().getUserByUuid(uuid);
		Assertions.assertEquals(UserType.DEVICE, user.getUserType());
		Assertions.assertEquals("SN-1001", Context.getUserService().getDeviceAgent(user).getIdentifier());
	}
	
	@Test
	public void createUser_shouldRefuseASoftwareUserWithoutItsAgent() {
		String json = "{\"username\":\"extractor-0.1\",\"password\":\"Openmr5xy\",\"userType\":\"SOFTWARE\"}";
		
		Assertions.assertThrows(IllegalRequestException.class, () -> handle(newPostRequest(getURI(), json)));
	}
	
	@Test
	public void createUser_shouldRefuseAHumanUserWithoutAPerson() {
		String json = "{\"username\":\"nobody\",\"password\":\"Openmr5xy\"}";
		
		Assertions.assertThrows(IllegalRequestException.class, () -> handle(newPostRequest(getURI(), json)));
	}
	
	@Test
	public void createUser_shouldRefuseASoftwareAgentThatIsNotAnObject() {
		String json = "{\"username\":\"extractor-0.1\",\"password\":\"Openmr5xy\",\"userType\":\"SOFTWARE\","
		        + "\"softwareAgent\":\"extractor\"}";
		
		Assertions.assertThrows(ConversionException.class, () -> handle(newPostRequest(getURI(), json)));
	}
	
	@Test
	public void createUser_shouldRefuseADeviceAtAnUnknownLocation() {
		String json = "{\"username\":\"analyser-1\",\"password\":\"Openmr5xy\",\"userType\":\"DEVICE\","
		        + "\"deviceAgent\":{\"identifier\":\"SN-1001\",\"location\":\"no-such-location\"}}";
		
		Assertions.assertThrows(IllegalRequestException.class, () -> handle(newPostRequest(getURI(), json)));
	}
	
	@Test
	public void updateUser_shouldNotChangeTheUserType() {
		String json = "{\"userType\":\"SOFTWARE\"}";
		
		Assertions.assertThrows(ConversionException.class, () -> handle(newPostRequest(getURI() + "/" + getUuid(), json)));
		Assertions.assertEquals(UserType.HUMAN, Context.getUserService().getUserByUuid(getUuid()).getUserType());
	}
	
	@Override
	public String getURI() {
		return "user";
	}
	
	@Override
	public String getUuid() {
		return RestTestConstants1_8.USER_UUID;
	}
	
	@Override
	public long getAllCount() {
		return Context.getUserService().getAllUsers().size();
	}
}
