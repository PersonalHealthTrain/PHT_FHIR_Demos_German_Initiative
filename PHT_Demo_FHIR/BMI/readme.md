# Personal Health Train
The basic workflow of Personal Health Train is to share analytics algorithm instead of sharing the data and computational resources without leaving the data source. This means:

    we can calculate information at the source
    we don't have to transfer data for calculation purposes, hence preserving patients' privacy
    we are able to support different data representation and query standards, as applications/calculations at the source can perform the conversion/transformation.

## Repository
This repository is structured into two different folders:

    Client (web-app): Can be used for composing the pnenotype algorithm, packaging, shipping, train monitoring, result receive and visualization. The web-app can be accessed at http://menzel.informatik.rwth-aachen.de:3000/login. You need to register first. Then you'll be able to enjoy the features.  
    Docker registry: for hosting the train and updaing their status
    Routing module: make sure each trains dispatches to the handling station smoothly
    Handling station: PHT station runtime, where all the data retreival and computations happen. 
    Sample CQL input: see Input.cql file shows a CQL expression, which will filter all the patient whose are over 50 kg and height is 45 inch. 
    
## Interactive notebook
Querying_FHIR_server_with_CQL_expression_and_compute_BMI.ipynb can be executed to run the PHT BMI counter. This will:

-- query a FHIR server (containing 65 patients records in FHIR standard), 
-- pull the FHIR resource bundle, 
-- performs some minor preprocessing and 
-- finally compute the BMI of each patient that satisfy filtering criteria in the CQL query file (see Input.cql file). 

## Updates
We plan to provide more details and full automated way to run this demo so that client can define the phenotype algorithm from a web-app, ship the package to be pushed to the Docker registry. Then the handler (station) gets notified automatically (well, the routing module schedules it, though). The train gets executed at the startion, send the result back to the client on the web-app result service endpoint. Additionally, the handling station pushes the updated image to the Docker registry. 

