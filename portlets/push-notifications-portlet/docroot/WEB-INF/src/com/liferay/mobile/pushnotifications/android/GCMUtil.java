/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.mobile.pushnotifications.android;

import com.google.android.gcm.server.*;

import com.liferay.mobile.pushnotifications.model.Device;
import com.liferay.mobile.pushnotifications.service.DeviceLocalServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.Validator;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Silvio Santos
 */
public class GCMUtil {

	public static void sendNotification(
			long userId, String collapseKey, String data, int timeToLive,
			boolean delayWhileIdle)
		throws IOException, PortalException, SystemException {

		List<Device> devices = DeviceLocalServiceUtil.findByUser(userId);

		if (devices.isEmpty()) {
			return;
		}

		List<String> deviceIds = getDeviceIds(devices);

		Message message = buildMessage(
			collapseKey, data, timeToLive, delayWhileIdle);

		Sender sender = new Sender(getApiKey());

		MulticastResult multicastResult = sender.send(message, deviceIds, 5);

		handleResponse(devices, multicastResult);
	}

	protected static Message buildMessage(
		String collapseKey, String data, int timeToLive,
		boolean delayWhileIdle) {

		Message.Builder builder = new Message.Builder();

		if (data != null) {
			builder.addData("data", data);
		}

		if (collapseKey != null) {
			builder.collapseKey(collapseKey);
		}

		if ((timeToLive >= 0) || (timeToLive <= MAX_TIME_TO_LIVE)) {
			builder.timeToLive(timeToLive);
		}

		builder.delayWhileIdle(delayWhileIdle);

		return builder.build();
	}

	protected static String getApiKey() {
		return com.liferay.util.portlet.PortletProps.get(API_KEY);
	}

	protected static List<String> getDeviceIds(List<Device> devices) {
		List<String> deviceIds = new ArrayList<String>();

		for (Device device : devices) {
			deviceIds.add(device.getDeviceId());
		}

		return deviceIds;
	}

	protected static void handleError(Device device, String error)
		throws SystemException {

		if (error.equals(Constants.ERROR_NOT_REGISTERED) ||
			error.equals(Constants.ERROR_INVALID_REGISTRATION)) {

			DeviceLocalServiceUtil.deleteDevice(device);
		}
	}

	protected static void handleResponse(
		List<Device> devices, MulticastResult multicastResult) {

		if ((multicastResult.getCanonicalIds() == 0) &&
			(multicastResult.getFailure() == 0)) {

			return;
		}

		List<Result> results = multicastResult.getResults();

		int size = results.size();

		for (int i = 0; i < size; i++) {
			Result result = results.get(i);

			String messageId = result.getMessageId();
			String canonicalId = result.getCanonicalRegistrationId();

			Device device = devices.get(i);

			try {
				if (Validator.isNotNull(messageId) &&
					Validator.isNotNull(canonicalId)) {

					DeviceLocalServiceUtil.deleteDevice(device);

					device.setDeviceId(canonicalId);

					DeviceLocalServiceUtil.addDevice(device);

					continue;
				}

				String error = result.getErrorCodeName();

				if (Validator.isNotNull(error)) {
					handleError(device, error);
				}
			}
			catch (SystemException e) {
				e.printStackTrace();
			}
		}
	}

	protected static final String API_KEY = "gcm.api.key";

	protected static final int MAX_TIME_TO_LIVE = 2419200;

}