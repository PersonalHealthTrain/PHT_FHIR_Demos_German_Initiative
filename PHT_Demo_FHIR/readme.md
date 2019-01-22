
# FHIR based Personal Health Train demos
The basic workflow of Personal Health Train is to share analytics algorithm instead of sharing the data and computational resources without leaving the data source. This means:
- We define the computing algorithm and methods at the client-side with seeing the original data but based on only publicly available metadata. Then the metadata about the algorithm, train and the script are encapsulated to be shipped to the handler statation hosting the FHIR resources
- The analytics algorithm is executed at the (handler) station 
- We don't need to transfer data back-forth over the network (or by any means), hence the overall pipleline ensures the preserving patients' privacy
- Only the results are back to the client for visual analytics
- The handler stations updates the train registry with the latest image
- Most importantly, we aim at supporting different data representation in FHIR and query standards in CQL as applications/calculations at the source can perform the conversion/transformation. 
