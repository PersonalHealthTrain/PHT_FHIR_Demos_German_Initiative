package org.opencds.cqf.cql.execution;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.ExpressionDef;
import org.cqframework.cql.elm.execution.FunctionDef;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.PlanDefinition;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.data.fhir.FhirBundleCursorStu3;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderStu3;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by Christopher on 1/13/2017.
 */
@Path("evaluate")
public class Executor {

    private Map<String, List<Integer>> locations = new HashMap<>();

    // for future use
    private ModelManager modelManager;
    private ModelManager getModelManager() {
        if (modelManager == null) {
            modelManager = new ModelManager();
        }
        return modelManager;
    }

    JSONParser parser = new JSONParser();
    
    private LibraryManager libraryManager;
    private LibraryManager getLibraryManager() {
        if (libraryManager == null) {
            libraryManager = new LibraryManager(getModelManager());
            libraryManager.getLibrarySourceLoader().clearProviders();
            libraryManager.getLibrarySourceLoader().registerProvider(getLibrarySourceProvider());
        }
        return libraryManager;
    }

    private ExecutorLibrarySourceProvider librarySourceProvider;
    private ExecutorLibrarySourceProvider getLibrarySourceProvider() {
        if (librarySourceProvider == null) {
            librarySourceProvider = new ExecutorLibrarySourceProvider();
        }
        return librarySourceProvider;
    }

    private LibraryLoader libraryLoader;
    private LibraryLoader getLibraryLoader() {
        if (libraryLoader == null) {
            libraryLoader = new ExecutorLibraryLoader(getLibraryManager(), getModelManager());
        }
        return libraryLoader;
    }

    private String performRetrieve(Iterable result) {
        FhirContext fhirContext = FhirContext.forDstu3(); // for JSON parsing
        Iterator it = result.iterator();
        List<Object> findings = new ArrayList<>();
        while (it.hasNext()) {
            // returning full JSON retrieve response
            findings.add(fhirContext
                    .newJsonParser()
                    .setPrettyPrint(true)
                    .encodeResourceToString((org.hl7.fhir.instance.model.api.IBaseResource)it.next()));
        }
        return findings.toString();
    }

    private String resolveType(Object result) {
        String type = result == null ? "Null" : result.getClass().getSimpleName();
        switch (type) {
            case "BigDecimal": return "Decimal";
            case "ArrayList": return "List";
            case "FhirBundleCursor": return "Retrieve";
        }
        return type;
    }

    private CqlTranslator getTranslator(String cql, LibraryManager libraryManager, ModelManager modelManager) {
        return getTranslator(new ByteArrayInputStream(cql.getBytes(StandardCharsets.UTF_8)), libraryManager, modelManager);
    }

    private CqlTranslator getTranslator(InputStream cqlStream, LibraryManager libraryManager, ModelManager modelManager) {
        ArrayList<CqlTranslator.Options> options = new ArrayList<>();
        options.add(CqlTranslator.Options.EnableDateRangeOptimization);
//        options.add(CqlTranslator.Options.EnableAnnotations);
//        options.add(CqlTranslator.Options.EnableDetailedErrors);
        CqlTranslator translator;
        try {
            translator = CqlTranslator.fromStream(cqlStream, modelManager, libraryManager,
                    options.toArray(new CqlTranslator.Options[options.size()]));
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Errors occurred translating library: %s", e.getMessage()));
        }

        String xml = translator.toXml();

        if (translator.getErrors().size() > 0) {
            throw new IllegalArgumentException(errorsToString(translator.getErrors()));
        }

        return translator;
    }

    private String errorsToString(Iterable<CqlTranslatorException> exceptions) {
        ArrayList<String> errors = new ArrayList<>();
        for (CqlTranslatorException error : exceptions) {
            TrackBack tb = error.getLocator();
            String lines = tb == null ? "[n/a]" : String.format("%s[%d:%d, %d:%d]",
                    (tb.getLibrary() != null ? tb.getLibrary().getId() + (tb.getLibrary().getVersion() != null
                            ? ("-" + tb.getLibrary().getVersion()) : "") : ""),
                    tb.getStartLine(), tb.getStartChar(), tb.getEndLine(), tb.getEndChar());
            errors.add(lines + error.getMessage() + "\n");
        }

        return errors.toString();
    }

    private void setExpressionLocations(org.hl7.elm.r1.Library library) {
        if (library.getStatements() == null) return;
        for (org.hl7.elm.r1.ExpressionDef def : library.getStatements().getDef()) {
            int startLine = def.getTrackbacks().isEmpty() ? 0 : def.getTrackbacks().get(0).getStartLine();
            int startChar = def.getTrackbacks().isEmpty() ? 0 : def.getTrackbacks().get(0).getStartChar();
            List<Integer> loc = Arrays.asList(startLine, startChar);
            locations.put(def.getName(), loc);
        }
    }

    private Library readLibrary(InputStream xmlStream) {
        try {
            return CqlLibraryReader.read(xmlStream);
        } catch (IOException | JAXBException e) {
            throw new IllegalArgumentException("Error encountered while reading ELM xml: " + e.getMessage());
        }
    }

    private Library translateLibrary(CqlTranslator translator) {
        return readLibrary(new ByteArrayInputStream(translator.toXml().getBytes(StandardCharsets.UTF_8)));
    }
    
    private BaseFhirDataProvider provider;
    
    private void registerProviders(Context context, String termSvcUrl, String termUser,
                                   String termPass, String dataPvdrURL, String dataUser, String dataPass)
    {
        // TODO: plugin authorization for data provider when available

        String defaultEndpoint = "http://measure.eval.kanvix.com/cqf-ruler/baseDstu3";

        provider = new FhirDataProviderStu3()
                .setEndpoint(dataPvdrURL == null ? defaultEndpoint : dataPvdrURL);
        FhirContext fhirContext = provider.getFhirContext();
        fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        provider.setFhirContext(fhirContext);
        provider.getFhirClient().setEncoding(EncodingEnum.JSON);

        FhirTerminologyProvider terminologyProvider = new FhirTerminologyProvider()
                .withBasicAuth(termUser, termPass)
                .setEndpoint(termSvcUrl == null ? defaultEndpoint : termSvcUrl, false);

        provider.setTerminologyProvider(terminologyProvider);
//        provider.setSearchUsingPOST(true);
//        provider.setExpandValueSets(true);
        context.registerDataProvider("http://hl7.org/fhir", provider);
        context.registerTerminologyProvider(terminologyProvider);
        context.registerLibraryLoader(getLibraryLoader());
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public String evaluateCql(String requestData) throws JAXBException, IOException, ParseException {

        JSONParser parser = new JSONParser();
        JSONObject json;
        try {
            json = (JSONObject) parser.parse(requestData);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Error parsing JSON request: " + e.getMessage());
        }

        String code = (String) json.get("code");
        String terminologyServiceUri = (String) json.get("terminologyServiceUri");
        String terminologyUser = (String) json.get("terminologyUser");
        String terminologyPass = (String) json.get("terminologyPass");
        String dataServiceUri = (String) json.get("dataServiceUri");
        String dataUser = (String) json.get("dataUser");
        String dataPass = (String) json.get("dataPass");
        String patientId = (String) json.get("patientId");

        CqlTranslator translator;
        try {
            translator = getTranslator(code, getLibraryManager(), getModelManager());
        }
        catch (IllegalArgumentException iae) {
            JSONObject result = new JSONObject();
            JSONArray resultArr = new JSONArray();
            result.put("translation-error", iae.getMessage());
            resultArr.add(result);
            return resultArr.toJSONString();
        }

        setExpressionLocations(translator.getTranslatedLibrary().getLibrary());

        if (locations.isEmpty()) {
            JSONObject result = new JSONObject();
            JSONArray resultArr = new JSONArray();
            result.put("result", "Please provide valid CQL named expressions for execution output");
            result.put("name", "No expressions found");
            String location = String.format("[%d:%d]", 0, 0);
            result.put("location", location);

            resultArr.add(result);
            return resultArr.toJSONString();
        }

        Library library = translateLibrary(translator);

        Context context = new Context(library);
        registerProviders(context, terminologyServiceUri, terminologyUser, terminologyPass, dataServiceUri, dataUser, dataPass);
        
        JSONArray resultArr = new JSONArray();
        Map<String, ArrayList<String>> dictionary = new HashMap<>();

        if (patientId != null && !patientId.isEmpty()) {
            context.setContextValue(context.getCurrentContext(), patientId);

            evaluateStatements(library, context, resultArr);
        }
        else {
	        // TODO: set context in population
	    	Iterable<Object> list = provider.retrieve("Population", null, "Patient", null, null, null, null, null, null, null, null);
	    	JSONArray resultArray = new JSONArray();
	    	
            Iterable<Object> element = (Iterable<Object>)list;
            Iterator<Object> elemsItr = element.iterator();

            while (elemsItr.hasNext()) {
                Patient patient = (Patient) elemsItr.next();
	            context.setContextValue("Patient", patient.getIdElement().getIdPart());
	            
	            Object result = context.resolveExpressionRef("In Demographic").evaluate(context);
                if (result == null) {
                    continue;
                }
                if ((Boolean) result) {
                	collectStatements(library, context, resultArray);
                }
            }

	    	if(resultArray.size() > 0) {
	    		resultArr = resultArray;
	    	}
        }
        
        return resultArr.toJSONString();
    }

    private JSONArray performRetrieve2(Iterable result) {
        FhirContext fhirContext = FhirContext.forDstu3(); // for JSON parsing
        Iterator it = result.iterator();
        JSONArray findings = new JSONArray();
        while (it.hasNext()) {
            // returning full JSON retrieve response
        	String value = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString((org.hl7.fhir.instance.model.api.IBaseResource)it.next());
            try {
				findings.add((JSONObject) parser.parse(value));
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        return findings;
    }

	private void collectStatements(Library library, Context context, ArrayList<JSONObject> resultArr) {
		int i=0;
		for (ExpressionDef def : library.getStatements().getDef()) {
            context.enterContext(def.getContext());
            
            JSONObject result;
            if (resultArr.size() == i) {
            	result = new JSONObject();
            } else {
            	result = resultArr.get(i);
            }
            
            try {
            	putValue(result, "name", def.getName());

                String location = String.format("[%d:%d]", locations.get(def.getName()).get(0), locations.get(def.getName()).get(1));
                putValue(result, "location", location);

                Object res = def instanceof FunctionDef ? "Definition successfully validated" : def.getExpression().evaluate(context);

                if (res == null) {
                	putValue(result, "location", location);
                	putValue(result, "result", "Null");
                }
                else if (res instanceof FhirBundleCursorStu3) {
                	putValue(result, "result", performRetrieve2((Iterable) res));
                }
                else if (res instanceof List) {
                    if (((List) res).size() > 0 && ((List) res).get(0) instanceof IBaseResource) {
                    	putValue(result, "result", performRetrieve2((Iterable) res));
                    }
                    else {
                    	putValue(result, "result", res.toString());
                    }
                }
                else if (res instanceof IBaseResource) {
                	String value = FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString((IBaseResource) res);
                	putValue(result, "result", (JSONObject) parser.parse(value));
                }
                else {
                	putValue(result, "result", res.toString());
                }
                putValue(result, "resultType", resolveType(res));
            }
            catch (RuntimeException re) {
            	putValue(result, "error", re.getMessage());
                re.printStackTrace();
            } catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
            if (resultArr.size() == i) {
            	resultArr.add(result);
            } else {
            	resultArr.set(i, result);
            }
            i++;
        }
	}

	private void putValue(JSONObject dictionary, String key, Object value) {
		
		if (dictionary.containsKey(key)) {
			if (key == "result") {
				JSONArray dictValue;
				
				Object obj = dictionary.get(key);
				String type = obj.getClass().getSimpleName();
				
				if(!value.equals(obj)) {
					if(type.equals("String") || type.equals("JSONObject")) {
						dictValue = new JSONArray();
						dictValue.add(obj);
					} else {
						dictValue = (JSONArray) obj;
					}
					
					if(!value.equals("[]")) {
						dictValue.add(value);
					}
					
					dictionary.put(key, dictValue);
				}
			}
		} else {
			dictionary.put(key, value);
		}
	}
	
	private void evaluateStatements(Library library, Context context, JSONArray resultArr) {
		for (ExpressionDef def : library.getStatements().getDef()) {
            context.enterContext(def.getContext());
            
            JSONObject result = new JSONObject();
            try {
                result.put("name", def.getName());

                String location = String.format("[%d:%d]", locations.get(def.getName()).get(0), locations.get(def.getName()).get(1));
                result.put("location", location);

                Object res = def instanceof FunctionDef ? "Definition successfully validated" : def.getExpression().evaluate(context);

                if (res == null) {
                    result.put("result", "Null");
                }
                else if (res instanceof FhirBundleCursorStu3) {
                	result.put("result", performRetrieve((Iterable) res));
                }
                else if (res instanceof List) {
                    if (((List) res).size() > 0 && ((List) res).get(0) instanceof IBaseResource) {
                    	result.put("result", performRetrieve((Iterable) res));
                    }
                    else {
                        result.put("result", res.toString());
                    }
                }
                else if (res instanceof IBaseResource) {
                    result.put("result", FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString((IBaseResource) res));
                }
                else {
                    result.put("result", res.toString());
                }
                result.put("resultType", resolveType(res));
            }
            catch (RuntimeException re) {
                result.put("error", re.getMessage());
                re.printStackTrace();
            }
            resultArr.add(result);
        }
	}

}
