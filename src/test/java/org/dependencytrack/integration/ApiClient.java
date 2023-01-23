/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.integration;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.dependencytrack.common.HttpClientPool;
import org.json.JSONObject;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

public class ApiClient {

    private String baseUrl;
    private String apiKey;

    public ApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public UUID createProject(String name, String version) throws IOException {
        HttpPut request = new HttpPut(baseUrl + "/api/v1/project");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-API-Key", apiKey);
        JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", name);
                jsonObject.put("version", version);
        String jsonString = jsonObject.toString();
        request.setEntity(new StringEntity(jsonString));
        CloseableHttpResponse response = HttpClientPool.getClient().execute(request);
        if (response.getStatusLine().getStatusCode() == 201) {
            String responseString = EntityUtils.toString(response.getEntity());
            JSONObject jsonObject1 = new JSONObject(responseString);
            return UUID.fromString(jsonObject1.getString("uuid"));
        }
        System.out.println("Error creating project " + name + " status: " + response.getStatusLine().getStatusCode());
        return null;
    }

    public boolean uploadBom(UUID uuid, File bom) throws IOException, IOException {
        HttpPut request = new HttpPut(baseUrl + "/api/v1/bom");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-API-Key", apiKey);
        JSONObject jsonObject = new JSONObject()
                .put("project", uuid.toString())
                .put("bom", Base64.getEncoder().encodeToString(FileUtils.readFileToByteArray(bom)));
        String jsonString = jsonObject.toString();
        request.setEntity(new StringEntity(jsonString));
        CloseableHttpResponse response = HttpClientPool.getClient().execute(request);
        return (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
    }

    public boolean uploadScan(UUID uuid, File scan) throws IOException {
        HttpPut request = new HttpPut(baseUrl + "/api/v1/scan");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-API-Key", apiKey);
        JSONObject jsonObject = new JSONObject()
                        .put("project", uuid.toString())
                        .put("scan", Base64.getEncoder().encodeToString(FileUtils.readFileToByteArray(scan)));
        String jsonString = jsonObject.toString();
        request.setEntity(new StringEntity(jsonString));
        CloseableHttpResponse response = HttpClientPool.getClient().execute(request);
        return (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK);
    }
}
