#!/usr/bin/env groovy

/*
Steps: 
We make a call to GET /configuration-items(of config-management API)  to check if CMDB record already present for this API.
  if present=>update Configuration Item record by calling PUT /configuration-items
  else =>create new Configuration Item record for the API by calling POST /configuration-items(of config-management API),content of SNOWfile.json will be passed as request body.
*/

import groovy.json.JsonSlurperClassic
import java.net.URLEncoder

@NonCPS
def call(clientID, clientSecret,baseURL, snowfileContent) {
	try {
		def apiStatus = "SUCCESS"
		def jsonSlurper = new JsonSlurperClassic()
		cfg = jsonSlurper.parseText(snowfileContent)
		def encodedName = URLEncoder.encode(cfg['name'], "UTF-8")
		def getURL = "${baseURL}?name=${encodedName}"
		def putURL = "${baseURL}/${encodedName}"
		echo "${getURL}"
		def get = new URL(getURL).openConnection() as HttpURLConnection;
		get.setRequestProperty("Content-Type", "application/json")
		get.setRequestProperty("client-id", "${clientID}")
		get.setRequestProperty("client-secret", "${clientSecret}")
		get.connect()
		def getRC = get.responseCode;
		echo "${getRC}"
		if (getRC.equals(200)) {
			//echo "GET /configuration-items Response:  ${new JsonSlurperClassic().parseText(get.inputStream.getText('UTF-8'))}" 
			echo  "CI already present, updating it...."
			// PUT /configuration-items , update CI entry for this API 
			echo "Calling: PUT ${putURL}"
			def put = new URL(putURL).openConnection() as HttpURLConnection;
			put.setRequestMethod("PUT")
			put.setDoOutput(true)
			put.setRequestProperty("Content-Type", "application/json")
			put.setRequestProperty("client-id", "${clientID}")
			put.setRequestProperty("client-secret", "${clientSecret}")  
			put.outputStream.write(snowfileContent.getBytes("UTF-8"));
			put.connect()
			def putRC = put.responseCode //without this line PUT call will not trigger
			if (putRC.equals(201)) { 
				//echo "PUT Response: ${put.inputStream.getText('UTF-8')}" 
				echo  "CI record updated" 
			}
			else if (putRC.equals(404)){
				//echo "PUT Response:  ${put.errorStream.getText('UTF-8')}" 
				echo "Unable to update CI record for ${cfg['name']} due to unknown dependency" 
				apiStatus = "FAILURE"
			}          
			else{
				//echo "PUT Response: ${put.errorStream.getText('UTF-8')}" 
				error  "Unable to update CI record for ${cfg['name']} due to ${put.errorStream.getText('UTF-8')}" 
			}   
			return apiStatus
		}
		else if (getRC.equals(404)){
			echo  "CI not found ,Creating a new CI record"
			// POST /configuration-items , creating new CI entry for this API 
			echo "Calling: POST ${baseURL}"
			def post = new URL(baseURL).openConnection() as HttpURLConnection;
			post.setRequestMethod("POST")
			post.setDoOutput(true)
			post.setRequestProperty("Content-Type", "application/json")
			post.setRequestProperty("client-id", "${clientID}")
			post.setRequestProperty("client-secret", "${clientSecret}")  
			post.outputStream.write(snowfileContent.getBytes("UTF-8"));
			post.connect()
			def postRC = post.responseCode //without this line POST call will not trigger
			if (postRC.equals(201)){
				echo  "New CI record created"           
			}
			else if (postRC.equals(404)){
				//echo "POST Response:  ${post.errorStream.getText('UTF-8')}" 
				echo "CI created, unable to update CI record for ${cfg['name']} due to unknown dependency, please check response" 
				apiStatus = "FAILURE"
			}
			else{
				//echo "POST Response:  ${post.errorStream.getText('UTF-8')}" 
				error  "Unable to create CI record for ${cfg['name']}" 
			}
			return apiStatus
		}
		else {
			//echo "GET Response:  ${get.errorStream.getText('UTF-8')}" 
			error  "Unable to create CI record for ${cfg['name']} due to ${get.errorStream.getText('UTF-8')}"
		}
	} catch (err) {        
		echo " Error Occured: ${err}. Please check the issue"
		return "ERROR"
	}
}