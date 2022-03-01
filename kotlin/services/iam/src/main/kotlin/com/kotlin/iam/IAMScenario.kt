//snippet-sourcedescription:[IAMScenario.kt demonstrates how to perform various AWS Identity and Access Management (IAM) operations.]
//snippet-keyword:[AWS SDK for Kotlin]
//snippet-keyword:[Code Sample]
//snippet-service:[IAM]
//snippet-sourcetype:[full-example]
//snippet-sourcedate:[01/20/2022]
//snippet-sourceauthor:[scmacdon-aws]

/*
   Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
   SPDX-License-Identifier: Apache-2.0
*/

package com.kotlin.iam

// snippet-start:[iam.kotlin.scenario.import]
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.iam.IamClient
import aws.sdk.kotlin.services.iam.model.*
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.ListObjectsRequest
import aws.sdk.kotlin.services.sts.StsClient
import aws.sdk.kotlin.services.sts.model.AssumeRoleRequest
import kotlinx.coroutines.delay
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.FileReader
import kotlin.system.exitProcess
// snippet-end:[iam.kotlin.scenario.import]

/**
To run this Kotlin code example, ensure that you have setup your development environment,
including your credentials.

For information, see this documentation topic:
https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/setup.html

This example performs these operations:

1. Creates a user that has no permissions.
2. Creates a role and policy that grants Amazon S3 permissions.
3. Grants the user permissions.
4. Gets temporary credentials by assuming the role.
5. Creates an Amazon S3 Service client object with the temporary credentials and list objects in an Amazon S3 bucket.
6. Gets various IAM resources.
7. Deletes the resources.
 */


// snippet-start:[iam.kotlin.scenario.main]
    suspend fun main(args: Array<String>) {

    val usage = """
    Usage:
        <username> <policyName> <roleName> <roleSessionName> <fileLocation> <bucketName> 

    Where:
        username - the name of the IAM user to create. 
        policyName - the name of the policy to create. 
        roleName - the name of the role to create. 
        roleSessionName - the name of the session required for the assumeRole operation. 
        fileLocation - the file location to the JSON required to create the role (see Readme). 
        bucketName - the name of the Amazon S3 bucket from which objects are read. 
    """

       if (args.size != 6) {
            println(usage)
            exitProcess(1)
        }

       val userName = args[0]
       val policyName = args[1]
       val roleName = args[2]
       val roleSessionName = args[3]
       val fileLocation = args[4]
       val bucketName = args[5]

       createUser(userName)
       println("$userName was successfully created.")

       val polArn =createPolicy(policyName)
       println("The policy $polArn was successfully created.")

      val roleArn = createRole( roleName, fileLocation)
      println("$roleArn was successfully created.")
      attachRolePolicy(roleName, polArn)

      println("*** Wait for 1 MIN so the resource is available.")
      delay(60000)
      assumeGivenRole(roleArn, roleSessionName, bucketName)

     println("*** Get the AWS resources")
     getPolicy(polArn)
     getRole(roleName)
     getSAMLProviders()
     getGroups()
     getPolicies()
     getAttachedRolePolicies(roleName)
     getRoles()

    println("*** Getting ready to delete the AWS resources.")
    deleteRole(roleName, polArn)
    deleteUser(userName)
    println("This IAM Scenario has successfully completed.")
}

suspend fun createUser(usernameVal: String?): String? {

    val request = CreateUserRequest {
        userName = usernameVal
    }

    IamClient { region = "AWS_GLOBAL" }.use { iamClient ->
        val response = iamClient.createUser(request)
        return response.user?.userName
    }
}

suspend fun createPolicy(policyNameVal: String?): String {

    val policyDocumentValue: String = "{" +
            "  \"Version\": \"2012-10-17\"," +
            "  \"Statement\": [" +
            "    {" +
            "        \"Effect\": \"Allow\"," +
            "        \"Action\": [" +
            "            \"s3:*\"" +
            "       ]," +
            "       \"Resource\": \"*\"" +
            "    }" +
            "   ]" +
            "}"

    val request = CreatePolicyRequest {
        policyName = policyNameVal
        policyDocument = policyDocumentValue
    }

    IamClient { region = "AWS_GLOBAL" }.use { iamClient ->
        val response = iamClient.createPolicy(request)
        return response.policy?.arn.toString()
    }
}

suspend fun createRole(rolenameVal: String?, fileLocation: String?): String? {

    val jsonObject = fileLocation?.let { readJsonSimpleDemo(it) } as JSONObject

    val request = CreateRoleRequest {
        roleName = rolenameVal
        assumeRolePolicyDocument = jsonObject.toJSONString()
        description = "Created using the AWS SDK for Kotlin"
    }

    IamClient { region = "AWS_GLOBAL" }.use { iamClient ->
        val response = iamClient.createRole(request)
        return response.role?.arn
    }
}

suspend fun attachRolePolicy(roleNameVal: String, policyArnVal: String) {

    val request = ListAttachedRolePoliciesRequest {
        roleName = roleNameVal
    }

    IamClient { region = "AWS_GLOBAL" }.use { iamClient ->
        val response = iamClient.listAttachedRolePolicies(request)
        val attachedPolicies = response.attachedPolicies

        // Ensure that the policy is not attached to this role.
        val checkStatus: Int
        if (attachedPolicies != null) {
            checkStatus = checkMyList(attachedPolicies, policyArnVal)
            if (checkStatus == -1)
                return
        }

        val policyRequest = AttachRolePolicyRequest {
            roleName = roleNameVal
            policyArn = policyArnVal
        }
        iamClient.attachRolePolicy(policyRequest)
        println("Successfully attached policy $policyArnVal to role $roleNameVal")
    }
}

fun checkMyList(attachedPolicies:List<AttachedPolicy>, policyArnVal:String) :Int {

    for (policy in attachedPolicies) {
        val polArn = policy.policyArn.toString()

        if (polArn.compareTo(policyArnVal) == 0) {
            println("The policy is already attached to this role.")
            return -1
        }
    }
    return 0
}

suspend fun assumeGivenRole(roleArnVal: String?, roleSessionNameVal: String?, bucketName: String) {

    val stsClient = StsClient {
        region = "us-east-1"
    }

    val roleRequest = AssumeRoleRequest {
            roleArn = roleArnVal
            roleSessionName = roleSessionNameVal
    }

    val roleResponse= stsClient.assumeRole(roleRequest)
    val myCreds = roleResponse.credentials
    val key = myCreds?.accessKeyId
    val secKey = myCreds?.secretAccessKey
    val secToken = myCreds?.sessionToken

    val staticCredentials = StaticCredentialsProvider {
         accessKeyId = key
         secretAccessKey = secKey
         sessionToken = secToken
    }


    // List all objects in an Amazon S3 bucket using the temp creds.
    val s3 = S3Client {
         credentialsProvider = staticCredentials
         region = "us-east-1"
     }

   println("Created a S3Client using temp credentials.")
   println("Listing objects in $bucketName")

   val listObjects = ListObjectsRequest {
        bucket = bucketName
   }

   val response = s3.listObjects(listObjects)
   response.contents?.forEach { myObject ->
         println("The name of the key is ${myObject.key}")
         println("The owner is ${myObject.owner}")
   }
}

suspend fun deleteRole(roleNameVal: String, polArn: String) {

    val iam = IamClient { region = "AWS_GLOBAL"}

        // First the policy needs to be detached.
        val rolePolicyRequest = DetachRolePolicyRequest {
            policyArn = polArn
            roleName = roleNameVal
        }

        iam.detachRolePolicy(rolePolicyRequest)

        // Delete the policy.
        val request = DeletePolicyRequest {
            policyArn = polArn
        }

        iam.deletePolicy(request)
        println("*** Successfully deleted $polArn")

        // Delete the role.
        val roleRequest = DeleteRoleRequest {
            roleName = roleNameVal
        }

        iam.deleteRole(roleRequest)
        println("*** Successfully deleted $roleNameVal")
   }

suspend fun deleteUser(userNameVal: String) {
    val iam = IamClient { region = "AWS_GLOBAL"}
    val request = DeleteUserRequest {
            userName = userNameVal
    }

   iam.deleteUser(request)
   println("*** Successfully deleted $userNameVal")
}

@Throws(java.lang.Exception::class)
fun readJsonSimpleDemo(filename: String): Any? {
    val reader = FileReader(filename)
    val jsonParser = JSONParser()
    return jsonParser.parse(reader)
}

suspend fun getPolicy( policyArnVal: String?) {

    val iam = IamClient { region = "AWS_GLOBAL"}
    val request = GetPolicyRequest {
            policyArn = policyArnVal
    }

    val response = iam.getPolicy(request)
    println("Successfully retrieved policy ${response.policy?.policyName}")
  }


suspend fun getRole(roleNameVal: String?) {

    val iam = IamClient { region = "AWS_GLOBAL"}
    val roleRequest = GetRoleRequest {
        roleName = roleNameVal
    }

    val response = iam.getRole(roleRequest)
    println("The ARN of the role is ${response.role?.arn}")
   }

suspend fun getSAMLProviders() {
    val iam = IamClient { region = "AWS_GLOBAL"}
    val response: ListSamlProvidersResponse = iam.listSamlProviders(ListSamlProvidersRequest{})

    val providers  = response.samlProviderList
    providers?.forEach { md ->
        println("The ARN value is ${md.arn}")
    }
}

suspend fun getGroups() {
    val iam = IamClient { region = "AWS_GLOBAL"}
    val request = ListGroupsRequest {
        maxItems = 10
    }

    val response = iam.listGroups(request)
    val groups= response.groups
    groups?.forEach { group ->
            println("The ARN value is ${group.arn}")
     }
   }

suspend fun getPolicies() {
    val iam = IamClient { region = "AWS_GLOBAL"}
    val response = iam.listPolicies(ListPoliciesRequest{})

   val policies = response.policies
    policies?.forEach { policy ->
        println("The ARN value is ${policy.arn}")
    }
}

suspend fun getAttachedRolePolicies(roleNameVal: String?) {
    val iam = IamClient { region = "AWS_GLOBAL" }
    val request = ListAttachedRolePoliciesRequest {
        roleName = roleNameVal
        maxItems = 10
    }

    val response = iam.listAttachedRolePolicies(request)
    val policies = response.attachedPolicies
    policies?.forEach { policy ->
        println("The name of the attached policy is  ${policy.policyName}")
    }
}

suspend fun getRoles() {
    val iam = IamClient { region = "AWS_GLOBAL" }
    val response = iam.listRoles { ListRolesRequest {} }
    val roles = response.roles
    roles?.forEach { role ->
        println("The name of the attached policy is  ${role.roleName}")
    }
}
// snippet-end:[iam.kotlin.scenario.main]