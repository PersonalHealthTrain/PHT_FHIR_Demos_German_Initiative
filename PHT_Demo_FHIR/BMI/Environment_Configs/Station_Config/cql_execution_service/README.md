# CQL Execution Service

The CQL Execution Service is an implementation based on [cql_execution_service](https://github.com/DBCG/cql_execution_service) of the [cql_engine](https://github.com/DBCG/cql_engine) as a web service.

## Usage:
- Clone repository
- mvn install
- mvn -Djetty.http.port=xxxx jetty:run
    - This command spins up the server and listens on the port of your choosing (usually 8080 for development).


### Execution Service

 - POST the following to [base]/cql/evaluate:
 
    ```
    {
        "code": "Your CQL code",
        "fhirServiceUri": "Terminology Service Endpoint",
        "fhirUser": "Username for authentication",
        "fhirPass": "Password for authentication",
        "dataServiceUri": "Fhir Data Provider Endpoint",
        "dataUser": "Username for authentication",
        "dataPass": "Password for authentication",
        "patientId": "The patient you want to run the library against"
    }
    ```
 
 - This Request will produce a JSON Response in the following format:
 
    ```
    [
        {
            "translator-error": "Translation error message (is the only element returned)",
            "name": "CQL Expression name",
            "location": "[row:col]",
            "resultType": "CQL Type being returned",
            "error": "Runtime error output (this may cause the omission of resultType)"
        }
    ]
    ```
 
### Validation Service

 - POST the following to [base]/cql/validate:
 
    ```
    {
        "code": "Your CQL code"
    }
    ```
 
 - This Request will produce a JSON Response in the following format:
 
    ```
    [
        {
            "translator-error": "Translation error message (is the only element returned)",
            "name": "CQL Expression name",
            "location": "[row:col]",
            "resultType": "CQL Type being returned",
            "error": "Runtime error output (this may cause the omission of resultType)"
        }
    ]
    ```
 
### CQL Formatter
 
 - POST the following to [base]/cql/format:
 
    ```
    {
        "code": "Unformatted CQL code"
    }
    ```
 
 - This Request will produce a JSON Response in the following format:
 
    ```
    [
        {
            "formatted-cql": "The formatted CQL code"
        }
    ]
    ```
