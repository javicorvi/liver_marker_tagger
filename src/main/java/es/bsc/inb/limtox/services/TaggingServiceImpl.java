package es.bsc.inb.limtox.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.MentionsAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import es.bsc.inb.limtox.model.LiverMarkerTerm;

@Service
public class TaggingServiceImpl implements TaggingService{
	
	static final Logger taggingLog = Logger.getLogger("taggingLog");
	
	Map<String, LiverMarkerTerm> liverMarkerTermsDict = new HashMap<String, LiverMarkerTerm>();
	
	public void execute(String propertiesParametersPath) {
		try {
			taggingLog.info("Tagging liver marker terms with properties :  " +  propertiesParametersPath);
			Properties propertiesParameters = this.loadPropertiesParameters(propertiesParametersPath);
			taggingLog.info("Input directory with the articles to tag : " + propertiesParameters.getProperty("inputDirectory"));
			taggingLog.info("Output directory : " + propertiesParameters.getProperty("outputDirectory"));
			taggingLog.info(" liver marker dictionary used : " + propertiesParameters.getProperty("liverMarkerDict"));
			
			String inputDirectoryPath = propertiesParameters.getProperty("inputDirectory");
			String outputDirectoryPath = propertiesParameters.getProperty("outputDirectory");
			String liverMarkerJSONDict = propertiesParameters.getProperty("liverMarkerDict");
			Integer index_id = new Integer(propertiesParameters.getProperty("index_id"));
			Integer index_text_to_tag = new Integer(propertiesParameters.getProperty("index_text_to_tag"));
			
			//curateDict(liverMarkerJSONDict, liverMarkerJSONDict + "_new.json");
			
			File inputDirectory = new File(inputDirectoryPath);
		    if(!inputDirectory.exists()) {
		    	return ;
		    }
		    if (!Files.isDirectory(Paths.get(inputDirectoryPath))) {
		    	return ;
		    }
		    File outputDirectory = new File(outputDirectoryPath);
		    if(!outputDirectory.exists())
		    	outputDirectory.mkdirs();
		    
		    String rulesPathOutput = "liver_marker_rules.txt";
		    generateRulesForTagging(liverMarkerJSONDict, rulesPathOutput);
		    
			Properties props = new Properties();
			props.put("annotators", "tokenize, ssplit, regexner, entitymentions");
			props.put("regexner.mapping", rulesPathOutput);
			props.put("regexner.posmatchtype", "MATCH_ALL_TOKENS");
			StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
	
			List<String> filesProcessed = readFilesProcessed(outputDirectoryPath); 
		    BufferedWriter filesPrecessedWriter = new BufferedWriter(new FileWriter(outputDirectoryPath + File.separator + "list_files_processed.dat", true));
		    File[] files =  inputDirectory.listFiles();
			for (File file_to_classify : files) {
				if(file_to_classify.getName().endsWith(".txt") && filesProcessed!=null && !filesProcessed.contains(file_to_classify.getName())){
					taggingLog.info("Processing file  : " + file_to_classify.getName());
					String fileName = file_to_classify.getName();
					String outputFilePath = outputDirectory + File.separator + fileName;
					BufferedWriter outPutFile = new BufferedWriter(new FileWriter(outputFilePath));
					
					outPutFile.write("id\tstartOffset\tendOffset\ttext\tentityType\tmarker_namespace\tmarker_identifier\tmarker_normalization\t"
							+ "concept_namespace\tmarker_type_name\n");
					outPutFile.flush();
					for (String line : ObjectBank.getLineIterator(file_to_classify.getAbsolutePath(), "utf-8")) {
						try {
							String[] data = line.split("\t");
							String id =  data[index_id];
							String text =  data[index_text_to_tag];
							tagging(pipeline, id, text, outPutFile, file_to_classify.getName());
						}  catch (Exception e) {
							taggingLog.error("Error tagging the document line " + line + " belongs to the file: " +  fileName,e);
						} 
					
					}
					outPutFile.close();
					filesPrecessedWriter.write(file_to_classify.getName()+"\n");
					filesPrecessedWriter.flush();
				}
			}
			filesPrecessedWriter.close();
		}  catch (Exception e) {
			taggingLog.error("Generic error in the classification step",e);
		} 
	}


	/**
	 * Curate the liver marker Limtox Dictionary, run only one time to remove the duplicates original_entry now every thing is lowew case.
	 * @param inputPath
	 * @param outputPath
	 */
	private void curateDict(String inputPath, String outputPath) {
		List<LiverMarkerTerm> liverMarkerTerms = this.findAll(inputPath);
		Map<String, LiverMarkerTerm> liverMarkerTermsCurated = new HashMap<String, LiverMarkerTerm>();
		for (LiverMarkerTerm liverMarkerTerm : liverMarkerTerms) {
			liverMarkerTerm.toLowerCase();
			if(liverMarkerTermsCurated.get(liverMarkerTerm.getMarker_full_name())!=null) {
				taggingLog.warn("The key alreay exist : " + liverMarkerTerm.getMarker_full_name());
			}else {
				liverMarkerTermsCurated.put(liverMarkerTerm.getMarker_full_name().toLowerCase(), liverMarkerTerm);
			}
		}
		taggingLog.warn("Original Dict size : " + liverMarkerTerms.size() + ", curated Dict size : " + liverMarkerTermsCurated.size());
		generateJSONFile(liverMarkerTermsCurated.values(), outputPath);
	}

	@SuppressWarnings("unused")
	private void generateJSONFile(Collection<LiverMarkerTerm> hepatotoxicityTerms, String outputPath)  {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.setSerializationInclusion(Include.NON_NULL);
			objectMapper.setSerializationInclusion(Include.NON_EMPTY);
			try {
				objectMapper.writeValue(new File(outputPath), hepatotoxicityTerms);
			} catch (JsonGenerationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
				} catch (JsonMappingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

	private Map<String, LiverMarkerTerm> generateRulesForTagging(String inputPath,String outputPath) throws IOException {
		List<LiverMarkerTerm> liverMarkerTerms = this.findAll(inputPath);
		BufferedWriter liverMarkerDictWriter = new BufferedWriter(new FileWriter(outputPath));
		for (LiverMarkerTerm liverMarkerTerm : liverMarkerTerms) {
			liverMarkerDictWriter.write(this.removeInvalidCharacters(liverMarkerTerm.getMarker_full_name()) + "\t" + "liver_marker\n");
			liverMarkerDictWriter.flush();
			liverMarkerTermsDict.put(liverMarkerTerm.getMarker_full_name(), liverMarkerTerm);
		}
		liverMarkerDictWriter.close();
		return liverMarkerTermsDict;
	}
	
	/**
	 * 
	 * @param original_entry
	 * @return
	 */
	private String removeInvalidCharacters(String original_entry) {
		// TODO Auto-generated method stub
		return original_entry.replaceAll("\\(", "").replaceAll("\\)", "").replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\*", "");
	}



	/**
	 * 
	 * @throws IOException
	 */
	protected String readJsonFile(String file_path) throws IOException {
		try{
			if(Files.isRegularFile(Paths.get(file_path))) {
				FileReader fr = new FileReader(file_path);
			    BufferedReader br = new BufferedReader(fr);
			
			    StringBuilder textBuilder = new StringBuilder();
			    String line;
			    while ((line=br.readLine())!=null) {
			    	textBuilder.append(line);
			    }
			    br.close();
			    fr.close();
			    return textBuilder.toString();
			}  else {
				
			}
	    }catch(IOException ex){
	       ex.printStackTrace();   
	    }
		return null;
	}
	
    private List<LiverMarkerTerm> findAll(String path) {
    	ObjectMapper mapper = new ObjectMapper();
    	mapper.configure(Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
		try {
			String json_string = readJsonFile(path);
			JsonNode rootNode = mapper.readTree(json_string);
			//JsonNode data = rootNode.path("liver_marker");
			List<LiverMarkerTerm> myObjects = Arrays.asList(mapper.readValue(rootNode.toString(), LiverMarkerTerm[].class));
			return myObjects;
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();	
		}
		return null;
    }
	
	/**
	 * Findings of LTKB ChemicalCompunds
	 * 
	 * @param sourceId
	 * @param document_model
	 * @param first_finding_on_document
	 * @param section
	 * @param sentence_text
	 * @return
	 * @throws MoreThanOneEntityException
	 */
	private void tagging(StanfordCoreNLP pipeline, String id, String text_to_tag, BufferedWriter output, String fileName) {
//		String text = "Joe Smith was born in California. " +
//			      "In 2017, he went amineptine to Paris, France in the summer. " +
//			      "xenobiotic liver toxicity His flight left at 3:00pm on alatrofloxacin mesylate July 10th, 2017. " +
//			      "After eating some escargot for the first time, Joe said, \"That was delicious!\" " +
//			      "He sent xenobiotic liver toxicity a postcard to his sister Jane Smith. " +
//			      "After hearing about Joe's fipexide trip, Jane decided she might go to France one day.";
		//Annotation document = new Annotation(text_to_tag);
		Annotation document = new Annotation(text_to_tag.toLowerCase());
		// run all Annotators on this text
		pipeline.annotate(document);
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
        	// traversing the words in the current sentence
	        // a CoreLabel is a CoreMap with additional token-specific methods
        	
        	List<CoreMap> entityMentions = sentence.get(MentionsAnnotation.class);
    		for (CoreMap entityMention : entityMentions) {
    			try {
    				String keyword = entityMention.get(TextAnnotation.class);
        			String entityType = entityMention.get(CoreAnnotations.EntityTypeAnnotation.class);
			        if(entityType!=null && entityType.equals("liver_marker")) {
			        	CoreLabel token = entityMention.get(TokensAnnotation.class).get(0);
			        	LiverMarkerTerm hepatotoxicityTerm = liverMarkerTermsDict.get(keyword);
			        	if(hepatotoxicityTerm!=null) {
			        		output.write(id + "\t"+ token.beginPosition() + "\t" + (token.beginPosition() + keyword.length())  + "\t" + keyword + "\t" + entityType.toLowerCase() + "\t" + 
						        	hepatotoxicityTerm.getMarker_namespace() + "\t" + hepatotoxicityTerm.getMarker_identifier() + "\t" + hepatotoxicityTerm.getMarker_normalization() + "\t" + 
						        	hepatotoxicityTerm.getConcept_namespace() + "\t" + hepatotoxicityTerm.getMarker_type_name() + "\n");
			        	} else {
			        		//revisar el tipo de ner que se esta utilizando hay casos en que encuentra cosas que no estan en el listado, creo que es porque se usa combinedner.
			        		output.write(id + "\t"+ token.beginPosition() + "\t" + (token.beginPosition() + keyword.length())  + "\t" + keyword + "\t" + entityType + "\t null \t null \t null \t null \t" + 
			        		 "null\n");
			        		taggingLog.warn("Entry not found " + keyword);
			        	}
			        	output.flush();
				    }
        		} catch (Exception e) {
					taggingLog.error("Generic Error tagging id "  + id + " in file " + fileName, e);
				}
    		}
        	
	        
        }
	}


	private List<String> readFilesProcessed(String outputDirectoryPath) {
		try {
			List<String> files_processed = new ArrayList<String>();
			if(Files.isRegularFile(Paths.get(outputDirectoryPath + File.separator + "list_files_processed.dat"))) {
				FileReader fr = new FileReader(outputDirectoryPath + File.separator + "list_files_processed.dat");
			    BufferedReader br = new BufferedReader(fr);
			    
			    String sCurrentLine;
			    while ((sCurrentLine = br.readLine()) != null) {
			    	files_processed.add(sCurrentLine);
				}
			    br.close();
			    fr.close();
			}
			return files_processed;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	  * Load Properties
	  * @param properitesParametersPath
	  */
	 public Properties loadPropertiesParameters(String properitesParametersPath) {
		 Properties prop = new Properties();
		 InputStream input = null;
		 try {
			 input = new FileInputStream(properitesParametersPath);
			 // load a properties file
			 prop.load(input);
			 return prop;
		 } catch (IOException ex) {
			 ex.printStackTrace();
		 } finally {
			 if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			 }
		}
		return null;
	 }	

	
}