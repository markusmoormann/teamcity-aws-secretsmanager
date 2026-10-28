# AWS Secrets Manager Support for TeamCity

Use secrets stored in **AWS Secrets Manager** as TeamCity build parameters. You don't need to copy them into
password parameters or fetch them with scripts.

Secrets are referenced by **remote parameters** that point to a TeamCity **AWS Connection**. They work the same way as
the parameters of the bundled
[HashiCorp Vault integration](https://www.jetbrains.com/help/teamcity/hashicorp-vault.html#Create+and+Set+Up+a+Parameter).
The secret values are fetched on the build agent when a build starts and are masked in the build log.

## Features

- **Remote parameter type "AWS Secrets Manager Parameter"**: works for configuration parameters, environment
  variables (`env.*`) and system properties (`system.*`).
- **Uses the TeamCity AWS Connections you already have**: IAM role, access keys or the default credential provider
  chain. You don't need separate credentials.
- **Secret values are fetched on the build agent.** They never pass through the TeamCity server and are masked in the
  build log.
- **Editor support**: suggestions for secret names while you type, a dropdown of the secret's JSON keys, and a
  **Test Query** button.
- **JSON secrets**: select a single top-level key with `secret!/key`, or use the whole secret string.
- **Kotlin DSL support**: `awsSecretsManagerParameter { … }`.
- **Protection for custom builds**: users without the *Edit project* permission cannot add AWS Secrets Manager
  parameters or redirect them to other secrets.
- **Clear build log**: shows which secret and version each parameter came from, never the values.

## Requirements

- TeamCity **2026.2** or later
- A TeamCity [AWS Connection](https://www.jetbrains.com/help/teamcity/configuring-connections.html)
  in the project or in one of its parent projects
- Outbound HTTPS access to `secretsmanager.<region>.amazonaws.com`:
  - from the **build agents**, to resolve the parameters
  - from the **TeamCity server**, for *Test Query* and the editor suggestions

## Installation

1. Install the plugin from JetBrains Marketplace, or upload the plugin ZIP under
   *Administration | Plugins | Upload plugin zip*.
2. Restart the TeamCity server if you are asked to.
3. TeamCity distributes the agent part automatically. Agents with the plugin installed report the configuration
   parameter `teamcity.awssm.supported = true`.

## Quick start

1. Create an AWS Connection with the ID `AwsProd`. Make sure *Available for builds* is enabled.
2. In AWS, create a secret `prod/db` with the value `{"user":"admin","password":"s3cr3t"}`.
3. Add a parameter `env.DB_PASSWORD`, of type **Remote** / **AWS Secrets Manager Parameter**, with the connection
   `AwsProd`, the secret `prod/db` and the key `password`.
4. Use `$DB_PASSWORD` (or `%env.DB_PASSWORD%`) in a build step. The build log shows `*******` instead of the value.

## Configuration

### 1. AWS Connection

Go to *Project Settings | Connections | Add Connection | Amazon Web Services (AWS)*.

| Setting | Recommendation |
|---|---|
| Type | **IAM Role**, or **Access keys** with *Session credentials* enabled, so that only short-lived credentials are passed to the agent |
| Connection ID | A readable ID such as `AwsProd`. Parameters reference the connection by this ID. |
| Region | The region your secrets are stored in. Secrets in other regions can be referenced by their full ARN. |
| Available for builds | **Must be enabled** |
| Available for sub-projects | Enable it if subprojects should use the connection |

### 2. IAM permissions

The AWS identity used by the connection needs the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadSecrets",
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:eu-central-1:123456789012:secret:prod/*"
    },
    {
      "Sid": "SuggestSecretNamesInEditor",
      "Effect": "Allow",
      "Action": "secretsmanager:ListSecrets",
      "Resource": "*"
    }
  ]
}
```

- `secretsmanager:GetSecretValue` is required. Limit it to the secrets your builds need.
- `secretsmanager:ListSecrets` is **optional**. It is only used for the secret name suggestions in the parameter
  editor. AWS doesn't allow restricting it to individual secrets. It exposes the *names* of all secrets in the region,
  but not their values. Without this permission, you enter the secret name or ARN manually.
- `kms:Decrypt` is required on the KMS key if a secret is encrypted with a customer-managed key.

### 3. Create a parameter in the UI

1. Open *Parameters | Add new parameter* in a project or build configuration and enter a name, for example
   `env.DB_PASSWORD`.
2. Click *Edit…* next to **Spec** and set:
   - **Type**: Remote
   - **Remote Connection Type**: AWS Secrets Manager Parameter
   - **AWS Connection**: the connection to use
   - **Secret**: start typing to get suggestions from the connection's region. You can also enter a full ARN, for
     example to use a secret from another region.
   - **JSON Key**: after you select a secret, its top-level keys appear in a dropdown. The editor loads only the key
     names, never the values. Choose *(whole secret string)* to use the complete value. If the keys cannot be loaded,
     you can type the key instead.
3. Click **Test Query** to check that the secret can be read. The value is never displayed.
4. Save the parameter. It can now be used like any other parameter.

The secret suggestions, the key dropdown and *Test Query* are available only to users with the *Edit project*
permission.

### 4. Create a parameter with the Kotlin DSL

```kotlin
project {
    params {
        awsSecretsManagerParameter {
            name = "env.DB_PASSWORD"
            query = "prod/db!/password"
            connectionId = "AwsProd"
        }
        awsSecretsManagerParameter {
            name = "apiToken"
            query = "arn:aws:secretsmanager:us-west-2:123456789012:secret:api-token-AbCdEf"
            connectionId = "AwsProd"
        }
    }
}
```

| Property | Required | Description |
|---|---|---|
| `name` | yes | Parameter name. Use the `env.` prefix for environment variables and `system.` for system properties. |
| `query` | yes | Secret reference, see [Query syntax](#query-syntax) |
| `connectionId` | yes | ID of the TeamCity AWS Connection |

## Query syntax

```
<secret name or ARN>[!/<JSON key>]
```

| Query | Resolves to |
|---|---|
| `prod/db!/password` | the value of the top-level key `password` in the JSON secret `prod/db` |
| `api-token` | the whole secret string |
| `arn:aws:secretsmanager:us-west-2:123456789012:secret:api-token-AbCdEf` | the whole secret string, read from `us-west-2` |

- Secrets are read from the region of the AWS Connection. If the query is a full ARN, the region in the ARN is used.
- String values are returned as they are. Numbers, booleans and nested objects are returned as JSON text.
- Each secret is fetched only once per build, even if several parameters reference it.

## How it works

1. **On the server, when a build starts**, the plugin finds all AWS Secrets Manager parameters of the build. For
   each AWS Connection they use, it requests credentials from TeamCity and passes them to the agent in an internal
   parameter. TeamCity's built-in *AWS Credentials* build feature uses the same mechanism.
2. **On the agent, before the first build step**, the plugin:
   - masks the credentials and clears the internal parameter
   - reads the secrets directly from AWS Secrets Manager
   - sets each value as a configuration parameter, environment variable or system property and masks it in the
     build log
3. If a parameter cannot be resolved, the plugin reports a build problem and stops the build. Reasons include a
   missing connection, access denied, or a missing secret or key. A build never continues with an empty or wrong
   value.

The build log contains a collapsible *AWS Secrets Manager* block. It shows metadata only, never values, value
lengths or hashes:

```
Connection 'AwsProd' (region eu-central-1, access key AKIA…A123)
env.DB_PASSWORD <- prod/db!/password (version 3f2a9c1e)
apiToken <- arn:aws:secretsmanager:us-west-2:123456789012:secret:api-token-AbCdEf [us-west-2]
Resolved 2 parameter(s) from 2 secret(s)
```

## Security

- **Secret values never pass through the TeamCity server** during a build. The agent reads them directly from AWS.
  Every resolved value is registered with TeamCity's password masking.
- **Credentials:** the AWS credentials passed to the agent are stored with the build's parameters on the server, the
  same as with the *AWS Credentials* build feature. Use an IAM role or session credentials so that they expire
  quickly. Grant only the permissions your builds need.
- **Custom builds:** if a user without the *Edit project* permission runs a custom build:
  - New AWS Secrets Manager parameters or changes to their spec (connection or query) stop the build.
  - A changed parameter value is ignored and replaced with the configured value. A warning is written to the build
    log.
- **Editor endpoints:** secret suggestions, key lookup and *Test Query* require the *Edit project* permission. None
  of them return secret values.

## Troubleshooting

| Message | Cause and solution |
|---|---|
| `AWS connection '…' is not available in project …` | The connection ID doesn't exist in this project or its parents, or the connection isn't shared with subprojects. |
| `… is not available for builds` | Enable *Available for builds* in the AWS Connection. |
| `Secrets Manager can't find the specified secret` | Check the secret name. If the secret is in another region, use its full ARN. |
| `AccessDeniedException` | Grant `secretsmanager:GetSecretValue` on the secret, and `kms:Decrypt` on the key if a customer-managed KMS key is used. |
| `The secret does not contain the key '…'` | The JSON key doesn't exist. Select it from the dropdown in the parameter editor. |
| `The secret value is not JSON, cannot read key '…'` | The secret is plain text. Remove the `!/key` part to use the whole value. |
| `No suggestions available: …` in the editor | The connection lacks `secretsmanager:ListSecrets`. Enter the secret name manually. |
| `Custom AWS Secrets Manager parameters were added or changed` | A user without *Edit project* permission changed an AWS Secrets Manager parameter in a custom build. |

## Limitations

- Binary secrets (`SecretBinary`) are not supported.
- Only top-level JSON keys can be selected.
- A specific secret version or staging label cannot be selected. The current version (`AWSCURRENT`) is always used.

## Building from source

```bash
./gradlew build
```

The plugin ZIP is created at `server/build/distributions/aws-secretsmanager-plugin.zip`. To build against another
TeamCity version, change `teamcityVersion` in `gradle.properties`.

## License

Copyright 2026 Markus Moormann

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE).

The plugin bundles third-party libraries, among them the AWS SDK for Java 2.x, Jackson and the Kotlin standard
library. They are licensed under the Apache License 2.0, except Reactive Streams, which uses MIT-0. Their license
texts and notices are included in the `licenses` directory of the plugin distribution.
