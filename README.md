## PHT demos by German (PHT) team 
This repository provides some sample PHT implementations efforts by the PHT German team. We plan to provide several demo use cases based on FHIR and RDF standards. Refer to respective directory for full description on how to run the demo. 

# Personal Health Train in a nutshell
The basic workflow of Personal Health Train is to share analytics algorithm instead of sharing the data and computational resources without leaving the data source. This means:
- We define the computing algorithm and methods at the client-side with seeing the original data but based on only publicly available metadata. Then the metadata about the algorithm, train and the script are encapsulated to be shipped to the handler statation hosting the FHIR resources
- The analytics algorithm is executed at the (handler) station 
- We don't need to transfer data back-forth over the network (or by any means), hence the overall pipleline ensures the preserving patients' privacy
- Only the results are back to the client for visual analytics
- The handler stations updates the train registry with the latest image
- Most importantly, we aim at supporting different data representation (FHIR, RDF) and query standards (CQL, CQL) as applications/calculations at the source can perform the conversion/transformation. 

## Citation request
If you use any part of this in your research, please cite the following paper: 

    @inproceedings{karimetalswat4ls2018,
        title={A Distributed Analytics Platform to Execute FHIR-based Phenotyping Algorithms},
        author={Md. Rezaul Karim, Binh-Phi Nguyen, Lukas Zimmermann, Toralf Kirsten, Matthias Lobe, Frank Meineke, Holger Stenzhorn, Oliver Kohlbacher, Stefan Decker and Oya Beyan},
        booktitle={11th International Semantic Web Applications and Tools for Healthcare and Life Sciences(SWAT4HCLS2018)},
        year={Antwerp, Belgium, 3-6 December 2018}
    }

## Contributing
For any questions, feel free to open an issue or contact at beyan@dbis.rwth-aachen.de
